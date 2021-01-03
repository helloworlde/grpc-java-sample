# gRPC 中使用 StreamTracer 监听流

gRPC 提供了拦截器可以监听请求的事件，但是对于 Stream 的具体事件，无法通过拦截器实现；gRPC 提供了 StreamTracer 支持这样的能力，可以用于打点统计流信息

## StreamTracer

`StreamTracer` 用于监听流的所有事件，包括流关闭、出入站消息、出入站流大小等信息

`StreamTracer` 有用于客户端的 `ClientStreamTracer` 和用于服务端的 `ServerStreamTracer`

### 客户端

客户端的 `StreamTracer` 在拦截器中注入，当有请求被执行时，可以向 `callOptions` 添加自定义的 `ClientStreamTracer.Factory`，这样就会创建相应的 `StreamTracer`，实现监听


- CustomClientInterceptor.java

在拦截器中指定

```java
public class CustomClientInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        callOptions = callOptions.withStreamTracerFactory(new CustomClientStreamTracerFactory<>(method, callOptions, next));

        return next.newCall(method, callOptions);
    }
}
```

- CustomClientStreamTracerFactory.java

在实现类中重写需要监听的事件方法

```java
public class CustomClientStreamTracerFactory<ReqT, RespT> extends ClientStreamTracer.Factory {

    private final MethodDescriptor<ReqT, RespT> method;
    private final CallOptions callOptions;
    private final Channel next;

    public CustomClientStreamTracerFactory(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        this.method = method;
        this.callOptions = callOptions;
        this.next = next;
    }

    @Override
    public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
        return new CustomClientStreamTracer<>(method, callOptions, next, info, headers);
    }
}

@Slf4j
class CustomClientStreamTracer<ReqT, RespT> extends ClientStreamTracer {

    public CustomClientStreamTracer(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next, StreamInfo info, Metadata headers) {
        log.info("method: {}", method.getFullMethodName());
    }
}

```

### 服务端

服务端的 `StreamTracer` 在构建 Server 时指定，在执行请求时会监听相应的方法

- 构建 Server

```java
Server server = ServerBuilder.forPort(9090) 
                             .addStreamTracerFactory(new CustomServerStreamTracerFactory())
                             .build();
```

- CustomServerStreamTracerFactory.java

可以在 `CustomServerStreamTracer` 中重写要监听的事件的方法

```java
public class CustomServerStreamTracerFactory extends ServerStreamTracer.Factory {
    @Override
    public ServerStreamTracer newServerStreamTracer(String fullMethodName, Metadata headers) {
        return new CustomServerStreamTracer(fullMethodName, headers);
    }
}

@Slf4j
class CustomServerStreamTracer extends ServerStreamTracer {

    private final String fullMethodName;
    private final Metadata headers;

    public CustomServerStreamTracer(String fullMethodName, Metadata headers) {
        this.fullMethodName = fullMethodName;
        this.headers = headers;
        log.info("method: {}, header: {}", this.fullMethodName, this.headers);
    }
}
```