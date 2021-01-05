package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class TlsClient {

    @SneakyThrows
    public static void main(String[] args) {
        setLogger("io.grpc");

        File trustCertCollectionFile = new File("tls/src/main/resources/cert/server.pem");
        SslContextBuilder builder = GrpcSslContexts.forClient();
        SslContext sslContext = builder.trustManager(trustCertCollectionFile).build();

        // 构建 Channel
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", 9090)
                                                    .overrideAuthority("localhost")
                                                    .sslContext(sslContext)
                                                    .build();

        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("TLS")
                                           .build();

        // 发送消息，并返回响应
        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());

        // 等待终止
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void setLogger(String className) {
        Logger logger = Logger.getLogger(className);
        logger.setLevel(Level.ALL);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }
}
