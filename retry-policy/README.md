# gRPC 中使用重试策略

gRPC 支持重试策略，当 Server 端返回 UNAVAILABLE 等状态，表示不可用时，可以通过配置，自动进行重试；如果 Server 端有多个节点，会根据路由策略请求不同的节点，避免因单个节点不可用导致请求失败 关于 gRPC
重试的设计可以参考 [gRPC Retry Design](https://github.com/grpc/proposal/blob/master/A6-client-retries.md)

## Server 端

修改 Server 端的返回逻辑，随机返回成功或者失败，当返回失败时，如果 Client 有配置重试策略，会再次执行请求

- 服务实现

```diff
@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

+   private final Random random = new Random();

    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
+       if (random.nextInt(10) > 3) {
+           log.info("收到客户端请求，返回 UNAVAILABLE");
+           responseObserver.onError(Status.UNAVAILABLE.withDescription("For retry").asRuntimeException());
+       } else {
            log.info("收到客户端请求，返回 OK");
            // 构建响应
            HelloResponse response = HelloResponse.newBuilder()
                                                  .setMessage("Retry Success: " + request.getMessage())
                                                  .build();

            // 发送响应
            responseObserver.onNext(response);
            // 结束请求
            responseObserver.onCompleted();
+       }
    }
}

```

## 实现 Client 端

Client 需要添加重试的配置，并开启重试

#### 1. 添加配置 service_config.json

- `service` 表示添加重试策略的服务
- `SayHello` 表示为这个方法配置重试策略
- `maxAttempts` 表示最大重试 5 次
- `initialBackoff` 表示重试请求初始延迟时间间隔
- `maxBackoff` 表示重试请求最大延迟时间间隔
- `backoffMultiplier` 表示重试请求退避指数
- `retryableStatusCodes` 表示可重试的响应状态

这些参数共同决定了重试的策略，具体可以参考 [gRPC Retry Design#retry-policy-capabilities](https://github.com/grpc/proposal/blob/master/A6-client-retries.md#retry-policy-capabilities)

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
      "retryPolicy": {
        "maxAttempts": 5,
        "initialBackoff": "0.5s",
        "maxBackoff": "30s",
        "backoffMultiplier": 2,
        "retryableStatusCodes": [
          "UNAVAILABLE"
        ]
      }
    }
  ]
}
```

#### 2. 配置重试

```diff

@Slf4j
public class HelloWorldClient {

    public static void main(String[] args) throws InterruptedException {
+       // 读取重试配置文件
+       Map<String, ?> config = getServiceConfig();

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
+                                                     // 添加重试配置，并开启重试
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
+       File configFile = new File("retry-policy/src/main/resources/service_config.json");
+       Path path = configFile.toPath();
+       byte[] bytes = Files.readAllBytes(path);
+       return new Gson().fromJson(new String(bytes), Map.class);
+   }
}
```

#### 3. 测试

使用 Client 多次发起请求，观察 Server 端日志；客户端只发起了一次请求，但是 Server 端收到了多个，说明重试策略生效

- Client 端

```
[main] INFO io.github.helloworlde.grpc.RetryPolicyClient - Retry Success: Retry Policy
```

- Server 端

```
[grpc-default-executor-0] INFO io.github.helloworlde.grpc.HelloServiceImpl - 收到客户端请求，返回 UNAVAILABLE
[grpc-default-executor-0] INFO io.github.helloworlde.grpc.HelloServiceImpl - 收到客户端请求，返回 UNAVAILABLE
[grpc-default-executor-0] INFO io.github.helloworlde.grpc.HelloServiceImpl - 收到客户端请求，返回 OK
```