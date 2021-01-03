# gRPC 拦截器和监听器

gRPC 拦截器用于在请求执行之前执行，以实现校验授权，记录调用行为，插入其他逻辑等；拦截器有 `ClientInterceptor` 和 `ServerInterceptor`，分别用于客户端和服务端

## 客户端

### 拦截器接口定义

- ClientInterceptor

```java
@ThreadSafe
public interface ClientInterceptor {
    <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method,
                                                        CallOptions callOptions,
                                                        Channel next);
}
```

### 使用

#### 添加拦截器

- 在构建 Channel 时添加拦截器

```java
this.channel = ManagedChannelBuilder
        .forAddress("127.0.0.1", 9090) 
        .intercept(new CustomClientInterceptor())
        .build();
```

- io.grpc.internal.ManagedChannelImpl#ManagedChannelImpl

然后会在 `ManagedChannelImpl` 的构造方法中，使用拦截器将 Channel 实例封装，返回的 Channel 实例是 `InterceptorChannel` 的实例

```java
this.interceptorChannel = ClientInterceptors.intercept(channel, interceptors);
```

- io.grpc.ClientInterceptors#intercept

当有多个拦截器时，会顺序的封装，最后添加的拦截器会最先执行

```java
public static Channel intercept(Channel channel, List<? extends ClientInterceptor> interceptors) {
    Preconditions.checkNotNull(channel, "channel");
    // 遍历拦截器，创建 InterceptorChannel
    for (ClientInterceptor interceptor : interceptors) {
        channel = new InterceptorChannel(channel, interceptor);
    }
    return channel;
}
```

#### 处理请求

- io.grpc.ClientInterceptors.InterceptorChannel#newCall

`InterceptorChannel` 继承了 `Channel`，在执行请求时，会调用`channel.newCall`，在这个方法里，会调用拦截器的方法

```java
@Override
public <ReqT, RespT> ClientCall<ReqT, RespT> newCall(MethodDescriptor<ReqT, RespT> method,
                                                     CallOptions callOptions) {
  return interceptor.interceptCall(method, callOptions, channel);
}
```

然后返回自定义的 `CustomForwardingClientCall`，在这个类的`checkedStart`方法中，还创建了 `CustomCallListener`, 这样在调用时，就可以实现 `ClientCall` 和 `ClientCallListener` 的事件监听，从而实现自定义的逻辑

#### 自定义拦截器

客户端拦截器通常和 `CheckedForwardingClientCall`，`SimpleForwardingClientCallListener` 一起使用，以实现监听调用整个生命周期

- CustomClientInterceptor.java

```java
@Slf4j
public class CustomClientInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new CustomForwardingClientCall<>(next.newCall(method, callOptions));
    }
}

@Slf4j
class CustomCallListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

    protected CustomCallListener(ClientCall.Listener<RespT> delegate) {
        super(delegate);
    }
}

@Slf4j
class CustomForwardingClientCall<ReqT, RespT> extends ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT> {

    protected CustomForwardingClientCall(ClientCall<ReqT, RespT> delegate) {
        super(delegate);
    }

    @Override
    protected void checkedStart(Listener<RespT> responseListener, Metadata headers) throws Exception {
        CustomCallListener<RespT> listener = new CustomCallListener<>(responseListener);
        delegate().start(listener, headers);
    }    
}
```

## 服务端

服务端的拦截器与客户端拦截器不同，服务端拦截器是方法定义的属性，在每个请求中都会重新创建新的实例；而客户端的拦截器是 Channel 的属性，只创建一个实例

### 拦截器接口定义

- ServerInterceptor

```java
@ThreadSafe
public interface ServerInterceptor {
    <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                          Metadata headers,
                                                          ServerCallHandler<ReqT, RespT> next);
}
```

### 使用

#### 添加拦截器

在构建 Server 时添加拦截器

```java
Server server = ServerBuilder.forPort(9090) 
                             .intercept(new CustomServerInterceptor())
                             .build();
```

#### 封装拦截器

- io.grpc.internal.ServerImpl.ServerTransportListenerImpl#startCall

当 Server 端监听到流创建事件后，会提交一个 `StreamCreated` 任务，在执行任务时，会根据拦截器封装相应的 `ServerCallHandler` 实例

```java
for (ServerInterceptor interceptor : interceptors) {
    handler = InternalServerInterceptors.interceptCallHandler(interceptor, handler);
}
```

- io.grpc.InternalServerInterceptors#interceptCallHandler

调用相应方法创建拦截器封装的 `ServerCallHandler`实例

```java
public static <ReqT, RespT> ServerCallHandler<ReqT, RespT> interceptCallHandler(ServerInterceptor interceptor,
                                                                                ServerCallHandler<ReqT, RespT> callHandler) {
    return ServerInterceptors.InterceptCallHandler.create(interceptor, callHandler);
}
```

- io.grpc.ServerInterceptors.InterceptCallHandler#create

创建 `InterceptCallHandler` 实例，这个类实现了 `ServerCallHandler`接口，在有请求调用时会调用其 `startCall`方法，然后调用拦截器的方法实现逻辑

```java
public static <ReqT, RespT> InterceptCallHandler<ReqT, RespT> create(ServerInterceptor interceptor,
                                                                     ServerCallHandler<ReqT, RespT> callHandler) {
    return new InterceptCallHandler<>(interceptor, callHandler);
}
```

#### 处理调用

在使用拦截器封装完成之后，会将封装后的处理器添加到方法定义 `ServerMethodDefinition`中

- io.grpc.internal.ServerImpl.ServerTransportListenerImpl#startWrappedCall

```java
ServerCall.Listener<WReqT> listener = methodDef.getServerCallHandler().startCall(call, headers);
```

- io.grpc.ServerInterceptors.InterceptCallHandler#startCall

在执行 startCall 时，会调用拦截器的方法，并返回 `ServerCall.Listener`实例

```java
@Override
public ServerCall.Listener<ReqT> startCall(ServerCall<ReqT, RespT> call,
                                           Metadata headers) {
    return interceptor.interceptCall(call, headers, callHandler);
}
```

然后会执行自定义的拦截器的逻辑，创建相应的 `CustomServerCall` 和 `CustomServerCallListener`，这样就可以监听 Server 端调用的事件，实现自定义的逻辑

#### 自定义拦截器

和 Client 拦截器一样，Server 端拦截器通常和 `SimpleForwardingServerCall`，`SimpleForwardingServerCallListener` 一起使用，以实现监听调用整个生命周期

```java
@Slf4j
public class CustomServerInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata, ServerCallHandler<ReqT, RespT> serverCallHandler) {
        CustomServerCall<ReqT, RespT> customServerCall = new CustomServerCall<>(call);
        ServerCall.Listener<ReqT> listener = next.startCall(customServerCall, headers);
        return new CustomServerCallListener<>(listener);
    }
}

@Slf4j
class CustomServerCallListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

    protected CustomServerCallListener(ServerCall.Listener<ReqT> delegate) {
        super(delegate);
    }
}


@Slf4j
class CustomServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {

    protected CustomServerCall(ServerCall<ReqT, RespT> delegate) {
        super(delegate);
    }
}
```
