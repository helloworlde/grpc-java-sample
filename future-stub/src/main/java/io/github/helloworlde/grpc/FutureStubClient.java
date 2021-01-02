package io.github.helloworlde.grpc;

import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FutureStubClient {

    @SneakyThrows
    public static void main(String[] args) throws InterruptedException {
        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 FutureStub
        HelloServiceGrpc.HelloServiceFutureStub futureStub = HelloServiceGrpc.newFutureStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Blocking Stub")
                                           .build();

        // 发送消息，返回 Future
        ListenableFuture<HelloResponse> future = futureStub.sayHello(message);

        future.addListener(() -> {
            log.info("Server 端返回响应");
        }, Executors.newCachedThreadPool());

        HelloResponse helloResponse = future.get(5, TimeUnit.SECONDS);
        log.info(helloResponse.getMessage());

        // 等待终止
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
