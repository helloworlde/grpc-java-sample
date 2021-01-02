package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class HelloWorldClient {


    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Blocking Stub")
                                           .build();

        HelloResponse helloResponse = blockingStub.sayHello(message);
        System.out.println(helloResponse.getMessage());

        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
