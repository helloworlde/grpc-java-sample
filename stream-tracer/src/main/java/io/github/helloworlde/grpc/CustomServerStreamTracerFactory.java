package io.github.helloworlde.grpc;

import io.grpc.Context;
import io.grpc.Metadata;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

public class CustomServerStreamTracerFactory extends ServerStreamTracer.Factory {
    @Override
    public ServerStreamTracer newServerStreamTracer(String fullMethodName, Metadata headers) {
        return new CustomServerStreamTracer(fullMethodName, headers);
    }
}

@Slf4j
class CustomServerStreamTracer extends ServerStreamTracer {

    private final String fullMethodName;

    private final Metadata headers;

    public CustomServerStreamTracer(String fullMethodName, Metadata headers) {
        this.fullMethodName = fullMethodName;
        this.headers = headers;
        log.info("method: {}, header: {}", this.fullMethodName, this.headers);
    }

    @Override
    public Context filterContext(Context context) {
        log.info("CustomServerStreamTracer filterContext, context: {}", context);
        return super.filterContext(context);
    }

    @Override
    public void serverCallStarted(ServerCallInfo<?, ?> callInfo) {
        log.info("CustomServerStreamTracer serverCallStarted, callInfo: {}", callInfo);
        super.serverCallStarted(callInfo);
    }

    @Override
    public void inboundMessage(int seqNo) {
        log.info("CustomServerStreamTracer inboundMessage, seqNo: {}", seqNo);
        super.inboundMessage(seqNo);
    }

    @Override
    public void inboundMessageRead(int seqNo, long optionalWireSize, long optionalUncompressedSize) {
        log.info("CustomServerStreamTracer inboundMessage, seqNo: {}, optionalWireSize: {} optionalUncompressedSize: {},", seqNo, optionalWireSize, optionalUncompressedSize);
        super.inboundMessageRead(seqNo, optionalWireSize, optionalUncompressedSize);
    }

    @Override
    public void inboundUncompressedSize(long bytes) {
        log.info("CustomServerStreamTracer inboundUncompressedSize, bytes: {}", bytes);
        super.inboundUncompressedSize(bytes);
    }

    @Override
    public void inboundWireSize(long bytes) {
        log.info("CustomServerStreamTracer inboundWireSize, bytes: {}", bytes);
        super.inboundWireSize(bytes);
    }

    @Override
    public void outboundMessage(int seqNo) {
        log.info("CustomServerStreamTracer outboundMessage, seqNo: {}", seqNo);
        super.outboundMessage(seqNo);
    }

    @Override
    public void outboundMessageSent(int seqNo, long optionalWireSize, long optionalUncompressedSize) {
        log.info("CustomServerStreamTracer outboundMessageSent, seqNo: {}, optionalWireSize: {} optionalUncompressedSize: {},", seqNo, optionalWireSize, optionalUncompressedSize);
        super.outboundMessageSent(seqNo, optionalWireSize, optionalUncompressedSize);
    }

    @Override
    public void outboundWireSize(long bytes) {
        log.info("CustomServerStreamTracer outboundWireSize, bytes: {}", bytes);
        super.outboundWireSize(bytes);
    }

    @Override
    public void outboundUncompressedSize(long bytes) {
        log.info("CustomServerStreamTracer outboundUncompressedSize, bytes: {}", bytes);
        super.outboundUncompressedSize(bytes);
    }

    @Override
    public void streamClosed(Status status) {
        log.info("CustomServerStreamTracer streamClosed, status: {}", status);
        super.streamClosed(status);
    }
}
