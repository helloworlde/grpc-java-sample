package io.github.helloworlde.grpc;

import io.github.helloworlde.grpc.nameresovler.CustomNameResolverProvider;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class NameResolverClient {

    public static void main(String[] args) throws InterruptedException {
        // 注册 NameResolver
        NameResolverRegistry.getDefaultRegistry().register(new CustomNameResolverProvider());

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forTarget("grpc-server")
                                                      .usePlaintext()
                                                      // 指定负载均衡策略
                                                      .defaultLoadBalancingPolicy("round_robin")
                                                      .build();

        for (int i = 0; i < 100; i++) {
            sendRequest(channel);
            Thread.sleep(1000);
        }

        // 等待终止
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void sendRequest(ManagedChannel channel) {
        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Custom NameResolver")
                                           .build();

        // 发送消息，并返回响应
        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());
    }
}
