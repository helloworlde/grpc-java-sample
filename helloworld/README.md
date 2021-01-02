# 使用 Gradle 构建 gRPC 服务

## Gradle 配置

- build.gradle.kts

修改 Gradle 配置，添加 gRPC 相关的插件、依赖和任务

```diff
+import com.google.protobuf.gradle.*

plugins {
    java
    idea
    application
+   id("com.google.protobuf") version "0.8.14"
    id("io.freefair.lombok") version "5.3.0"
}

repositories {
    mavenCentral()
    jcenter()
}

+val grpcVersion = "1.34.1"

dependencies {
+   implementation("io.grpc:grpc-netty:${grpcVersion}")
+   implementation("io.grpc:grpc-protobuf:${grpcVersion}")
+   implementation("io.grpc:grpc-stub:${grpcVersion}")
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")
    implementation("org.slf4j:slf4j-simple:${slf4jVersion}")
}

+sourceSets {
+    main {
+        proto {
+            srcDir("src/main/resources/proto")
+        }
+    }
+}

+protobuf {
+    protoc { artifact = "com.google.protobuf:protoc:${protocVersion}" }
+    plugins {
+        id("grpc") {
+            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
+        }
+    }
+    generateProtoTasks {
+        ofSourceSet("main").forEach {
+            it.plugins {
+                id("grpc")
+            }
+        }
+    }
+}
```

添加完成后，执行构建时会根据 proto 文件生成相应的 Java 代码

## 添加 proto

- 在 `src/main/resources/proto`下添加 proto 文件

```protobuf
syntax = "proto3";

package io.github.helloworlde.grpc;

option java_package = "io.github.helloworlde.grpc";
option java_multiple_files = true;
option java_outer_classname = "HelloWorldGrpc";

service HelloService{
  rpc SayHello(HelloMessage) returns (HelloResponse){
  }
}

message HelloMessage {
  string message = 1;
}

message HelloResponse {
  string message = 1;
}
```

## 实现 Server 端

- 服务实现

```java

@Slf4j
class HelloServiceImpl extends HelloServiceGrpc.HelloServiceImplBase {

    @Override
    public void sayHello(HelloMessage request, StreamObserver<HelloResponse> responseObserver) {
        log.info("收到客户端请求: " + request.getMessage());

        HelloResponse response = HelloResponse.newBuilder()
                                              .setMessage("Hello " + request.getMessage())
                                              .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

- 启动 Server

```java

@Slf4j
public class HelloWorldServer {

    @SneakyThrows
    public static void main(String[] args) {
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          .addService(new HelloServiceImpl())
                                          .build();

        server.start();
        log.info("服务端启动成功");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        server.awaitTermination();
    }
}
```

## 实现 Client 端

使用 BlockingStub 发起请求，这个调用是同步的，会阻塞直到 Server 端返回结果；调用方法的返回值是

```java

@Slf4j
public class HelloWorldClient {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("Blocking Stub")
                                           .build();

        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());

        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```