# gRPC 中泛化调用服务接口

gRPC 没有直接支持泛化调用，protobuf 可以不依赖于生成的代码实现调用，所以可以通过反射接口间接实现泛化调用

要求 Server 端提供 `grpc.reflection.v1alpha.ServerReflection` 服务，用于获取服务的描述文件

大致的流程是：

1. 根据方法名称，调用服务端反射服务的方法，获取方法所在 proto 文件的描述
2. 根据 proto 描述文件，获取文件描述、服务描述，用于重新构建要被调用方法的方法描述 `MethodDescriptor`
3. 根据方法描述，将请求内容序列化为对应的类型
4. 使用重新构建的`MethodDescriptor`和其他参数对 Server 端相应的方法发起调用
5. 解析响应并返回

## 实现

使用 JSON 格式请求被调用的服务方法，并返回 JSON 格式的响应

### proto 定义

```protobuf
syntax = "proto3";

package io.github.helloworlde.grpc;

option go_package = "api;grpc_gateway";
option java_package = "io.github.helloworlde.grpc";
option java_multiple_files = true;
option java_outer_classname = "HelloWorldGrpc";

service HelloService{
  rpc SayHello(HelloMessage) returns (HelloResponse){
  }
}

message HelloMessage {
  string message = 2;
}

message HelloResponse {
  string message = 1;
}
```

### 调用

#### 1. 构建反射服务 Stub

需要调用反射服务的方法，该方法是双向流

```java
// 构建 Channel
ManagedChannel channel=ManagedChannelBuilder.forAddress("127.0.0.1",9090)
        .usePlaintext()
        .build();
        // 使用 Channel 构建 BlockingStub
        ServerReflectionGrpc.ServerReflectionStub reflectionStub=ServerReflectionGrpc.newStub(channel);
        // 响应观察器
        StreamObserver<ServerReflectionResponse> streamObserver=new StreamObserver<ServerReflectionResponse>(){
@Override
public void onNext(ServerReflectionResponse response){
        // 处理响应
        }

@Override
public void onError(Throwable t){

        }

@Override
public void onCompleted(){
        log.info("Complete");
        }
        };
        // 请求观察器
        StreamObserver<ServerReflectionRequest> requestStreamObserver=reflectionStub.serverReflectionInfo(streamObserver);
```

#### 2. 根据方法名称获取文件描述

这里的 `methodSymbol` 即服务或方法的限定名，可以是 `package.service` 或者 `package.service.method`
，如 `io.github.helloworlde.grpc.HelloService.SayHello`，需要注意方法前是 `.`不是`/`

```java
// 构建并发送获取方法文件描述请求
ServerReflectionRequest getFileContainingSymbolRequest=ServerReflectionRequest.newBuilder()
        .setFileContainingSymbol(methodSymbol)
        .build();
        requestStreamObserver.onNext(getFileContainingSymbolRequest);
```

#### 3. 处理响应，解析 FileDescriptor

返回的响应后会触发 `onNext` 方法，如果响应类型是文件描述类型，即 `FILE_DESCRIPTOR_RESPONSE`，则进行处理

```java
public void onNext(ServerReflectionResponse response){
        try{
        // 只需要关注文件描述类型的响应
        if(response.getMessageResponseCase()==ServerReflectionResponse.MessageResponseCase.FILE_DESCRIPTOR_RESPONSE){
        List<ByteString> fileDescriptorProtoList=response.getFileDescriptorResponse().getFileDescriptorProtoList();
        handleResponse(fileDescriptorProtoList,channel,methodSymbol,requestContent);
        }else{
        log.warn("未知响应类型: "+response.getMessageResponseCase());
        }
        }catch(Exception e){
        log.error("处理响应失败: {}",e.getMessage(),e);
        }
        }
```

- handleResponse

在处理请求时，先解析了包名、服务名和方法名，然后根据包名和服务名，从返回的文件描述中获取到了响应方法所在文件的描述；然后从文件描述中获取服务描述，最终获取到方法描述，根据方法描述执行调用

```java
private static void handleResponse(List<ByteString> fileDescriptorProtoList,
        ManagedChannel channel,
        String methodFullName,
        String requestContent){
        try{
        // 解析方法和服务名称
        String fullServiceName=extraPrefix(methodFullName);
        String methodName=extraSuffix(methodFullName);
        String packageName=extraPrefix(fullServiceName);
        String serviceName=extraSuffix(fullServiceName);

        // 根据响应解析 FileDescriptor
        Descriptors.FileDescriptor fileDescriptor=getFileDescriptor(fileDescriptorProtoList,packageName,serviceName);

        // 查找服务描述
        Descriptors.ServiceDescriptor serviceDescriptor=fileDescriptor.getFile().findServiceByName(serviceName);
        // 查找方法描述
        Descriptors.MethodDescriptor methodDescriptor=serviceDescriptor.findMethodByName(methodName);

        // 发起请求
        executeCall(channel,fileDescriptor,methodDescriptor,requestContent);
        }catch(Exception e){
        log.error(e.getMessage(),e);
        }
        }
```

- getFileDescriptor

根据响应找到方法对应的文件的 `FileDescriptorProto`，然后构建出对应的 `FileDescriptor`

