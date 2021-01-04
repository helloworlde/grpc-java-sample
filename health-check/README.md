# gRPC 使用健康检查

在 gRPC 中使用健康检查，在负载均衡前通过健康检查，只对健康的 Subchannel 发起请求，保证请求的成功率

## 使用

### Server 端

健康检查是一个独立的 Service，需要在 Server 端显式添加健康检查服务

健康检查定义了两个方法，一个适用于单次请求的 `check` 方法，另一个是适用于 Stream 流的 `watch` 方法

Server 端的健康检查由 `io.grpc.services.HealthStatusManager`控制，抽象类是 `io.grpc.health.v1.HealthGrpc.HealthImplBase`，具体实现是通过 `io.grpc.services.HealthServiceImpl`

- 在 Server 端添加健康检查服务

```java
HealthStatusManager healthStatusManager = new HealthStatusManager();

Server server = ServerBuilder.forPort(9090)
                             .addService(healthStatusManager.getHealthService())
                             .addService(new HelloServiceImpl())
                             .build();
```

这样，当 Server 端启动之后，就可以通过访问 `grpc.health.v1.Health`服务获取当前的 Server 端的状态


### 客户端

1. 添加配置

客户端开启健康检查有两个条件：
- 配置了健康检查参数，配置的名称是 `healthCheckConfig`，通过指定 `serviceName` 的方式配置
- 使用了支持健康检查的 LB (如 round_robin)

需要注意，这里的 `serviceName`可以是组件名称，或者服务名称；服务端默认为 `""`，  如果想检查某个组件，需要自己实现健康检查的逻辑；配置中的 `serviceName`只有在 NameResolver 解析到新的配置，且发生变化时才会更新，所以设置 `serviceName` 意义不大

```java
    public static void main(String[] args) throws InterruptedException {
        // 修改日志级别
        setLogLevel();

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
```

2. 执行健康检查

在发起请求前，会先使用 Service 的名称请求服务端健康检查服务，检查服务是否处于 `SERVING` 状态，如果状态正常，则发起请求，否则将会失败

- 调整日志级别

将`io.grpc.ChannelLogger`的日志级别调整到 `ALL`，用于观察日志

```java
    private static void setLogLevel() {
        Logger logger = Logger.getLogger("io.grpc.ChannelLogger");
        logger.setLevel(Level.ALL);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }
```

- 当健康检查成功时输出成功日志

```java
非常详细: [Subchannel<3>: (grpc-server)] CONNECTING: Starting health-check for ""
非常详细: [Subchannel<3>: (grpc-server)] READY: health-check responded SERVING
非常详细: [Channel<1>: (grpc-server)] Entering READY state with picker: ReadyPicker{list=[SubchannelImpl{delegate=Subchannel<3>: (grpc-server)}]}
```

- 当健康检查失败时输出错误日志

```java
非常详细: [Subchannel<3>: (grpc-server)] READY
非常详细: [Subchannel<3>: (grpc-server)] CONNECTING: Starting health-check for ""
非常详细: [Subchannel<3>: (grpc-server)] TRANSIENT_FAILURE: health-check responded NOT_SERVING
非常详细: [Channel<1>: (grpc-server)] Entering TRANSIENT_FAILURE state with picker: EmptyPicker{status=Status{code=UNAVAILABLE, description=Health-check service responded NOT_SERVING for '', cause=null}}
```

## 实现

### 定义

健康检查通过 `health.proto` 文件定义

- health.proto

```protobuf
syntax = "proto3";

package grpc.health.v1;

option csharp_namespace = "Grpc.Health.V1";
option go_package = "google.golang.org/grpc/health/grpc_health_v1";
option java_multiple_files = true;
option java_outer_classname = "HealthProto";
option java_package = "io.grpc.health.v1";

message HealthCheckRequest {
  string service = 1;
}

message HealthCheckResponse {
  enum ServingStatus {
    UNKNOWN = 0;
    SERVING = 1;
    NOT_SERVING = 2;
    SERVICE_UNKNOWN = 3;
  }
  ServingStatus status = 1;
}

service Health {
  // 单次健康检查
  rpc Check(HealthCheckRequest) returns (HealthCheckResponse);

  // 流式健康检查
  rpc Watch(HealthCheckRequest) returns (stream HealthCheckResponse);
}
```

### 客户端

#### 执行检查

##### 发起检查

1. 获取配置

在 `NameResolver` 解析后，调用 `io.grpc.internal.ManagedChannelImpl.NameResolverListener#onResult` 时检查是否有健康检查的配置，如果有则将配置添加到 `Attributes` 中

