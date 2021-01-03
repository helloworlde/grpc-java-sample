package io.github.helloworlde.grpc;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ServerStreamServer {

    @SneakyThrows
    public static void main(String[] args) {
        // 构建 Server
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          // 添加服务
                                          .addService(new HelloServiceImpl())
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
        AtomicInteger counter = new AtomicInteger();

        while (counter.get() < 100) {
            // 构建响应
            HelloResponse response = HelloResponse.newBuilder()
                                                  .setMessage(counter.get() + ": Hello " + request.getMessage())
                                                  .build();

            // 发送响应
            responseObserver.onNext(response);
            counter.getAndIncrement();
        }

        // 结束请求
        responseObserver.onCompleted();
    }
}
