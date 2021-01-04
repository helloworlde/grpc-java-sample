# gRPC 输出底层库的日志

gRPC 的库使用 `java.util.logging` 输出日志，用于输出或调整级别，在调试或者排查问题时容易使用

## 使用 java.util.logging 

#### 1. 设置日志级别

会将所有的 gRPC 日志输出

```java
    public static void main(String[] args) throws InterruptedException{
        setLogger("io.grpc");
    }
    
    private static void setLogger(String className) {
        Logger logger = Logger.getLogger(className);
        logger.setLevel(Level.ALL);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }
```

#### 2. 输出

```
一月 04, 2021 11:06:39 上午 io.grpc.ChannelLogger log
非常详细: [Channel<1>: (127.0.0.1:9090)] Resolved address: [[[/127.0.0.1:9090]/{}]], config={}
一月 04, 2021 11:06:39 上午 io.grpc.ChannelLogger log
非常详细: [Channel<1>: (127.0.0.1:9090)] Address resolved: [[[/127.0.0.1:9090]/{}]]
一月 04, 2021 11:06:39 上午 io.grpc.ChannelLogger log
非常详细: [Subchannel<3>: (127.0.0.1:9090)] Subchannel for [[[/127.0.0.1:9090]/{}]] created
一月 04, 2021 11:06:39 上午 io.grpc.ChannelLogger log
非常详细: [Channel<1>: (127.0.0.1:9090)] Child Subchannel started
一月 04, 2021 11:06:39 上午 io.grpc.ChannelLogger log
非常详细: [Channel<1>: (127.0.0.1:9090)] Entering CONNECTING state with picker: Picker{result=PickResult{subchannel=Subchannel<3>: (127.0.0.1:9090), streamTracerFactory=null, status=Status{code=OK, description=null, cause=null}, drop=false}}
一月 04, 2021 11:06:39 上午 io.grpc.ChannelLogger log
非常详细: [Subchannel<3>: (127.0.0.1:9090)] CONNECTING as requested
一月 04, 2021 11:06:39 上午 io.grpc.netty.Utils getByteBufAllocator
详细: Using custom allocator: forceHeapBuffer=false, defaultPreferDirect=true
一月 04, 2021 11:06:39 上午 io.grpc.netty.Utils createByteBufAllocator
详细: Creating allocator, preferDirect=true
```

## 使用 logback 

使用 Logback 替换其他的日志，但是无法输出 FINEST 级别的日志

#### 1. 添加依赖

- build.gradle.kts

添加 Slf4j 以及 logback 的依赖

```kotlin
dependencies {
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")
    implementation("org.slf4j:jul-to-slf4j:${slf4jVersion}")
    implementation("ch.qos.logback:logback-core:${logbackVersion}")
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")
}
```

#### 2. 添加 logback 配置

- logback.xml

在 `src/main/resources` 下面添加 `logback.xml` 配置

```xml
<?xml version="1.0" encoding="UTF-8"?>

<configuration debug="false" scan="false" scanPeriod="30 seconds">

    <contextName>grpc-java-sample</contextName>

    <!--  重置 java.util.logger 的级别 -->
    <contextListener class="ch.qos.logback.classic.jul.LevelChangePropagator">
        <resetJUL>true</resetJUL>
    </contextListener>

    <appender name="CONSOLE_OUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{35} - %msg %n
            </pattern>
        </encoder>
    </appender>

    <root level="TRACE">
        <appender-ref ref="CONSOLE_OUT"/>
    </root>

</configuration>
```

#### 3. 添加 Slf4j 适配器

在项目启动类中添加适配处理器，这样就会将 java.util.logging 的日志输出到 Slf4j

```java
SLF4JBridgeHandler.removeHandlersForRootLogger();
SLF4JBridgeHandler.install();
```

#### 4. 输出

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