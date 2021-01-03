# gRPC 使用 FutureStub

与 [helloworld](../helloworld) 基本相同，不同的是构建的 Stub 是一个 FutureStub；发起的调用是异步的，当调用后会返回一个 Future，这个 Future 可以阻塞的获取响应结果，也可以为这个 Future 添加一个回调事件，当接收到响应后触发

## 实现 Client 端

```java
// 使用 Channel 构建 FutureStub
HelloServiceGrpc.HelloServiceFutureStub futureStub = HelloServiceGrpc.newFutureStub(channel);

// 构建消息
HelloMessage message = HelloMessage.newBuilder()
                                   .setMessage("Future Stub")
                                   .build();

// 发送消息，返回 Future
ListenableFuture<HelloResponse> future = futureStub.sayHello(message);

future.addListener(() -> {
    log.info("Server 端返回响应");
}, Executors.newCachedThreadPool());

HelloResponse helloResponse = future.get(5, TimeUnit.SECONDS);
log.info(helloResponse.getMessage());
```