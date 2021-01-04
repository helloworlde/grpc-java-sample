package io.github.helloworlde.grpc;

import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RetryPolicyServer {

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

    private final Random random = new Random();

    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        if (random.nextInt(100) > 30) {
            log.info("收到客户端请求，返回 UNAVAILABLE");
            responseObserver.onError(Status.UNAVAILABLE.withDescription("For retry").asRuntimeException());
        } else {
            log.info("收到客户端请求，返回 OK");
            // 构建响应
            HelloResponse response = HelloResponse.newBuilder()
                                                  .setMessage("Retry Success: " + request.getMessage())
                                                  .build();

            // 发送响应
            responseObserver.onNext(response);
            // 结束请求
            responseObserver.onCompleted();
        }
    }
}
