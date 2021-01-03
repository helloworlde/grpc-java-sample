package io.github.helloworlde.grpc;

import io.grpc.ForwardingServerCall;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CustomServerInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        log.info("执行服务端拦截器");
        CustomServerCall<ReqT, RespT> customServerCall = new CustomServerCall<>(call);
        ServerCall.Listener<ReqT> listener = next.startCall(customServerCall, headers);
        return new CustomServerCallListener<>(listener);
    }
}

@Slf4j
class CustomServerCall<ReqT, RespT> extends ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT> {

    protected CustomServerCall(ServerCall<ReqT, RespT> delegate) {
        super(delegate);
    }

    @Override
    public void request(int numMessages) {
        log.info("ServerCall request: {}", numMessages);
        super.request(numMessages);
    }

    @Override
    public void close(Status status, Metadata trailers) {
        log.info("ServerCall close with Status: {}", status);
        super.close(status, trailers);
    }

    @Override
    public void sendMessage(RespT message) {
        log.info("ServerCall sendMessage");
        super.sendMessage(message);
    }

    @Override
    public void sendHeaders(Metadata headers) {
        log.info("ServerCall sendHeader: {}", headers);
        super.sendHeaders(headers);
    }
}

@Slf4j
class CustomServerCallListener<ReqT> extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

    protected CustomServerCallListener(ServerCall.Listener<ReqT> delegate) {
        super(delegate);
    }

    @Override
    public void onHalfClose() {
        log.info("CallListener onHalfClose");
        delegate().onHalfClose();
    }

    @Override
    public void onCancel() {
        log.info("CallListener onCancel");
        super.onCancel();
    }

    @Override
    public void onComplete() {
        log.info("CallListener onComplete");
        super.onComplete();
    }

    @Override
    public void onMessage(ReqT message) {
        log.info("CallListener onMessage");
        super.onMessage(message);
    }

    @Override
    public void onReady() {
        log.info("CallListener onReady");
        super.onReady();
    }
}