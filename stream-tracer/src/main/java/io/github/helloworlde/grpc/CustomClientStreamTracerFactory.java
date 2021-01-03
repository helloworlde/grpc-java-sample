package io.github.helloworlde.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientStreamTracer;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

public class CustomClientStreamTracerFactory<ReqT, RespT> extends ClientStreamTracer.Factory {

    private final MethodDescriptor<ReqT, RespT> method;
    private final CallOptions callOptions;
    private final Channel next;

    public CustomClientStreamTracerFactory(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        this.method = method;
        this.callOptions = callOptions;
        this.next = next;
    }

    @Override
    public ClientStreamTracer newClientStreamTracer(ClientStreamTracer.StreamInfo info, Metadata headers) {
        return new CustomClientStreamTracer<>(method, callOptions, next, info, headers);
    }
}

@Slf4j
class CustomClientStreamTracer<ReqT, RespT> extends ClientStreamTracer {

    public CustomClientStreamTracer(MethodDescriptor<ReqT, RespT> method,
                                    CallOptions callOptions,
                                    Channel next,
                                    StreamInfo info,
                                    Metadata headers) {
        log.info("method: {}", method.getFullMethodName());
    }

    @Override
    public void inboundHeaders() {
        log.info("CustomClientStreamTracer inboundHeaders");
        super.inboundHeaders();
    }

    @Override
    public void inboundMessage(int seqNo) {
        log.info("CustomClientStreamTracer inboundMessage, seqNo: {}", seqNo);
        super.inboundMessage(seqNo);
    }

    @Override
    public void inboundMessageRead(int seqNo, long optionalWireSize, long optionalUncompressedSize) {
        log.info("CustomClientStreamTracer inboundMessage, seqNo: {}, optionalWireSize: {} optionalUncompressedSize: {},", seqNo, optionalWireSize, optionalUncompressedSize);
        super.inboundMessageRead(seqNo, optionalWireSize, optionalUncompressedSize);
    }

    @Override
    public void inboundUncompressedSize(long bytes) {
        log.info("CustomClientStreamTracer inboundUncompressedSize, bytes: {}", bytes);
        super.inboundUncompressedSize(bytes);
    }

    @Override
    public void inboundWireSize(long bytes) {
        log.info("CustomClientStreamTracer inboundWireSize, bytes: {}", bytes);
        super.inboundWireSize(bytes);
    }

    @Override
    public void inboundTrailers(Metadata trailers) {
        log.info("CustomClientStreamTracer inboundTrailers, trailers: {}", trailers);
        super.inboundTrailers(trailers);
    }

    @Override
    public void outboundHeaders() {
        log.info("CustomClientStreamTracer outboundHeaders");
        super.outboundHeaders();
    }

    @Override
    public void outboundMessage(int seqNo) {
        log.info("CustomClientStreamTracer outboundMessage, seqNo: {}", seqNo);
        super.outboundMessage(seqNo);
    }

    @Override
    public void outboundMessageSent(int seqNo, long optionalWireSize, long optionalUncompressedSize) {
        log.info("CustomClientStreamTracer outboundMessageSent, seqNo: {}, optionalWireSize: {} optionalUncompressedSize: {},", seqNo, optionalWireSize, optionalUncompressedSize);
        super.outboundMessageSent(seqNo, optionalWireSize, optionalUncompressedSize);
    }

    @Override
    public void outboundWireSize(long bytes) {
        log.info("CustomClientStreamTracer outboundWireSize, bytes: {}", bytes);
        super.outboundWireSize(bytes);
    }

    @Override
    public void outboundUncompressedSize(long bytes) {
        log.info("CustomClientStreamTracer outboundUncompressedSize, bytes: {}", bytes);
        super.outboundUncompressedSize(bytes);
    }

    @Override
    public void streamClosed(Status status) {
        log.info("CustomClientStreamTracer streamClosed, status: {}", status);
        super.streamClosed(status);
    }
}
