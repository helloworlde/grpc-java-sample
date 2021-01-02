package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
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
        log.info(helloResponse.getMessage());

        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