```java
// 获取属性
Attributes effectiveAttrs = resolutionResult.getAttributes();
// 如果服务发现没有关闭
if (NameResolverListener.this.helper == ManagedChannelImpl.this.lbHelper) {
  // 获取健康检查
  Map<String, ?> healthCheckingConfig = effectiveServiceConfig.getHealthCheckingConfig();
  // 构建健康检查配置
  if (healthCheckingConfig != null) {
    effectiveAttrs = effectiveAttrs.toBuilder()
                                   .set(LoadBalancer.ATTR_HEALTH_CHECKING_CONFIG, healthCheckingConfig)
                                   .build();
  }

  // 更新负载均衡算法，处理未处理的请求
  Status handleResult = helper.lb.tryHandleResolvedAddresses(
          ResolvedAddresses.newBuilder()
                           .setAddresses(servers)
                           .setAttributes(effectiveAttrs)
                           .setLoadBalancingPolicyConfig(effectiveServiceConfig.getLoadBalancingConfig())
                           .build());
}
```

2. 为 `Subchannel` 配置健康检查

通过代理调用 `io.grpc.util.RoundRobinLoadBalancer#handleResolvedAddresses`方法，然后调用 `io.grpc.services.HealthCheckingLoadBalancerFactory.HelperImpl#createSubchannel` 方法创建  `Subchannel`；创建用于健康检查的 `SubchannelStateListener`的实例 `HealthCheckState`

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HelperImpl#createSubchannel`

```java
HealthCheckState hcState = new HealthCheckState(this, originalSubchannel, syncContext, delegate.getScheduledExecutorService());
```

3. 添加健康检查

如果有设置健康检查，则将健康检查添加到 `Subchannel`健康检查集合中；然后调用 `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState#setServiceName` 方法执行

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState#setServiceName`

如果此时有已经提交的请求，则取消，并发送健康检查请求；当第一次执行的时候，如果状态是 `IDLE`s，则会跳出不执行，直到状态变为`READY`时执行

```java
void setServiceName(@Nullable String newServiceName) {
    serviceName = newServiceName;

    // 如果在 RPC 请求期间服务名称更改，请取消该服务，以便用新名称进行新的调用
    String cancelMsg = serviceName == null ? "Health check disabled by service config"
            : "Switching to new service name: " + newServiceName;

    // 停止调用
    stopRpc(cancelMsg);
    // 调整健康检查
    adjustHealthCheck();
}
```

- `io.grpc.internal.InternalSubchannel.TransportListener#transportReady`

当 `Transport` 状态是`READY` 的时候，开始健康检查

```java
    public void transportReady() {
      syncContext.execute(new Runnable() {
        @Override
        public void run() {
          reconnectPolicy = null;
          if (shutdownReason != null) {
            Preconditions.checkState(activeTransport == null, "Unexpected non-null activeTransport");
            transport.shutdown(shutdownReason);
          } else if (pendingTransport == transport) {
            activeTransport = transport;
            pendingTransport = null;
            gotoNonErrorState(READY);
          }
        }
      });
    }
```

- `io.grpc.internal.InternalSubchannel#gotoState`
  将状态变为 `READY` 状态，

```java
  private void gotoState(final ConnectivityStateInfo newState) {
    if (state.getState() != newState.getState()) {
      Preconditions.checkState(state.getState() != SHUTDOWN,
          "Cannot transition out of SHUTDOWN to " + newState);
      state = newState;
      callback.onStateChange(InternalSubchannel.this, newState);
    }
  }
```

- `ManagedInternalSubchannelCallback#onStateChange`

```java
void onStateChange(InternalSubchannel is, ConnectivityStateInfo newState) {
  // 调用服务发现，重新解析
  handleInternalSubchannelState(newState);
  checkState(listener != null, "listener is null");
  listener.onSubchannelState(newState);
}
```

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState#onSubchannelState`

当 `Subchannel` 状态发生变化时执行健康检查

```java
public void onSubchannelState(ConnectivityStateInfo rawState) {
    // 如果当前的状态是 READY，且新的状态不是 READY，则更新 disabled 为 false
    if (Objects.equal(this.rawState.getState(), READY)
            && !Objects.equal(rawState.getState(), READY)) {
        // 断开连接，将重置已禁用标志，因为健康检查在新连接上可能可用
        disabled = false;
    }

    // 如果是 SHUTDOWN，则移除
    if (Objects.equal(rawState.getState(), SHUTDOWN)) {
        helperImpl.hcStates.remove(this);
    }
    this.rawState = rawState;
    // 调整健康检查状态
    adjustHealthCheck();
}
```

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState#adjustHealthCheck`

