# gRPC 中使用自定义的 LoadBalancer

gRPC 中提供了 `round_robin`, `pick_first`, `grpclb`, `HealthCheckingRoundRobin` 等负载均衡的实现，默认使用`HealthCheckingRoundRobin`，该负载均衡支持检查 Subchannel 的健康状态

LoadBalancer 主要类包括 `LoadBalancerProvider`, `LoadBalancer`, `SubchannelPicker`, `LoadBalancer.SubchannelStateListener `，所以实现自定义的 LoadBalancer 实现这几个类就可以

在 [NameResolver](../name-resolver) 的基础上实现自定义的 LoadBalancer

## 自定义 LoadBalancer

自定义实现一个轮询策略的负载均衡器

- CustomLoadBalancerProvider.java

```java
public class CustomLoadBalancerProvider extends LoadBalancerProvider {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getPolicyName() {
        return "custom_round_robin";
    }

    @Override
    public LoadBalancer newLoadBalancer(LoadBalancer.Helper helper) {
        return new CustomLoadBalancer(helper);
    }
}
```

- CustomLoadBalancer.java

在 CustomLoadBalancer 中实现了地址的处理，根据地址创建 Subchannel，并启动 Subchannel 状态监听器

```java
@Slf4j
public class CustomLoadBalancer extends LoadBalancer {

    public static final Attributes.Key<Ref<ConnectivityState>> STATE_INFO = Attributes.Key.create("state-info");

    private final Helper helper;

    Map<EquivalentAddressGroup, Subchannel> subchannelMap = new ConcurrentHashMap<>();

    public CustomLoadBalancer(Helper helper) {
        this.helper = helper;
    }

    @Override
    public void handleResolvedAddresses(ResolvedAddresses resolvedAddresses) {
        log.info("处理地址:{}", resolvedAddresses.getAddresses().toString());

        // 将解析的地址分割成单个 Address
        List<EquivalentAddressGroup> latestAddresses = resolvedAddresses.getAddresses()
                                                                        .stream()
                                                                        .flatMap(this::splitAddressCollection)
                                                                        .distinct()
                                                                        .collect(Collectors.toList());

        // 已经存在的地址
        Set<EquivalentAddressGroup> originAddresses = subchannelMap.keySet();

        // 对新的 Address 创建 Subchannel
        Map<EquivalentAddressGroup, Subchannel> newSubchannelMap = latestAddresses.stream()
                                                                                  .filter(e -> !originAddresses.contains(e))
                                                                                  .map(this::buildCreateSubchannelArgs)
                                                                                  .map(helper::createSubchannel)
                                                                                  .map(this::processSubchannel)
                                                                                  .collect(Collectors.toConcurrentMap(Subchannel::getAddresses, s -> s));

        // 将已存在的 Subchannel 放到新的集合中
        originAddresses.stream()
                       .filter(latestAddresses::contains)
                       .forEach(e -> newSubchannelMap.put(e, subchannelMap.get(e)));


        // 关闭需要移除的 Subchannel
        originAddresses.stream()
                       .filter(e -> !latestAddresses.contains(e))
                       .map(e -> subchannelMap.get(e))
                       .forEach(Subchannel::shutdown);

        subchannelMap = newSubchannelMap;
    }

    private CreateSubchannelArgs buildCreateSubchannelArgs(EquivalentAddressGroup e) {
        return CreateSubchannelArgs.newBuilder()
                                   .setAddresses(e)
                                   .setAttributes(Attributes.newBuilder()
                                                            .set(STATE_INFO, new Ref<>(IDLE))
                                                            .build())
                                   .build();
    }

    private Stream<EquivalentAddressGroup> splitAddressCollection(EquivalentAddressGroup equivalentAddressGroup) {
        Attributes attributes = equivalentAddressGroup.getAttributes();
        return equivalentAddressGroup.getAddresses()
                                     .stream()
                                     .map(e -> new EquivalentAddressGroup(e, attributes));
    }

    private Subchannel processSubchannel(Subchannel subchannel) {
        if (subchannelMap.containsValue(subchannel)) {
            log.info("{} {} 已经存在", subchannel, subchannel.getAddresses());
            return subchannel;
        }

        subchannel.start(new CustomSubchannelStateListener(this, subchannel, helper));
        subchannel.requestConnection();
        return subchannel;
    }


    @Override
    public void handleNameResolutionError(Status error) {
        log.info("命名解析失败:{}", error);
        helper.updateBalancingState(ConnectivityState.TRANSIENT_FAILURE, new CustomSubchannelPicker(PickResult.withNoResult()));
    }

    @Override
    public void shutdown() {
        subchannelMap.values()
                     .stream()
                     .peek(s -> log.info("关闭 {} {}", s, s.getAddresses()))
                     .forEach(Subchannel::shutdown);
    }

    public Map<EquivalentAddressGroup, Subchannel> getSubchannelMap() {
        return new ConcurrentHashMap<>(this.subchannelMap);
    }
}
```

