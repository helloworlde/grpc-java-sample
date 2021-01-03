# gRPC 发送 Server 端流

与 [helloworld](../helloworld) 大致相同，不同的是 proto 定义修改了返回值，Client 端的返回值发生了变化， Server 端的发送变成了多个

## 修改 proto

```diff
service HelloService{
- rpc SayHello(HelloMessage) returns (HelloResponse){
+ rpc SayHello(HelloMessage) returns (stream HelloResponse){
  }
}
```

## 实现 Server 端

- 服务实现

```diff
@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        log.info("收到客户端请求: " + request.getMessage());
+       AtomicInteger counter = new AtomicInteger();
+       while (counter.get() < 100) {
            // 构建响应
            HelloResponse response = HelloResponse.newBuilder()
                                                  .setMessage(counter.get() + ": Hello " + request.getMessage())
                                                  .build();

            // 发送响应
            responseObserver.onNext(response);
+           counter.getAndIncrement();
        }
        
        responseObserver.onCompleted();
    }
}
```

## 实现 Client 端

使用 BlockingStub 发起请求，这个调用是同步的，返回值由 `HelloResponse`变成了`Iterator<HelloResponse>`

```diff
@Slf4j
public class HelloWorldClient {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Blocking Stub")
                                           .build();

-       HelloResponse helloResponse = blockingStub.sayHello(message);
        // 发送消息，并返回响应
+       Iterator<HelloResponse> helloResponses = blockingStub.sayHello(message);
+       while (helloResponses.hasNext()) {
            log.info(helloResponses.next().getMessage());
+       }
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```