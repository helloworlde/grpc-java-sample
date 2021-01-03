package io.github.helloworlde.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class CustomClientInterceptor implements ClientInterceptor {
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        log.info("执行客户端拦截器");
        return new CustomForwardingClientCall<>(next.newCall(method, callOptions));
    }
}

@Slf4j
class CustomForwardingClientCall<ReqT, RespT> extends ClientInterceptors.CheckedForwardingClientCall<ReqT, RespT> {

    protected CustomForwardingClientCall(ClientCall<ReqT, RespT> delegate) {
        super(delegate);
    }

    @Override
    protected void checkedStart(Listener<RespT> responseListener, Metadata headers) throws Exception {
        log.info("ClientCall checkedStart: {}", headers);
        CustomCallListener<RespT> listener = new CustomCallListener<>(responseListener);
        delegate().start(listener, headers);
    }

    @Override
    public void request(int numMessages) {
        log.info("ClientCall request: {}", numMessages);
        super.request(numMessages);
    }

    @Override
    public void sendMessage(ReqT message) {
        log.info("ClientCall sendMessage");
        super.sendMessage(message);
    }

    @Override
    public void halfClose() {
        log.info("ClientCall halfClose");
        super.halfClose();
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        log.info("ClientCall cancel, message: {}, cause: {}", message, cause);
        super.cancel(message, cause);
    }
}

@Slf4j
class CustomCallListener<RespT> extends ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT> {

    protected CustomCallListener(ClientCall.Listener<RespT> delegate) {
        super(delegate);
    }

    @Override
    public void onReady() {
        log.info("CallListener onReady");
        super.onReady();
    }

    @Override
    public void onClose(Status status, Metadata trailers) {
        log.info("CallListener onClose, status: {}, headers: {}", status, trailers);
        super.onClose(status, trailers);
    }

    @Override
    public void onHeaders(Metadata headers) {
        log.info("CallListener onHeaders: {}", headers);
        super.onHeaders(headers);
    }

    @Override
    public void onMessage(RespT message) {
        log.info("CallListener onMessage");
        super.onMessage(message);
    }

}