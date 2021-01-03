package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ServerStreamClient {

    public static void main(String[] args) throws InterruptedException {
        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Server Stream")
                                           .build();

        // 发送消息，并返回响应
        Iterator<HelloResponse> helloResponses = blockingStub.sayHello(message);

        while (helloResponses.hasNext()) {
            log.info(helloResponses.next().getMessage());
        }

        // 等待终止
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
