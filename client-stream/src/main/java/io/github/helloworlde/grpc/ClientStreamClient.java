package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ClientStreamClient {

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
}