当没有禁止，且服务名不为空，且连接状态是 READY，则发送健康检查的请求

```java
private void adjustHealthCheck() {
    // 如果没有禁止，且服务名不为空，且连接状态是 READY
    if (!disabled && serviceName != null && Objects.equal(rawState.getState(), READY)) {
        running = true;
        // 如果没有活跃的 RPC，且重试计时器没有等待，则开始 RPC
        if (activeRpc == null && !isRetryTimerPending()) {
            // 执行健康检查，并根据结果发送请求
            startRpc();
        }
    } else {
        running = false;
        stopRpc("Client stops health check");
        backoffPolicy = null;
        gotoState(rawState);
    }
}
```

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState#startRpc`

在开始健康检查之前，将连接状态由 `READY` 改为 `CONNECTING`；
创建新的 `ClientCall.Listener`实例 `HcStream`，并调用 `start` 方法，发起请求

```java
private void startRpc() {
    if (!Objects.equal(concludedState.getState(), READY)) {
        // 修改连接状态
        gotoState(ConnectivityStateInfo.forNonError(CONNECTING));
    }
    // 创建新的 ClientCall.Listener
    activeRpc = new HcStream();
    // 开始调用，发出请求
    activeRpc.start();
}
```

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState.HcStream#HcStream`

在  `HcStream` 构造方法中，创建新的 Stream 请求

```java
HcStream() {
    stopwatch = stopwatchSupplier.get().start();
    callServiceName = serviceName;
    // 开始新的调用
    call = subchannel.asChannel().newCall(HealthGrpc.getWatchMethod(), CallOptions.DEFAULT);
}
```

- `io.grpc.internal.SubchannelChannel#newCall`

发起一个 `SERVER_STREAMING` 请求

```java
public <RequestT, ResponseT> ClientCall<RequestT, ResponseT> newCall(MethodDescriptor<RequestT, ResponseT> methodDescriptor, CallOptions callOptions) {
    final Executor effectiveExecutor = callOptions.getExecutor() == null ? executor : callOptions.getExecutor();
        
    return new ClientCallImpl<>(methodDescriptor,
        effectiveExecutor,
        callOptions.withOption(GrpcUtil.CALL_OPTIONS_RPC_OWNED_BY_BALANCER, Boolean.TRUE),
        transportProvider, deadlineCancellationExecutor, callsTracer, false /* retryEnabled */);
  }
```

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState.HcStream#start`

开始调用，使用服务名作为健康检查的参数，向服务端发起健康检查请求
此时服务端接收到健康检查请求，根据请求的参数进行检查，然后返回结果

```java
void start() {
    // 开始调用
    call.start(this, new Metadata());
    // 发送服务健康检查消息
    call.sendMessage(HealthCheckRequest.newBuilder().setService(serviceName).build());
    call.halfClose();
    call.request(1);
}
```

##### 处理结果

- `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState.HcStream#onMessage`

监听响应结果，如果是当前 `Subchannel`的请求响应，则进行处理

```java
public void onMessage(final HealthCheckResponse response) {
    syncContext.execute(new Runnable() {
        @Override
        public void run() {
            // 如果是当前的请求，则进行处理
            if (activeRpc == HcStream.this) {
                // 根据响应更新连接状态
                handleResponse(response);
            }
        }
    });
}
```

- 根据响应结果处理连接状态

在 `io.grpc.services.HealthCheckingLoadBalancerFactory.HealthCheckState.HcStream#handleResponse` 方法中处理响应结果；如果是 `SERVING`状态，则将连接状态改为 `READY`，否则将状态改为 `UNAVAILABLE`

```java
void handleResponse(HealthCheckResponse response) {
    callHasResponded = true;
    backoffPolicy = null;

    // 获取返回的状态
    ServingStatus status = response.getStatus();

    // 如果是服务中，则更新连接状态为 READY
    if (Objects.equal(status, ServingStatus.SERVING)) {
        gotoState(ConnectivityStateInfo.forNonError(READY));
    } else {
        // 更新连接状态为 UNAVAILABLE
        gotoState(ConnectivityStateInfo.forTransientFailure(Status.UNAVAILABLE.withDescription("Health-check service responded " + status + " for '" + callServiceName + "'")));
    }
    call.request(1);
}
```

也会将 LB 状态也改为 `READY`，此时 Picker 变为 `ReadyPicker`，至此，完成健康检查

### Server 端

#### 设置服务状态

#####  默认服务

`io.grpc.services.HealthServiceImpl#HealthServiceImpl` 内有一个 Map，用于存放各个服务的状态；默认含有一个 key 为 `""`, value 为 `SERVING`的键值对，当请求参数中没有 seviceName 时直接返回 `SERVING` 状态

