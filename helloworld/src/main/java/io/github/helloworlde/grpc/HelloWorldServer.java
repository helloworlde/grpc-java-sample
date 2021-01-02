package io.github.helloworlde.grpc;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class HelloWorldServer {


    public static void main(String[] args) {
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          .addService(new HelloServiceImpl())
                                          .build();

        try {
            server.start();
            System.out.println("服务端启动成功");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        try {
            server.awaitTermination();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}

class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {
    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        System.out.println("收到客户端请求: " + request.getMessage());

        HelloResponse response = HelloResponse.newBuilder()
                                              .setMessage("Hello " + request.getMessage())
                                              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