- CustomSubchannelStateListener.java

Subchannel 的状态监听器，当 Subchannel 状态发生变化时进行处理

```java
@Slf4j
class CustomSubchannelStateListener implements LoadBalancer.SubchannelStateListener {
    private final LoadBalancer.Subchannel subchannel;
    private final LoadBalancer.Helper helper;
    private final CustomLoadBalancer loadBalancer;

    public CustomSubchannelStateListener(CustomLoadBalancer customLoadBalancer,
                                         LoadBalancer.Subchannel subchannel,
                                         LoadBalancer.Helper helper) {
        this.loadBalancer = customLoadBalancer;
        this.subchannel = subchannel;
        this.helper = helper;
    }

    @Override
    public void onSubchannelState(ConnectivityStateInfo stateInfo) {
        Ref<ConnectivityState> stateInfoRef = subchannel.getAttributes().get(STATE_INFO);
        ConnectivityState currentState = stateInfoRef.getValue();
        ConnectivityState newState = stateInfo.getState();

        log.info("{} 状态变化:{}", subchannel, newState);

        if (newState == SHUTDOWN) {
            log.info("关闭 {}", subchannel);
            return;
        }

        if (newState == READY) {
            subchannel.requestConnection();
        }

        if (currentState == TRANSIENT_FAILURE) {
            if (newState == CONNECTING || newState == IDLE) {
                log.info("{} 建立连接或者失败", subchannel);
                return;
            }
        }

        stateInfoRef.setValue(newState);
        updateLoadBalancerState();
    }

    private void updateLoadBalancerState() {
        List<LoadBalancer.Subchannel> readySubchannels = loadBalancer.getSubchannelMap()
                                                                     .values()
                                                                     .stream()
                                                                     .filter(s -> s.getAttributes().get(STATE_INFO).getValue() == READY)
                                                                     .collect(Collectors.toList());

        if (readySubchannels.isEmpty()) {
            log.info("更新 LB 状态为 CONNECTING，没有 READY 的 Subchannel");
            helper.updateBalancingState(CONNECTING, new CustomSubchannelPicker(LoadBalancer.PickResult.withNoResult()));
        } else {
            log.info("更新 LB 状态为 READY，Subchannel 为:{}", readySubchannels.toArray());
            helper.updateBalancingState(READY, new CustomSubchannelPicker(readySubchannels));
        }
    }
}

```

- CustomSubchannelPicker.java

实现选择 Subchannel 的逻辑，这里使用的是轮询策略

