package io.github.helloworlde.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.reflection.v1alpha.ExtensionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ReflectionClient {

    public static void main(String[] args) throws InterruptedException {
        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 BlockingStub
        ServerReflectionGrpc.ServerReflectionStub reflectionStub = ServerReflectionGrpc.newStub(channel);

        StreamObserver<ServerReflectionResponse> streamObserver = new StreamObserver<ServerReflectionResponse>() {
            @Override
            public void onNext(ServerReflectionResponse response) {
                ServerReflectionResponse.MessageResponseCase messageResponseCase = response.getMessageResponseCase();
                log.info("Case: {}, Response: {}",messageResponseCase, response);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {
                log.info("Complete");
            }
        };

        StreamObserver<ServerReflectionRequest> requestStreamObserver = reflectionStub.serverReflectionInfo(streamObserver);

        // 列举所有的服务
        // 参考 reflection.proto:67#list_services
        ServerReflectionRequest listServiceRequest = listService();
        requestStreamObserver.onNext(listServiceRequest);

        // 根据名称获取文件
        // 参考 reflection.proto:44#file_by_filename
        ServerReflectionRequest getFileByName = getFileByName("helloworld.proto");
        requestStreamObserver.onNext(getFileByName);

        // 根据符号获取服务定义
        // 参考 reflection.proto:49#file_containing_symbol
        ServerReflectionRequest getFileContainingSymbol = getFileContainingSymbol("io.github.helloworlde.grpc.HelloService");
        requestStreamObserver.onNext(getFileContainingSymbol);


        // 根据扩展名称和序号查询
        // 参考 reflection.proto:53#file_containing_extension
        // 是 annotation 中的扩展属性, extend 修饰的
        ServerReflectionRequest fileContainingExtension = getServerReflectionRequest("google.protobuf.MethodOptions", 72295728);
        requestStreamObserver.onNext(fileContainingExtension);

        // 根据扩展名称查询
        // 参考 reflection.proto:63#all_extension_numbers_of_type
        ServerReflectionRequest allExtensionNumbersOfType = getAllExtensionNumbersOfType("google.protobuf.MethodOptions");
        requestStreamObserver.onNext(allExtensionNumbersOfType);

        requestStreamObserver.onCompleted();

        // 等待终止
        channel.awaitTermination(10, TimeUnit.SECONDS);
    }

    private static ServerReflectionRequest getAllExtensionNumbersOfType(String type) {
        return ServerReflectionRequest.newBuilder()
                                      .setAllExtensionNumbersOfType(type)
                                      .build();
    }

    private static ServerReflectionRequest getFileContainingSymbol(String symbol) {
        return ServerReflectionRequest.newBuilder()
                                      .setFileContainingSymbol(symbol)
                                      .build();
    }

    private static ServerReflectionRequest getServerReflectionRequest(String type, int number) {
        return ServerReflectionRequest.newBuilder()
                                      .setFileContainingExtension(ExtensionRequest.newBuilder()
                                                                                  .setContainingType(type)
                                                                                  .setExtensionNumber(number)
                                                                                  .build())
                                      .build();
    }

    private static ServerReflectionRequest getFileByName(String name) {
        return ServerReflectionRequest.newBuilder()
                                      .setFileByFilename(name)
                                      .build();
    }

    private static ServerReflectionRequest listService() {
        // 构建消息
        return ServerReflectionRequest.newBuilder()
                                      .setListServices("")
                                      .build();
    }
}
