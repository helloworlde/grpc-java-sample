# gRPC 输出底层库的日志

gRPC 的库使用 `java.util.logging` 和 log4j 输出日志，可以使用 Logback，适配其他的日志，用于输出或调整级别，在调试或者排查问题时容易使用 ，这里使用 slf4j

## 添加依赖

因为 gRPC 内部以及 Netty 使用了多种库，适配较复杂，所以 Google 提供 logback 的适配实现

- build.gradle.kts

```kotlin
dependencies {
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")
    implementation("com.google.cloud:google-cloud-logging-logback:${logbackVersion}")
}
```

## 添加 logback 配置

- logback.xml

在 `src/main/resources` 下面添加 `logback.xml` 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>

<configuration debug="false" scan="false" scanPeriod="30 seconds">

    <contextName>grpc-java-demo</contextName>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                [%date{yyyy-MM-dd HH:mm:ss.SSS}] [%thread] %level %logger - %message%n
            </pattern>
        </encoder>
    </appender>

    <root level="DEBUG">
        <appender-ref ref="STDOUT"/>
    </root>

</configuration>
```

## 输出

```
[2021-01-03 20:44:46.453] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND SETTINGS: ack=false settings={ENABLE_PUSH=0, MAX_CONCURRENT_STREAMS=0, INITIAL_WINDOW_SIZE=1048576, MAX_HEADER_LIST_SIZE=8192}
[2021-01-03 20:44:46.463] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND WINDOW_UPDATE: streamId=0 windowSizeIncrement=983041
[2021-01-03 20:44:46.475] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND SETTINGS: ack=false settings={MAX_CONCURRENT_STREAMS=2147483647, INITIAL_WINDOW_SIZE=1048576, MAX_HEADER_LIST_SIZE=8192}
[2021-01-03 20:44:46.477] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND SETTINGS: ack=true
[2021-01-03 20:44:46.478] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND WINDOW_UPDATE: streamId=0 windowSizeIncrement=983041
[2021-01-03 20:44:46.478] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND SETTINGS: ack=true
[2021-01-03 20:44:46.514] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND HEADERS: streamId=3 headers=GrpcHttp2OutboundHeaders[:authority: 127.0.0.1:9090, :path: /io.github.helloworlde.grpc.HelloService/SayHello, :method: POST, :scheme: http, content-type: application/grpc, te: trailers, user-agent: grpc-java-netty/1.34.1, grpc-accept-encoding: gzip] streamDependency=0 weight=16 exclusive=false padding=0 endStream=false
[2021-01-03 20:44:46.524] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND DATA: streamId=3 padding=0 endStream=true length=20 bytes=000000000f0a0d426c6f636b696e672053747562
[2021-01-03 20:44:46.527] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND PING: ack=false bytes=1234
[2021-01-03 20:44:46.528] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND PING: ack=true bytes=1234
[2021-01-03 20:44:46.532] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND HEADERS: streamId=3 headers=GrpcHttp2ResponseHeaders[:status: 200, content-type: application/grpc, grpc-encoding: identity, grpc-accept-encoding: gzip] padding=0 endStream=false
[2021-01-03 20:44:46.536] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND DATA: streamId=3 padding=0 endStream=false length=26 bytes=00000000150a1348656c6c6f20426c6f636b696e672053747562
[2021-01-03 20:44:46.536] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] OUTBOUND PING: ack=false bytes=1234
[2021-01-03 20:44:46.539] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND HEADERS: streamId=3 headers=GrpcHttp2ResponseHeaders[grpc-status: 0] padding=0 endStream=true
[2021-01-03 20:44:46.542] [grpc-nio-worker-ELG-1-2] DEBUG io.grpc.netty.shaded.io.grpc.netty.NettyClientHandler - [id: 0xaeabbfb2, L:/127.0.0.1:62532 - R:/127.0.0.1:9090] INBOUND PING: ack=true bytes=1234
[2021-01-03 20:44:46.544] [main] INFO io.github.helloworlde.grpc.LogClient - Hello Blocking Stub
```