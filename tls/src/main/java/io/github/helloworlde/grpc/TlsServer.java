package io.github.helloworlde.grpc;

import io.grpc.Server;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TlsServer {

    @SneakyThrows
    public static void main(String[] args) {
        // 初始化 SSL 上下文
        File keyCertChainFile = new File("tls/src/main/resources/cert/server.pem");
        File keyFile = new File("tls/src/main/resources/cert/server.key");
        SslContextBuilder builder = SslContextBuilder.forServer(keyCertChainFile, keyFile);
        SslContext sslContext = GrpcSslContexts.configure(builder).build();

        // 构建 Server
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          // 添加服务
                                          .addService(new HelloServiceImpl())
                                          .sslContext(sslContext)
                                          .build();

        // 启动 Server
        server.start();
        log.info("服务端启动成功");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        // 保持运行
        server.awaitTermination();
    }
}

@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        log.info("收到客户端请求: " + request.getMessage());

        // 构建响应
        HelloResponse response = HelloResponse.newBuilder()
                                              .setMessage("Hello " + request.getMessage())
                                              .build();

        // 发送响应
        responseObserver.onNext(response);
        // 结束请求
        responseObserver.onCompleted();
    }
}
