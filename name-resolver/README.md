# gRPC 中使用自定义的 NameResolver

gRPC 默认使用 DNS 作为命名解析，如果想要使用其他的注册中心，如 Consul 等，就需要扩展命名解析的逻辑
使用 Consul 作为注册中心，实现 Consul 命名解析的逻辑

## 添加依赖

- build.gradle.kts

添加 Consul 相关的依赖

```diff
dependencies {
+   implementation("com.orbitz.consul:consul-client:${consulVersion}")
}
```

## Server 端

Server 端需要增加向注册中心注册的逻辑

- 注册到 Consul

```java
@Slf4j
public class NameResolverServer {

    @SneakyThrows
    public static void main(String[] args) {
        Random random = new Random();
        // 使用随机端口
        int port = random.nextInt(65535);
        String address = Inet4Address.getLocalHost().getHostAddress();
        
        // ... 

        registerToConsul(address, port);
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

        // 向 Consul 注册服务
        agentClient.register(service);
    }
}
```

## Client 端

### 1. 实现自定义命名解析

- CustomNameResolver

`NameResolver` 里面重写了 `start` 和 `refresh` 方法，这两个方法都调用一个 `resolve` 方法做服务发现；
`resovle` 方法内部通过服务名从注册中心拉取服务实例列表，然后调用 `Listener` 的 `onResult`方法，将实例列表传递给 `LoadBalancer`，完成服务解析
在服务运行期间，因为实例可能会发生变化，所以可以通过定时执行触发服务解析；如果注册中心支持，也可以通过回调触发

```java
@Slf4j
public class CustomNameResolver extends NameResolver {

    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(10);

    private final String authority;

    private Listener2 listener;

    private final Consul client;

    public CustomNameResolver(String authority) {
        this.authority = authority;
        this.client = Consul.builder().build();
    }

    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        // 定期从注册中心获取地址
        this.executorService.scheduleAtFixedRate(this::resolve, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void refresh() {
        this.resolve();
    }

    private void resolve() {
        log.info("开始解析服务: {}", this.authority);

        // 从注册中心获取实例列表
        List<InetSocketAddress> addressList = getAddressList(this.authority);
        if (addressList == null || addressList.size() == 0) {
            log.error("解析服务: {} 失败，没有可用的节点", this.authority);
            listener.onError(Status.UNAVAILABLE.withDescription("没有可用的节点"));
            return;
        }

        List<EquivalentAddressGroup> equivalentAddressGroups = addressList.stream()
                                                                          .map(EquivalentAddressGroup::new)
                                                                          .collect(Collectors.toList());

        ResolutionResult resolutionResult = ResolutionResult.newBuilder()
                                                            .setAddresses(equivalentAddressGroups)
                                                            .build();

        this.listener.onResult(resolutionResult);
    }

    /**
     * 从 Consul 获取健康的服务
     *
     * @param serviceName 服务名称
     * @return 服务实例列表
     */
    private List<InetSocketAddress> getAddressList(String serviceName) {
        HealthClient healthClient = client.healthClient();
        ConsulResponse<List<ServiceHealth>> response = healthClient.getHealthyServiceInstances(serviceName);
        List<ServiceHealth> healthList = response.getResponse();

        log.info("从 Consul 中获取到服务: {} 共: {} 个实例", this.authority, healthList.size());

        return healthList.stream()
                         .map(ServiceHealth::getService)
                         .map(service -> new InetSocketAddress(service.getAddress(), service.getPort()))
                         .collect(Collectors.toList());
    }
}
```

- NameResolverProvider

`NameResolverProvider` 主要用于注册 `NameResolver`，可以设置默认的协议，是否可用，优先级等
优先级有效值是 0-10，gRPC 默认的 `DnsNameResolver` 优先级是5，所以自定义的优先级要大于5

```java
public class CustomNameResolverProvider extends NameResolverProvider {
    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        return new CustomNameResolver(targetUri.toString());
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 10;
    }

    @Override
    public String getDefaultScheme() {
        return "http";
    }
}
```

### 2. 注册自定义的 NameResolver

在新版本(1.21+)之后，`NameResolver` 推荐使用 `NameResolverRegistry` 进行注册；
注册要在创建 Channel 之前执行

```java
    public static void main(String[] args) throws InterruptedException {
        // 注册 NameResolver
        NameResolverRegistry.getDefaultRegistry().register(new CustomNameResolverProvider());

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forTarget("grpc-server")
                                                      .usePlaintext()
                                                      // 指定负载均衡策略
                                                      .defaultLoadBalancingPolicy("round_robin")
                                                      .build();

    }
```

在 Channel 调用 `build` 方式时，会在 `io.grpc.internal.ManagedChannelImpl#ManagedChannelImpl`的构造方法中获取 `NameResolver.Factory`，这个属性的值是由调用 `io.grpc.internal.AbstractManagedChannelImplBuilder#getNameResolverFactory` 方法获取的，这个方法里面的属性值来自于 `io.grpc.NameResolverRegistry#asFactory`；`NameResolverRegistry` 自己通过内部类 `NameResolverFactory`创建了`NameResovler.Factory` 的实例，调用 Factory 的 `newNameResolver`时，从 `provider` 属性中获取根据优先级排序后的 `NameResolver`，创建实例并返回第一个创建的有效实例


## 测试

#### 1. 启动 Consul

```bash
docker run --name consul -d -p 8500:8500 consul
```


#### 2. 启动多个 Server

启动后，可以在 Consul 中看到多个 Server

#### 3. 启动 Client 发起请求

当 Client 启动后会循环发出请求，因为配置了 target 是 `grpc-server`，所以会从 Consul 中查找相应的服务，使用 `round_robin` 策略轮询调用服务端，可以看到相关日志：

```java
[pool-2-thread-2] INFO io.github.helloworlde.grpc.nameresovler.CustomNameResolver - 开始解析服务: grpc-server
[pool-2-thread-2] INFO io.github.helloworlde.grpc.nameresovler.CustomNameResolver - 从 Consul 中获取到服务: grpc-server 共: 2 个实例
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:37551
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:17174
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:37551
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:17174
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:37551
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:17174
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:37551
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:17174
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:37551
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:17174
[pool-2-thread-1] INFO io.github.helloworlde.grpc.nameresovler.CustomNameResolver - 开始解析服务: grpc-server
[pool-2-thread-1] INFO io.github.helloworlde.grpc.nameresovler.CustomNameResolver - 从 Consul 中获取到服务: grpc-server 共: 2 个实例
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:37551
[main] INFO io.github.helloworlde.grpc.HelloWorldClient - 192.168.0.113:17174
```