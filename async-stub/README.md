# gRPC 使用 AsyncStub

与 [helloworld](../helloworld) 基本相同，不同的是构建的 Stub 是一个 `AbstractAsyncStub`；发起的调用是异步的，调用时时需要两个参数，一个是请求体，另一个实现响应观察器 `StreamObserver`，当接收到响应后会执行响应观察器的回调函数

## 实现 Client 端

```java
// 构建 Channel
ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                              .usePlaintext()
                                              .build();

// 使用 Channel 构建 Stub
HelloServiceGrpc.HelloServiceStub stub = HelloServiceGrpc.newStub(channel);

// 构建消息
HelloMessage message = HelloMessage.newBuilder()
                                   .setMessage("Async Stub")
                                   .build();

// 构建响应观察器
StreamObserver<HelloResponse> streamObserver = new StreamObserver<HelloResponse>() {
    @Override
    public void onNext(HelloResponse helloResponse) {
        log.info("接收到响应: {}", helloResponse.getMessage());
    }

    @Override
    public void onError(Throwable t) {
        log.info("请求失败: {}", t.getMessage(), t);
    }

    @Override
    public void onCompleted() {
        log.info("请求完成");
    }
};
// 发送消息，响应会执行回调函数
stub.sayHello(message, streamObserver);

// 等待终止
channel.awaitTermination(5, TimeUnit.SECONDS);
```