package io.github.helloworlde.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Grpc;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLSession;

@Slf4j
public class TlsServerInterceptor implements ServerInterceptor {
    // The application uses this in its handlers
    public final static Context.Key<SSLSession> SSL_SESSION_CONTEXT = Context.key("SSLSession");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                                                                 Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        SSLSession sslSession = call.getAttributes().get(Grpc.TRANSPORT_ATTR_SSL_SESSION);
        if (sslSession == null) {
            return next.startCall(call, headers);
        }
        return Contexts.interceptCall(Context.current().withValue(SSL_SESSION_CONTEXT, sslSession),
                call,
                headers,
                next);
    }

}