```java 
@Slf4j
class CustomSubchannelPicker extends LoadBalancer.SubchannelPicker {

    private final AtomicInteger index = new AtomicInteger();

    private List<LoadBalancer.Subchannel> subchannelList;

    private LoadBalancer.PickResult pickResult;

    public CustomSubchannelPicker(LoadBalancer.PickResult pickResult) {
        this.pickResult = pickResult;
    }

    public CustomSubchannelPicker(List<LoadBalancer.Subchannel> subchannelList) {
        this.subchannelList = subchannelList;
    }

    @Override
    public LoadBalancer.PickResult pickSubchannel(LoadBalancer.PickSubchannelArgs args) {
        if (pickResult != null) {
            log.info("有错误的 pickResult，返回:{}", pickResult);
            return pickResult;
        }
        LoadBalancer.PickResult pickResult = nextSubchannel(args);
        log.info("Pick 下一个 Subchannel:{}", pickResult.getSubchannel());
        return pickResult;
    }

    private LoadBalancer.PickResult nextSubchannel(LoadBalancer.PickSubchannelArgs args) {
        if (index.get() >= subchannelList.size()) {
            index.set(0);
        }

        LoadBalancer.Subchannel subchannel = subchannelList.get(index.getAndIncrement());

        log.info("返回 Subchannel:{}", subchannel);
        return LoadBalancer.PickResult.withSubchannel(subchannel);
    }
}
```

- Ref.java

用于保存 Subchannel 状态的工具类

```java
final class Ref<T> {
    T value;

    Ref(T value) {
        this.value = value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }
}
``` 

## 使用自定义的 LoadBalancer 策略

- 客户端

需要注册自定义的负载均衡策略，然后指定策略名称

```java
    public static void main(String[] args) throws InterruptedException {
        // 注册 NameResolver
        NameResolverRegistry.getDefaultRegistry().register(new CustomNameResolverProvider());
        // 注册 LoadBalancer
        LoadBalancerRegistry.getDefaultRegistry().register(new CustomLoadBalancerProvider());

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forTarget("grpc-server")
                                                      .usePlaintext()
                                                      // 指定负载均衡策略
                                                      .defaultLoadBalancingPolicy("custom_round_robin")
                                                      .build();

    }
```

## 测试

1. 启动多个 Server 端

2. 启动客户端，发送多个请求

```java
[pool-2-thread-1] INFO io.github.helloworlde.grpc.nameresolver.CustomNameResolver - 开始解析服务: grpc-server
[pool-2-thread-1] INFO io.github.helloworlde.grpc.nameresolver.CustomNameResolver - 从 Consul 中获取到服务: grpc-server 共: 1 个实例
[pool-2-thread-1] INFO io.github.helloworlde.grpc.loadbalcner.CustomLoadBalancer - 处理地址:[[[/192.168.0.113:52259]/{}]]
[pool-2-thread-1] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelStateListener - Subchannel<3>: (grpc-server) 状态变化:CONNECTING
[pool-2-thread-1] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelStateListener - 更新 LB 状态为 CONNECTING，没有 READY 的 Subchannel
[pool-2-thread-1] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - 有错误的 pickResult，返回:PickResult{subchannel=null, streamTracerFactory=null, status=Status{code=OK, description=null, cause=null}, drop=false}
[grpc-nio-worker-ELG-1-2] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelStateListener - Subchannel<3>: (grpc-server) 状态变化:READY
[grpc-nio-worker-ELG-1-2] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelStateListener - 更新 LB 状态为 READY，Subchannel 为:Subchannel<3>: (grpc-server)
[grpc-nio-worker-ELG-1-2] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - 返回 Subchannel:Subchannel<3>: (grpc-server)
[grpc-nio-worker-ELG-1-2] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - Pick 下一个 Subchannel:Subchannel<3>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.LoadBalancerClient - 192.168.0.113:52259
[main] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - 返回 Subchannel:Subchannel<5>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - Pick 下一个 Subchannel:Subchannel<5>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.LoadBalancerClient - 192.168.0.113:21425
[main] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - 返回 Subchannel:Subchannel<3>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - Pick 下一个 Subchannel:Subchannel<3>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.LoadBalancerClient - 192.168.0.113:52259
[main] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - 返回 Subchannel:Subchannel<5>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.loadbalcner.CustomSubchannelPicker - Pick 下一个 Subchannel:Subchannel<5>: (grpc-server)
[main] INFO io.github.helloworlde.grpc.LoadBalancerClient - 192.168.0.113:21425
```