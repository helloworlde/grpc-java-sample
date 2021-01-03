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
public class BidirectionalStreamServer {

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
    public StreamObserver<HelloMessage> sayHello(StreamObserver<HelloResponse> responseObserver) {
        AtomicInteger counter = new AtomicInteger();

        return new StreamObserver<HelloMessage>() {
            @Override
            public void onNext(HelloMessage helloMessage) {
                log.info("接收到客户端请求: {}", helloMessage.getMessage());
                counter.getAndIncrement();
                // 构建响应
                HelloResponse response = HelloResponse.newBuilder()
                                                      .setMessage("Hello " + helloMessage.getMessage())
                                                      .build();

                // 发送响应
                responseObserver.onNext(response);
            }

            @Override
            public void onError(Throwable t) {
                log.info("获取请求错误: {}", t.getMessage(), t);
            }

            @Override
            public void onCompleted() {
                log.info("客户端请求完成");
                // 结束请求
                responseObserver.onCompleted();
            }
        };
    }
}
