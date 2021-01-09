# gRPC 反射服务

gRPC 提供了 `grpc.reflection.v1alpha.ServerReflection` 服务，在 Server 端添加后可以通过该服务获取所有服务的信息，包括服务定义，方法，属性等；

可以根据获取到的服务信息调用其他的方法，实现泛化调用；gRPC 调试工具 [grpcurl]() 和 [gRPC Swagger](https://github.com/grpc-swagger/grpc-swagger) 等工具都是通过这种方式实现的

## 定义

参考 [GRPC Server Reflection Protocol](https://github.com/grpc/grpc/blob/master/doc/server-reflection.md) 和 [reflection.proto](https://github.com/grpc/grpc/blob/master/src/proto/grpc/reflection/v1alpha/reflection.proto)

该服务只有一个双向流的方法 `ServerReflectionInfo`，调用时根据请求参数不同，调用不同的方法进行处理，并返回响应；该方法的流控是非自动的，只有当一个请求完成之后才会获取下一个请求 

```protobuf
service ServerReflection {
  rpc ServerReflectionInfo(stream ServerReflectionRequest) returns (stream ServerReflectionResponse);
}

message ServerReflectionRequest {
  string host = 1;
  oneof message_request {
    // 根据服务名查询 proto 文件
    string file_by_filename = 3;

    // 根据名称获取 proto 文件，如 <package>.<service>[.<method>] 或 <package>.<type>
    string file_containing_symbol = 4;

    // 根据 message 类型和序号获取 proto 文件
    ExtensionRequest file_containing_extension = 5;

    // 查找给定消息类型的所有已知扩展使用的标记号，并将它们以未定义的顺序附加到ExtensionNumberResponse
    string all_extension_numbers_of_type = 6;

    // 查询所有的服务
    string list_services = 7;
  }
}
```

## Server 端

- 服务实现

```diff
@Slf4j
public class ReflectionServer {

    @SneakyThrows
    public static void main(String[] args) {
        // 构建 Server
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          // 添加服务
                                          .addService(new HelloServiceImpl())
                                          // 添加反射服务
+                                         .addService(ProtoReflectionService.newInstance())
                                          .build();

        // 启动 Server
        server.start();
        log.info("服务端启动成功");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        // 保持运行
        server.awaitTermination();
    }
}    
```

## Client 端

发起双向流请求

```java
@Slf4j
public class ReflectionClient {

    public static void main(String[] args) throws InterruptedException {
        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 BlockingStub
        ServerReflectionGrpc.ServerReflectionStub reflectionStub = ServerReflectionGrpc.newStub(channel);

        StreamObserver<ServerReflectionResponse> streamObserver = new StreamObserver<ServerReflectionResponse>() {
            @Override
            public void onNext(ServerReflectionResponse response) {
                log.info("{}", response);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {
                log.info("Complete");
            }
        };

        StreamObserver<ServerReflectionRequest> requestStreamObserver = reflectionStub.serverReflectionInfo(streamObserver);

        // 列举所有的服务
        ServerReflectionRequest listServiceRequest = ServerReflectionRequest.newBuilder()
                                                                            .setListServices("")
                                                                            .build();
        requestStreamObserver.onNext(listServiceRequest);
    }
}
```

其他的方法使用请参考 [ReflectionClient](reflection/src/main/java/io/github/helloworlde/grpc/ReflectionClient.java)

## 实现原理

在 Server 端启动时，将反射服务添加到服务中，当客户端触发调用后，会执行 `io.grpc.protobuf.services.ProtoReflectionService.getRefreshedIndex` 方法，会从 `Server` 中获取所有的可变和不可变的服务，遍历获取所有的服务、方法、属性，添加到 `ServerReflectionIndex` 对象中

- io.grpc.protobuf.services.ProtoReflectionService.getRefreshedIndex

```java
    private ServerReflectionIndex getRefreshedIndex() {
        synchronized (lock) {
            Server server = InternalServer.SERVER_CONTEXT_KEY.get();
            ServerReflectionIndex index = serverReflectionIndexes.get(server);

            if (index == null) {
                index = new ServerReflectionIndex(server.getImmutableServices(), server.getMutableServices());
                serverReflectionIndexes.put(server, index);
                return index;
            }
            
            // 更新可变服务信息 ... 
            return index;
        }
    }
```

然后处理请求，会调用 `io.grpc.protobuf.services.ProtoReflectionService.ProtoReflectionStreamObserver.handleReflectionRequest` 方法，根据请求参数进行判断，使用不同的方法处理，并返回响应

```java
private void handleReflectionRequest() {
    if (serverCallStreamObserver.isReady()) {
        switch (request.getMessageRequestCase()) {
            case FILE_BY_FILENAME:
                getFileByName(request);
                break;
            case FILE_CONTAINING_SYMBOL:
                getFileContainingSymbol(request);
                break;
            case FILE_CONTAINING_EXTENSION:
                getFileByExtension(request);
                break;
            case ALL_EXTENSION_NUMBERS_OF_TYPE:
                getAllExtensions(request);
                break;
            case LIST_SERVICES:
                listServices(request);
                break;
            default:
                sendErrorResponse(request, Status.Code.UNIMPLEMENTED, "not implemented " + request.getMessageRequestCase());
        }
        request = null;
        // 如果在发送完成后关闭，则关闭流，否则要求下一个请求
        if (closeAfterSend) {
            serverCallStreamObserver.onCompleted();
        } else {
            serverCallStreamObserver.request(1);
        }
    }
}
```

## 参考文档

- [gRPC Server Reflection Tutorial](https://github.com/grpc/grpc-java/blob/master/documentation/server-reflection-tutorial.md#enable-server-reflection)
- [Reflection](https://github.com/grpc/grpc-go/tree/master/reflection)
- [gRPC Server Reflection Tutorial](https://chromium.googlesource.com/external/github.com/grpc/grpc-go/+/HEAD/Documentation/server-reflection-tutorial.md)