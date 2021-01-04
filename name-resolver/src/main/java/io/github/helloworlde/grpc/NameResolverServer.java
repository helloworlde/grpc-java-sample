package io.github.helloworlde.grpc;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.Consul;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NameResolverServer {

    @SneakyThrows
    public static void main(String[] args) {
        Random random = new Random();
        int port = random.nextInt(65535);
        String address = Inet4Address.getLocalHost().getHostAddress();

        // 构建 Server
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(port))
                                          // 添加服务
                                          .addService(new HelloServiceImpl(address, port))
                                          .build();

        // 启动 Server
        server.start();
        log.info("服务端启动成功");

        registerToConsul(address, port);

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

    @SneakyThrows
    private static void registerToConsul(String address, int port) {
        Consul client = Consul.builder().build();
        AgentClient agentClient = client.agentClient();

        String serviceId = "Server-" + UUID.randomUUID().toString();
        Registration service = ImmutableRegistration.builder()
                                                    .id(serviceId)
                                                    .name("grpc-server")
                                                    .address(address)
                                                    .check(Registration.RegCheck.tcp(address + ":" + port, 10, 10))
                                                    .port(port)
                                                    .tags(Collections.singletonList("server"))
                                                    .meta(Collections.singletonMap("version", "1.0"))
                                                    .build();

        agentClient.register(service);
    }
}

@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    private final String address;
    private final int port;

    public HelloServiceImpl(String address, int port) {
        this.address = address;
        this.port = port;
    }

    @SneakyThrows
    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        log.info("收到客户端请求: " + request.getMessage());

        // 构建响应
        HelloResponse response = HelloResponse.newBuilder()
                                              .setMessage(address + ":" + port)
                                              .build();

        // 发送响应
        responseObserver.onNext(response);
        // 结束请求
        responseObserver.onCompleted();
    }
}
