package io.github.helloworlde.grpc;

import com.google.gson.Gson;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RetryPolicyClient {

    public static void main(String[] args) throws InterruptedException {
        // 读取重试配置文件
        Map<String, ?> config = getServiceConfig();

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      // 添加重试配置，并开启重试
                                                      .defaultServiceConfig(config)
                                                      .enableRetry()
                                                      .build();

        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Retry Policy")
                                           .build();

        // 发送消息，并返回响应
        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());

        // 等待终止
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 读取重试配置文件
     */
    @SuppressWarnings("all")
    @SneakyThrows
    private static Map<String, ?> getServiceConfig() {
        File configFile = new File("retry-policy/src/main/resources/service_config.json");
        Path path = configFile.toPath();
        byte[] bytes = Files.readAllBytes(path);
        return new Gson().fromJson(new String(bytes), Map.class);
    }
}
