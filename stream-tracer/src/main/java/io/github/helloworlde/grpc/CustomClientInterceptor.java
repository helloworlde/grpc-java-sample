package io.github.helloworlde.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.MethodDescriptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomClientInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        log.info("执行客户端拦截器");
        callOptions = callOptions.withStreamTracerFactory(new CustomClientStreamTracerFactory<>(method, callOptions, next));
        return next.newCall(method, callOptions);
    }
}