##### 其他服务

其他服务需要 Server 主动设置状态，具体的逻辑由自己实现，当服务状态发生变化时，通过调用 `io.grpc.services.HealthStatusManager#setStatus` 进行设置

- `io.grpc.services.HealthStatusManager#setStatus`

```java
public void setStatus(String service, ServingStatus status) {
    checkNotNull(status, "status");
    healthService.setStatus(service, status);
}
```

- `io.grpc.services.HealthServiceImpl#setStatus`

```java 
void setStatus(String service, ServingStatus status) {
    synchronized (watchLock) {
        if (terminal) {
            return;
        }
        setStatusInternal(service, status);
    }
}
```

- `io.grpc.services.HealthServiceImpl#setStatusInternal`

为 service 设置状态，当状态发生变化时，通过 Stream 发送响应给客户端，通知状态变化

```java
private void setStatusInternal(String service, ServingStatus status) {
    // 设置新的状态
    ServingStatus prevStatus = statusMap.put(service, status);
    // 如果状态不一样，则通知状态变化
    if (prevStatus != status) {
        notifyWatchers(service, status);
    }
}
```

- `io.grpc.services.HealthServiceImpl#notifyWatchers`

如果有客户端 Stream，则将状态变化通知给所有的监听该服务的客户端

```java
private void notifyWatchers(String service, @Nullable ServingStatus status) {
    // 构建结果
    HealthCheckResponse response = getResponseForWatch(status);

    IdentityHashMap<StreamObserver<HealthCheckResponse>, Boolean> serviceWatchers = watchers.get(service);

    // 如果有监听，则遍历所有的监听，发送结果
    if (serviceWatchers != null) {
        for (StreamObserver<HealthCheckResponse> responseObserver : serviceWatchers.keySet()) {
            responseObserver.onNext(response);
        }
    }
}
```

#### 处理健康检查

##### 单次请求

单次健康检查请求通过 `io.grpc.services.HealthServiceImpl#check` 处理，会根据当前的状态返回

- `io.grpc.services.HealthServiceImpl#check`

```java
public void check(HealthCheckRequest request,
                  StreamObserver<HealthCheckResponse> responseObserver) {
    // 根据请求中的服务名获取状态
    ServingStatus status = statusMap.get(request.getService());
    // 如果状态是 null，则返回 NOT_FOUND 错误
    if (status == null) {
        responseObserver.onError(new StatusException(Status.NOT_FOUND.withDescription("unknown service " + request.getService())));
    } else {
        // 根据状态构造响应
        HealthCheckResponse response = HealthCheckResponse.newBuilder().setStatus(status).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

##### Stream 请求

对于 Stream 请求，是通过 `io.grpc.services.HealthServiceImpl#watch` 处理
当接收到请求后，会从 Map 中获取服务状态，然后生成响应返回给客户端；
然后将该 `StreamObserver` 保存到  Service 对应的 Map 中，当 Service 状态发生变化时，通知相应的 Client
同时添加了监听器，当客户端关闭时，从 Map 中移除该 `StreamObserver`

- `io.grpc.services.HealthServiceImpl#watch`

```java
public void watch(HealthCheckRequest request,
                  final StreamObserver<HealthCheckResponse> responseObserver) {
    final String service = request.getService();

    // 加锁
    synchronized (watchLock) {
        // 根据服务获取状态，构建结果，并发送出去
        ServingStatus status = statusMap.get(service);
        responseObserver.onNext(getResponseForWatch(status));

        // 从 watcher 中获取服务名的 map，如果不存在，则创建一个
        IdentityHashMap<StreamObserver<HealthCheckResponse>, Boolean> serviceWatchers = watchers.get(service);

        if (serviceWatchers == null) {
            serviceWatchers = new IdentityHashMap<>();
            watchers.put(service, serviceWatchers);
        }

        // 如果存在，则将 responseObserver 添加到 map 中
        serviceWatchers.put(responseObserver, Boolean.TRUE);
    }

    Context.current().addListener(
            new CancellationListener() {
                @Override
                // Called when the client has closed the stream
                public void cancelled(Context context) {
                    synchronized (watchLock) {
                        // 当客户端关闭时，从 map 中移除方法对应的数据
                        IdentityHashMap<StreamObserver<HealthCheckResponse>, Boolean> serviceWatchers = watchers.get(service);
                        if (serviceWatchers != null) {
                            serviceWatchers.remove(responseObserver);
                            if (serviceWatchers.isEmpty()) {
                                watchers.remove(service);
                            }
                        }
                    }
                }
            },
            MoreExecutors.directExecutor());
}
```