```java
private static Descriptors.FileDescriptor getFileDescriptor(List<ByteString> fileDescriptorProtoList,
        String packageName,
        String serviceName)throws Exception{

        Map<String, DescriptorProtos.FileDescriptorProto>fileDescriptorProtoMap=
        fileDescriptorProtoList.stream()
        .map(bs->{
        try{
        return DescriptorProtos.FileDescriptorProto.parseFrom(bs);
        }catch(InvalidProtocolBufferException e){
        e.printStackTrace();
        }
        return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toMap(DescriptorProtos.FileDescriptorProto::getName,f->f));


        if(fileDescriptorProtoMap.isEmpty()){
        log.error("服务不存在");
        throw new IllegalArgumentException("方法的文件描述不存在");
        }

        // 查找服务对应的 Proto 描述
        DescriptorProtos.FileDescriptorProto fileDescriptorProto=findServiceFileDescriptorProto(packageName,serviceName,fileDescriptorProtoMap);

        // 获取这个 Proto 的依赖
        Descriptors.FileDescriptor[]dependencies=getDependencies(fileDescriptorProto,fileDescriptorProtoMap);

        // 生成 Proto 的 FileDescriptor
        return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto,dependencies);
        }
```

#### 4. 执行调用

- 生成方法描述

在执行调用时，需要重新生成 `MethodDescriptor`；因为获取到的 `MethodDescriptor` 中的方法全名是`package.service.method`
格式，而需要的是`package.service/method`格式，同时请求和响应类型也需要重新设置为 `DynamicMessage`,所以需要重新生成 `MethodDescriptor`

```java
private static MethodDescriptor<DynamicMessage, DynamicMessage> generateMethodDescriptor(Descriptors.MethodDescriptor originMethodDescriptor){
        // 生成方法全名
        String fullMethodName=MethodDescriptor.generateFullMethodName(originMethodDescriptor.getService().getFullName(),originMethodDescriptor.getName());
        // 请求和响应类型
        MethodDescriptor.Marshaller<DynamicMessage> inputTypeMarshaller=ProtoUtils.marshaller(DynamicMessage.newBuilder(originMethodDescriptor.getInputType())
        .buildPartial());
        MethodDescriptor.Marshaller<DynamicMessage> outputTypeMarshaller=ProtoUtils.marshaller(DynamicMessage.newBuilder(originMethodDescriptor.getOutputType())
        .buildPartial());

        // 生成方法描述, originMethodDescriptor 的 fullMethodName 不正确
        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
        .setFullMethodName(fullMethodName)
        .setRequestMarshaller(inputTypeMarshaller)
        .setResponseMarshaller(outputTypeMarshaller)
        // 使用 UNKNOWN，自动修改
        .setType(MethodDescriptor.MethodType.UNKNOWN)
        .build();
        }
```

- 执行调用

同时需要根据文件描述，将请求的类型转为对应的请求类型，生成 `DynamicMessage` 对象；然后根据方法类型，使用`MethodDescriptor` 和 `CallOptions`
发起请求；当接收到响应后将 `DynamicMessage` 解析为对应的格式的字符串；完成调用

```java
private static void executeCall(ManagedChannel channel,
        Descriptors.FileDescriptor fileDescriptor,
        Descriptors.MethodDescriptor originMethodDescriptor,
        String requestContent)throws Exception{

        // 重新生成 MethodDescriptor
        MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor=generateMethodDescriptor(originMethodDescriptor);

        CallOptions callOptions=CallOptions.DEFAULT;

        TypeRegistry registry=TypeRegistry.newBuilder()
        .add(fileDescriptor.getMessageTypes())
        .build();

        // 将请求内容由 JSON 字符串转为相应的类型
        JsonFormat.Parser parser=JsonFormat.parser().usingTypeRegistry(registry);
        DynamicMessage.Builder messageBuilder=DynamicMessage.newBuilder(originMethodDescriptor.getInputType());
        parser.merge(requestContent,messageBuilder);
        DynamicMessage requestMessage=messageBuilder.build();

        // 调用，调用方式可以通过 originMethodDescriptor.isClientStreaming() 和 originMethodDescriptor.isServerStreaming() 推断
        DynamicMessage response=ClientCalls.blockingUnaryCall(channel,methodDescriptor,callOptions,requestMessage);

        // 将响应解析为 JSON 字符串
        JsonFormat.Printer printer=JsonFormat.printer()
        .usingTypeRegistry(registry)
        .includingDefaultValueFields();
        String responseContent=printer.print(response);

        log.info("响应: {}",responseContent);
        }
```

---

## 参考文档 

- [相关实现代码参考 ReflectionCall.java](https://github.com/helloworlde/grpc-java-sample/blob/main/reflection/src/main/java/io/github/helloworlde/grpc/ReflectionCall.java)
- [protobuf-dynamic](https://github.com/os72/protobuf-dynamic)
- [grpcurl](https://github.com/fullstorydev/grpcurl)  
- [grpc-swagger](https://github.com/grpc-swagger/grpc-swagger)  
- [gRPC + JSON](https://grpc.io/blog/grpc-with-json/)
- [gRPC Server Reflection Tutorial](https://github.com/grpc/grpc-java/blob/master/documentation/server-reflection-tutorial.md#enable-server-reflection)
- [Reflection](https://github.com/grpc/grpc-go/tree/master/reflection)
- [gRPC Server Reflection Tutorial](https://chromium.googlesource.com/external/github.com/grpc/grpc-go/+/HEAD/Documentation/server-reflection-tutorial.md)
- [Protocol buffer objects generated at runtime](https://stackoverflow.com/questions/18836727/protocol-buffer-objects-generated-at-runtime)
- [How can I send a gRPC message whose format is determined at runtime](https://stackoverflow.com/questions/52368593/how-can-i-send-a-grpc-message-whose-format-is-determined-at-runtime)
- [How to create GRPC client directly from protobuf without compiling it into java code](https://stackoverflow.com/questions/61133529/how-to-create-grpc-client-directly-from-protobuf-without-compiling-it-into-java/61144510#61144510)  

