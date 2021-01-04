# gRPC 中使用对冲策略

gRPC 支持对冲策略，当 Server 端超过设定的时间没有返回时，会再次发送请求，直到达到最大次数、超时或者服务端返回；如果 Server 端有多个节点，会根据路由策略请求不同的节点，避免因单个节点不可用导致请求失败 关于 gRPC
对冲的设计可以参考 [gRPC Retry Design](https://github.com/grpc/proposal/blob/master/A6-client-retries.md)

对冲策略通常用于优化长尾请求

## Server 端

修改 Server 端的返回逻辑，随机添加线程等待，如果 Client 有配置对冲策略，会再次执行请求

- 服务实现

```diff
@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

+   private final Random random = new Random();

    @SneakyThrows
    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        log.info("收到客户端请求: {}", request.getMessage());
+       if (random.nextInt(100) > 30) {
+           Thread.sleep(1000);
+       }

        // 构建响应
        HelloResponse response = HelloResponse.newBuilder()
                                              .setMessage("Hedging Success: " + request.getMessage())
                                              .build();

        // 发送响应
        responseObserver.onNext(response);
        // 结束请求
        responseObserver.onCompleted();
    }
}

```

## 实现 Client 端

Client 需要添加对冲的配置，并开启重试

#### 1. 添加配置 service_config.json

```json
{
  "methodConfig": [
    {
      "name": [
        {
          "service": "io.github.helloworlde.grpc.HelloService",
          "method": "SayHello"
        }
      ],
      "hedgingPolicy": {
        "maxAttempts": 5,
        "hedgingDelay": "0.5s"
      }
    }
  ]
}
```

- `service` 表示添加重试策略的服务
- `method` 表示为这个方法配置重试策略
- `maxAttempts` 表示最大重试 5 次
- `hedgingDelay` 表示等待 Server 端返回的时间，如果超过这个时间还没有返回则发送下一个对冲请求

这些参数共同决定了对冲的策略，具体可以参考 [gRPC Retry Design#hedging-policy](https://github.com/grpc/proposal/blob/master/A6-client-retries.md#hedging-policy)

#### 2. 配置重试

```diff
@Slf4j
public class HelloWorldClient {

    public static void main(String[] args) throws InterruptedException {
+       // 读取配置文件
+       Map<String, ?> config = getServiceConfig();

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
+                                                     // 添加配置，并开启重试
+                                                     .defaultServiceConfig(config)
+                                                     .enableRetry()
                                                      .build();


        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Retry Policy")
                                           .build();

        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());

        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
    
+   @SneakyThrows
+   private static Map<String, ?> getServiceConfig() {
+       File configFile = new File("hedging-policy/src/main/resources/service_config.json");
+       Path path = configFile.toPath();
+       byte[] bytes = Files.readAllBytes(path);
+       return new Gson().fromJson(new String(bytes), Map.class);
+   }
}
```

#### 3. 测试

使用 Client 多次发起请求，观察 Server 端日志；客户端只发起了一次请求，但是 Server 端收到了多个，说明对冲策略生效

- Client 端

```
[main] INFO io.github.helloworlde.grpc.HedgingPolicyClient - Retry Success: Hedging Policy
```

- Server 端

```
[grpc-default-executor-0] INFO io.github.helloworlde.grpc.HelloServiceImpl - 收到客户端请求: Hedging Policy
[grpc-default-executor-1] INFO io.github.helloworlde.grpc.HelloServiceImpl - 收到客户端请求: Hedging Policy
[grpc-default-executor-2] INFO io.github.helloworlde.grpc.HelloServiceImpl - 收到客户端请求: Hedging Policy
```