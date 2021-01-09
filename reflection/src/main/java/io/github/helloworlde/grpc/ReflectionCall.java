package io.github.helloworlde.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TypeRegistry;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ReflectionCall {

    public static void main(String[] args) throws InterruptedException {
        // 反射方法的格式只支持 package.service.method 或者 package.service
        String methodSymbol = "io.github.helloworlde.grpc.HelloService.SayHello";
        String requestContent = "{\"message\": \"Reflection\"}";

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();
        // 使用 Channel 构建 BlockingStub
        ServerReflectionGrpc.ServerReflectionStub reflectionStub = ServerReflectionGrpc.newStub(channel);
        // 响应观察器
        StreamObserver<ServerReflectionResponse> streamObserver = new StreamObserver<ServerReflectionResponse>() {
            @Override
            public void onNext(ServerReflectionResponse response) {
                try {
                    // 只需要关注文件描述类型的响应
                    if (response.getMessageResponseCase() == ServerReflectionResponse.MessageResponseCase.FILE_DESCRIPTOR_RESPONSE) {
                        List<ByteString> fileDescriptorProtoList = response.getFileDescriptorResponse().getFileDescriptorProtoList();
                        handleResponse(fileDescriptorProtoList, channel, methodSymbol, requestContent);
                    } else {
                        log.warn("未知响应类型: " + response.getMessageResponseCase());
                    }
                } catch (Exception e) {
                    log.error("处理响应失败: {}", e.getMessage(), e);
                }
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {
                log.info("Complete");
            }
        };
        // 请求观察器
        StreamObserver<ServerReflectionRequest> requestStreamObserver = reflectionStub.serverReflectionInfo(streamObserver);

        // 构建并发送获取方法文件描述请求
        ServerReflectionRequest getFileContainingSymbolRequest = ServerReflectionRequest.newBuilder()
                                                                                        .setFileContainingSymbol(methodSymbol)
                                                                                        .build();
        requestStreamObserver.onNext(getFileContainingSymbolRequest);
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    /**
     * 处理响应
     */
    private static void handleResponse(List<ByteString> fileDescriptorProtoList,
                                       ManagedChannel channel,
                                       String methodFullName,
                                       String requestContent) {
        try {
            // 解析方法和服务名称
            String fullServiceName = extraPrefix(methodFullName);
            String methodName = extraSuffix(methodFullName);
            String packageName = extraPrefix(fullServiceName);
            String serviceName = extraSuffix(fullServiceName);

            // 根据响应解析 FileDescriptor
            Descriptors.FileDescriptor fileDescriptor = getFileDescriptor(fileDescriptorProtoList, packageName, serviceName);

            // 查找服务描述
            Descriptors.ServiceDescriptor serviceDescriptor = fileDescriptor.getFile().findServiceByName(serviceName);
            // 查找方法描述
            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(methodName);

            // 发起请求
            executeCall(channel, fileDescriptor, methodDescriptor, requestContent);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 解析并查找方法对应的文件描述
     */
    private static Descriptors.FileDescriptor getFileDescriptor(List<ByteString> fileDescriptorProtoList,
                                                                String packageName,
                                                                String serviceName) throws Exception {

        Map<String, DescriptorProtos.FileDescriptorProto> fileDescriptorProtoMap =
                fileDescriptorProtoList.stream()
                                       .map(bs -> {
                                           try {
                                               return DescriptorProtos.FileDescriptorProto.parseFrom(bs);
                                           } catch (InvalidProtocolBufferException e) {
                                               e.printStackTrace();
                                           }
                                           return null;
                                       })
                                       .filter(Objects::nonNull)
                                       .collect(Collectors.toMap(DescriptorProtos.FileDescriptorProto::getName, f -> f));


        if (fileDescriptorProtoMap.isEmpty()) {
            log.error("服务不存在");
            throw new IllegalArgumentException("方法的文件描述不存在");
        }

        // 查找服务对应的 Proto 描述
        DescriptorProtos.FileDescriptorProto fileDescriptorProto = findServiceFileDescriptorProto(packageName, serviceName, fileDescriptorProtoMap);

        // 获取这个 Proto 的依赖
        Descriptors.FileDescriptor[] dependencies = getDependencies(fileDescriptorProto, fileDescriptorProtoMap);

        // 生成 Proto 的 FileDescriptor
        return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
    }


    /**
     * 根据包名和服务名查找相应的文件描述
     */
    private static DescriptorProtos.FileDescriptorProto findServiceFileDescriptorProto(String packageName,
                                                                                       String serviceName,
                                                                                       Map<String, DescriptorProtos.FileDescriptorProto> fileDescriptorProtoMap) {
        for (DescriptorProtos.FileDescriptorProto proto : fileDescriptorProtoMap.values()) {
            if (proto.getPackage().equals(packageName)) {
                boolean exist = proto.getServiceList()
                                     .stream()
                                     .anyMatch(s -> serviceName.equals(s.getName()));
                if (exist) {
                    return proto;
                }
            }
        }

        throw new IllegalArgumentException("服务不存在");
    }

    /**
     * 获取前缀
     */
    private static String extraPrefix(String content) {
        int index = content.lastIndexOf(".");
        return content.substring(0, index);
    }

    /**
     * 获取后缀
     */
    private static String extraSuffix(String content) {
        int index = content.lastIndexOf(".");
        return content.substring(index + 1);
    }

    /**
     * 获取依赖类型
     */
    private static Descriptors.FileDescriptor[] getDependencies(DescriptorProtos.FileDescriptorProto proto,
                                                                Map<String, DescriptorProtos.FileDescriptorProto> finalDescriptorProtoMap) {
        return proto.getDependencyList()
                    .stream()
                    .map(finalDescriptorProtoMap::get)
                    .map(f -> toFileDescriptor(f, getDependencies(f, finalDescriptorProtoMap)))
                    .toArray(Descriptors.FileDescriptor[]::new);
    }

    /**
     * 将 FileDescriptorProto 转为 FileDescriptor
     */
    @SneakyThrows
    private static Descriptors.FileDescriptor toFileDescriptor(DescriptorProtos.FileDescriptorProto fileDescriptorProto,
                                                               Descriptors.FileDescriptor[] dependencies) {
        return Descriptors.FileDescriptor.buildFrom(fileDescriptorProto, dependencies);
    }


    /**
     * 执行方法调用
     */
    private static void executeCall(ManagedChannel channel,
                                    Descriptors.FileDescriptor fileDescriptor,
                                    Descriptors.MethodDescriptor originMethodDescriptor,
                                    String requestContent) throws Exception {

        // 重新生成 MethodDescriptor
        MethodDescriptor<DynamicMessage, DynamicMessage> methodDescriptor = generateMethodDescriptor(originMethodDescriptor);

        CallOptions callOptions = CallOptions.DEFAULT;

        TypeRegistry registry = TypeRegistry.newBuilder()
                                            .add(fileDescriptor.getMessageTypes())
                                            .build();

        // 将请求内容由 JSON 字符串转为相应的类型
        JsonFormat.Parser parser = JsonFormat.parser().usingTypeRegistry(registry);
        DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(originMethodDescriptor.getInputType());
        parser.merge(requestContent, messageBuilder);
        DynamicMessage requestMessage = messageBuilder.build();

        // 调用，调用方式可以通过 originMethodDescriptor.isClientStreaming() 和 originMethodDescriptor.isServerStreaming() 推断
        DynamicMessage response = ClientCalls.blockingUnaryCall(channel, methodDescriptor, callOptions, requestMessage);

        // 将响应解析为 JSON 字符串
        JsonFormat.Printer printer = JsonFormat.printer()
                                               .usingTypeRegistry(registry)
                                               .includingDefaultValueFields();
        String responseContent = printer.print(response);

        log.info("响应: {}", responseContent);
    }

    /**
     * 重新生成方法描述
     */
    private static MethodDescriptor<DynamicMessage, DynamicMessage> generateMethodDescriptor(Descriptors.MethodDescriptor originMethodDescriptor) {
        // 生成方法全名
        String fullMethodName = MethodDescriptor.generateFullMethodName(originMethodDescriptor.getService().getFullName(), originMethodDescriptor.getName());
        // 请求和响应类型
        MethodDescriptor.Marshaller<DynamicMessage> inputTypeMarshaller = ProtoUtils.marshaller(DynamicMessage.newBuilder(originMethodDescriptor.getInputType())
                                                                                                              .buildPartial());
        MethodDescriptor.Marshaller<DynamicMessage> outputTypeMarshaller = ProtoUtils.marshaller(DynamicMessage.newBuilder(originMethodDescriptor.getOutputType())
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
}
