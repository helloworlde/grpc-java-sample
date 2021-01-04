# gRPC 中打印请求二进制日志

gRPC 支持将请求调用的参数、Header 等信息以二进制的方式输出到文件中，方便在必要时排查问题

## 使用

#### 1. 添加依赖

binlog 的依赖在 `grpc-services`中，所以需要有该依赖

```kotlin
dependencies {
    implementation("io.grpc:grpc-services:${grpcVersion}")
}
```

#### 2. 添加 BinaryLogSink 实现 

```java
@Slf4j
public class CustomBinaryLogSink implements BinaryLogSink {

    private final String outPath;
    private final OutputStream out;
    private boolean closed;

    CustomBinaryLogSink(String path) throws IOException {
        File outFile = new File(path);
        outPath = outFile.getAbsolutePath();
        log.info("Writing binary logs to {}", outFile.getAbsolutePath());
        out = new BufferedOutputStream(new FileOutputStream(outFile));
    }

    String getPath() {
        return this.outPath;
    }

    @Override
    public synchronized void write(MessageLite message) {
        if (closed) {
            log.info("Attempt to write after TempFileSink is closed.");
            return;
        }
        try {
            message.writeDelimitedTo(out);
            out.flush();
        } catch (IOException e) {
            log.info("Caught exception while writing", e);
            closeQuietly();
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        try {
            out.flush();
        } finally {
            out.close();
        }
    }

    private synchronized void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
            log.info("Caught exception while closing", e);
        }
    }
}
```

#### 3. 创建 Channel 时指定 BinaryLog

```java
BinaryLog binaryLog = BinaryLogs.createBinaryLog(new CustomBinaryLogSink("CUSTOM_PATH"), "*");
this.channel = ManagedChannelBuilder.forAddress(host, port)
                                    .usePlaintext()
                                    .setBinaryLog(binaryLog)
                                    .build();
```

#### 4. 指定环境变量

需要指定环境变量，设置需要输出的方法才会生效，设置 `GRPC_BINARY_LOG_CONFIG=*`，`*`代表打印所有的方法，具体指定可以参考 [Control Interface](https://github.com/helloworlde/proposal/blob/master/A16-binary-logging.md#control-interface)

## 实现原理

在方法调用时，会判断有没有设置 binlog 对象，如果有则会封装方法，添加处理器和监听器；然后重新创建 `ServerMethodDefinition`；通过二进制日志拦截器 `io.grpc.services.BinlogHelper#getClientInterceptor` 拦截请求并写入日志

- io.grpc.internal.ServerImpl.ServerTransportListenerImpl#startCall

```java
    private <ReqT, RespT> ServerStreamListener startCall(ServerStream stream, String fullMethodName,
        ServerMethodDefinition<ReqT, RespT> methodDef, Metadata headers,
        Context.CancellableContext context, StatsTraceContext statsTraceCtx, Tag tag) {

      // 如果 binlog 不为空，即需要记录binlog，则添加请求监听器和方法处理器记录 binlog
      ServerMethodDefinition<?, ?> wMethodDef = binlog == null ? interceptedDef : binlog.wrapMethodDefinition(interceptedDef);

      return startWrappedCall(fullMethodName, wMethodDef, stream, headers, context, tag);
    }
```

- io.grpc.services.BinaryLogProvider#wrapMethodDefinition

```java
  public final <ReqT, RespT> ServerMethodDefinition<?, ?> wrapMethodDefinition(ServerMethodDefinition<ReqT, RespT> oMethodDef) {
    // 根据方法获取二进制日志拦截器，如果没有该方法则不拦截
    ServerInterceptor binlogInterceptor = getServerInterceptor(oMethodDef.getMethodDescriptor().getFullMethodName());
    if (binlogInterceptor == null) {
      return oMethodDef;
    }

    MethodDescriptor<byte[], byte[]> binMethod = BinaryLogProvider.toByteBufferMethod(oMethodDef.getMethodDescriptor());
    // 包装方法，添加了处理器和监听器
    ServerMethodDefinition<byte[], byte[]> binDef = InternalServerInterceptors.wrapMethod(oMethodDef, binMethod);
    // 创建处理器
    ServerCallHandler<byte[], byte[]> binlogHandler =
            InternalServerInterceptors.interceptCallHandlerCreate(binlogInterceptor, binDef.getServerCallHandler());
    // 创建服务方法定义
    return ServerMethodDefinition.create(binMethod, binlogHandler);
  }
```

- io.grpc.services.BinlogHelper#getClientInterceptor

会在每一个事件中输出具体的信息到 BinaryLog 中

```java
  public ClientInterceptor getClientInterceptor(final long callId) {
    return new ClientInterceptor() {
      boolean trailersOnlyResponse = true;

      @Override
      public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(final MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        final String methodName = method.getFullMethodName();
        final String authority = next.authority();
        final Deadline deadline = min(callOptions.getDeadline(), Context.current().getDeadline());

        return new SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
          @Override
          public void start(final ClientCall.Listener<RespT> responseListener, Metadata headers) {
            final Duration timeout = deadline == null ? null : Durations.fromNanos(deadline.timeRemaining(TimeUnit.NANOSECONDS));
            writer.logClientHeader(seq.getAndIncrement(), methodName, authority, timeout, headers, GrpcLogEntry.Logger.LOGGER_CLIENT,callId, /*peerAddress=*/ null);
            ClientCall.Listener<RespT> wListener =
                    new SimpleForwardingClientCallListener<RespT>(responseListener) {
                      @Override
                      public void onMessage(RespT message) {
                        writer.logRpcMessage(seq.getAndIncrement(), EventType.EVENT_TYPE_SERVER_MESSAGE, method.getResponseMarshaller(), message, GrpcLogEntry.Logger.LOGGER_CLIENT, callId);
                        super.onMessage(message);
                      }

                      @Override
                      public void onHeaders(Metadata headers) {
                        trailersOnlyResponse = false;
                        writer.logServerHeader(seq.getAndIncrement(), headers, GrpcLogEntry.Logger.LOGGER_CLIENT, callId, getPeerSocket(getAttributes()));
                        super.onHeaders(headers);
                      }

                      @Override
                      public void onClose(Status status, Metadata trailers) {
                        SocketAddress peer = trailersOnlyResponse ? getPeerSocket(getAttributes()) : null;
                        writer.logTrailer(seq.getAndIncrement(), status, trailers, GrpcLogEntry.Logger.LOGGER_CLIENT, callId, peer);
                        super.onClose(status, trailers);
                      }
                    };
            super.start(wListener, headers);
          }

          @Override
          public void sendMessage(ReqT message) {
            writer.logRpcMessage(seq.getAndIncrement(), EventType.EVENT_TYPE_CLIENT_MESSAGE, method.getRequestMarshaller(), message, GrpcLogEntry.Logger.LOGGER_CLIENT, callId);
            super.sendMessage(message);
          }

          @Override
          public void halfClose() {
            writer.logHalfClose(seq.getAndIncrement(), GrpcLogEntry.Logger.LOGGER_CLIENT, callId);
            super.halfClose();
          }

          @Override
          public void cancel(String message, Throwable cause) {
            writer.logCancel(seq.getAndIncrement(), GrpcLogEntry.Logger.LOGGER_CLIENT, callId);
            super.cancel(message, cause);
          }
        };
      }
    };
  }
```