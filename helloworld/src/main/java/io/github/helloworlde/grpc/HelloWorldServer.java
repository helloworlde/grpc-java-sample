package io.github.helloworlde.grpc;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HelloWorldServer {

    @SneakyThrows
    public static void main(String[] args) {
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          .addService(new HelloServiceImpl())
                                          .build();

        server.start();
        log.info("服务端启动成功");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        server.awaitTermination();
    }
}

@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        log.info("收到客户端请求: " + request.getMessage());

        HelloResponse response = HelloResponse.newBuilder()
                                              .setMessage("Hello " + request.getMessage())
                                              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
