# gRPC 发送双向流

与 [ClientStream](../client-stream) 大致相同，proto 参数和返回值都是 stream，Client 端只能使用 `AsyncStub` 发送，Server 端返回值不同

双向流和客户端流区别不大，只是声明了双向流，在 Server 端发送多个时不会限制数量，其他的和客户端流基本一致

## 修改 proto

```diff
service HelloService{
- rpc SayHello(stream HelloMessage) returns (HelloResponse){
+ rpc SayHello(stream HelloMessage) returns (stream HelloResponse){
  }
}
```

## 实现 Server 端

- 服务实现
             
Server 端没有变化，返回值是 `StreamObserver<HelloMessage>`，客户端的请求会在这个对象的 `onNext`方法中接收

```diff
@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {
    
    @Override
    public StreamObserver<HelloMessage> sayHello(StreamObserver<HelloResponse> responseObserver) {
        AtomicInteger counter = new AtomicInteger();

        return new StreamObserver<HelloMessage>() {
            @Override
            public void onNext(HelloMessage helloMessage) {
                log.info("接收到客户端请求: {}", helloMessage.getMessage());
+               counter.getAndIncrement();
+               // 构建响应
+               HelloResponse response = HelloResponse.newBuilder()
+                                                     .setMessage("Hello " + helloMessage.getMessage())
+                                                     .build();
+
+               // 发送响应
+               responseObserver.onNext(response);
            }
 
            @Override
            public void onError(Throwable t) {
                log.info("获取请求错误: {}", t.getMessage(), t);
            }
 
            @Override
            public void onCompleted() {
                log.info("客户端请求完成");
                // 结束请求
                responseObserver.onCompleted();
            }
        };
    }
}
```

## 实现 Client 端

Client 端和客户端流相比没有变化，只能使用 `AsyncStub` 发起请求，通过 `StreamObserver` 的 `onNext` 方法发送请求，由 `onComplete` 方法表示请求完成

```diff
    public static void main(String[] args) throws InterruptedException {
        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 AsyncStub
        HelloServiceGrpc.HelloServiceStub stub = HelloServiceGrpc.newStub(channel);

        StreamObserver<HelloResponse> streamObserver = new StreamObserver<HelloResponse>() {
            @Override
            public void onNext(HelloResponse value) {
                log.info("Server 端返回响应: {}", value.getMessage());
            }
 
            @Override
            public void onError(Throwable t) {
                log.error("请求失败: {}", t.getMessage(), t);
            }
 
            @Override
            public void onCompleted() {
                log.info("请求完成");
            }
        };


        // 发送消息，并返回响应
        StreamObserver<HelloMessage> requestObserver = stub.sayHello(streamObserver);

        for (int i = 0; i < 100; i++) {
            // 构建消息
            HelloMessage message = HelloMessage.newBuilder()
                                               .setMessage(i + " Server Stream")
                                               .build();

            requestObserver.onNext(message);
        }
        requestObserver.onCompleted();

        // 等待终止
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
```