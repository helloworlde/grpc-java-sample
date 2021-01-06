package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.grpc.services.ChannelzService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ChannelzClient {

    @SneakyThrows
    public static void main(String[] args) throws InterruptedException {

        // 构建并启动 Channelz 服务
        Server server = NettyServerBuilder.forPort(9091)
                                          // 添加 Channelz 服务
                                          .addService(ChannelzService.newInstance(100))
                                          // 添加反射服务，用于 grpcurl 等工具调试
                                          .addService(ProtoReflectionService.newInstance())
                                          .build()
                                          .start();

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 发送多个请求，用于观察数据变化
        for (int i = 0; i < 10000; i++) {
            // 构建消息
            HelloMessage message = HelloMessage.newBuilder()
                                               .setMessage("Channelz " + i)
                                               .build();

            // 发送消息，并返回响应
            HelloResponse helloResponse = blockingStub.sayHello(message);
            log.info(helloResponse.getMessage());
            Thread.sleep(1000);
        }

        // 等待终止
        server.awaitTermination();
    }
}
