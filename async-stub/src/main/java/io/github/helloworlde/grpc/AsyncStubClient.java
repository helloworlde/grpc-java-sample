package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class AsyncStubClient {

    @SneakyThrows
    public static void main(String[] args) throws InterruptedException {
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
    }
}
