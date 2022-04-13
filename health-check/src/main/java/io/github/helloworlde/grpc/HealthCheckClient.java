package io.github.helloworlde.grpc;

import io.github.helloworlde.grpc.nameresovler.CustomNameResolverProvider;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.NameResolverRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

@Slf4j
public class HealthCheckClient {

    public static void main(String[] args) throws InterruptedException {
        // 修改日志级别
        setLogLevel();

        // 注册 NameResolver
        NameResolverRegistry.getDefaultRegistry().register(new CustomNameResolverProvider());

        // 加载配置
        HashMap<String, Object> config = loadServiceConfig();

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forTarget("grpc-server")
                                                      .usePlaintext()
                                                      // 指定负载均衡策略
                                                      .defaultLoadBalancingPolicy("round_robin")
                                                      // 指定配置
                                                      .defaultServiceConfig(config)
                                                      .build();

        for (int i = 0; i < 100; i++) {
            sendRequest(channel);
            Thread.sleep(1000);
        }

        // 等待终止
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static void setLogLevel() {
        Logger logger = Logger.getLogger("io.grpc.ChannelLogger");
        logger.setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    /**
     * 加载配置
     */
    private static HashMap<String, Object> loadServiceConfig() {
        // 添加健康检查配置
        return new HashMap<String, Object>() {{
            put("healthCheckConfig", new HashMap<String, Object>() {{
                put("serviceName", "");
            }});
        }};
    }

    private static void sendRequest(ManagedChannel channel) {
        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Custom NameResolver")
                                           .build();

        // 发送消息，并返回响应
        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());
    }
}
