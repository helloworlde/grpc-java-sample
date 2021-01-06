# gRPC 中使用 Channelz

gRPC 提供了 Channelz 用于对外提供服务的数据，用于调试、监控等；根据服务的角色不同，可以提供的数据有：

- 服务端: Servers, Server, ServerSockets, Socket
- 客户端: TopChannels, Channel, Subchannel

## Channelz 服务定义

参考 Channelz 的设计 [gRPC Channelz](https://github.com/grpc/proposal/blob/master/A14-channelz.md) 以及服务定义 [channelz.proto](https://github.com/grpc/grpc/blob/master/src/proto/grpc/channelz/channelz.proto)，提供了以下方法：

```protobuf
service Channelz {
// 返回所有的根 Channel(即应用直接创建的 Channel)
  rpc GetTopChannels(GetTopChannelsRequest) returns (GetTopChannelsResponse);
  // 根据 Channel ID 返回单个的 Channel 详情，包括 Subchannel，如果没有则返回 NOT_FOUND
  rpc GetChannel(GetChannelRequest) returns (GetChannelResponse);
  // 根据 Subchannel ID 返回 Subchannel 详情
  rpc GetSubchannel(GetSubchannelRequest) returns (GetSubchannelResponse);
  // 返回所有存在的 Server
  rpc GetServers(GetServersRequest) returns (GetServersResponse);
  // 根据 Server ID 返回 Server 详情
  rpc GetServer(GetServerRequest) returns (GetServerResponse);
  // 根据 Server ID 返回 Server 所有的 Socket
  rpc GetServerSockets(GetServerSocketsRequest) returns (GetServerSocketsResponse);
  // 根据 Socket ID 返回 Socket 详情
  rpc GetSocket(GetSocketRequest) returns (GetSocketResponse);
}
```

## 使用

### 添加依赖

- build.gradle.kts

Channelz 服务在 grpc-services 包中，需要添加该依赖

```kotlin
dependencies {
    implementation("io.grpc:grpc-netty:${grpcVersion}")
    implementation("io.grpc:grpc-protobuf:${grpcVersion}")
    implementation("io.grpc:grpc-stub:${grpcVersion}")
    implementation("io.grpc:grpc-services:${grpcVersion}")
}
```

### Server 端

Server 端添加 Channelz 服务非常简单，只需要将 Channelz 的服务添加到 Server 中即可

- Server

可以通过 `ChannelzService.newInstance(100)` 直接构建 Channelz 服务实例，参数是获取数据时分页的大小

```diff
@Slf4j
public class ChannelzServer {

    @SneakyThrows
    public static void main(String[] args) {

        // 构建 Server
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          // 添加服务
                                          .addService(new HelloServiceImpl())
                                          // 添加 Channelz 服务
+                                         .addService(ChannelzService.newInstance(100))
                                          // 添加反射服务，用于 grpcurl 等工具调试
+                                         .addService(ProtoReflectionService.newInstance())
                                          .build();

        // 启动 Server
        server.start();
        log.info("服务端启动成功");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        // 保持运行
        server.awaitTermination();
    }
}
```

### Client 端

Client 端不能直接开启，需要单独启动一个 Server，用于提供数据，与 Server 端一样，不过只提供 Channelz 的服务

```diff
@Slf4j
public class ChannelzClient {

    @SneakyThrows
    public static void main(String[] args) throws InterruptedException {

        // 构建并启动 Channelz 服务
+       Server server = NettyServerBuilder.forPort(9091)
+                                         // 添加 Channelz 服务
+                                         .addService(ChannelzService.newInstance(100))
+                                         // 添加反射服务，用于 grpcurl 等工具调试
+                                         .addService(ProtoReflectionService.newInstance())
+                                         .build()
+                                         .start();

        // 构建 Channel
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 9090)
                                                      .usePlaintext()
                                                      .build();

        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 发送多个请求，用于观察数据变化
        for (int i = 0; i < 10000; i++) {
            // 构建消息
            HelloMessage message = HelloMessage.newBuilder()
                                               .setMessage("Channelz " + i)
                                               .build();

            // 发送消息，并返回响应
            HelloResponse helloResponse = blockingStub.sayHello(message);
            log.info(helloResponse.getMessage());
            Thread.sleep(1000);
        }

        // 等待终止
+       server.awaitTermination();
    }
}
```

## 测试

分别启动 Server 端和 Client 端，发送请求；使用其他工具观察


### grpc-zpages 

gRPC 官方提供了 [grpc-zpages](https://github.com/grpc/grpc-experiments/tree/master/gdebug) 工具，可用通过 Web 查看指定服务的信息，不过该工具已经很久没有维护，使用较为复杂，具体可以参考 [A short introduction to Channelz](https://grpc.io/blog/a-short-introduction-to-channelz/)

### channelzcli 

是一个 CLI 工具，可以通过命令行实现获取 Channelz 的信息，对 Channelz 的数据做了一定的处理，较为友好，具体使用参考 [channelzcli](https://github.com/kazegusuri/channelzcli)

#### 安装

```bash
go get -u github.com/kazegusuri/channelzcli
```

#### 使用

- 获取 Channel 信息

```bash
channelzcli list channel -k --addr localhost:9091

ID	Name                                                                            	State	Channel	SubChannel	Calls	Success	Fail	LastCall
4	ManagedChannelImpl{logId=4, target=127.0.0.1:9090}                              	READY	0      	1         	4355  	4355  	0     	716ms
```

- 描述 Server 信息

```bash
channelzcli describe server 2 -k --addr localhost:9090

ID: 	2
Name:	ServerImpl{logId=2, transportServers=[NettyServer{logId=1, address=0.0.0.0/0.0.0.0:9090}]}
Calls:
  Started:        	14486
  Succeeded:      	14476
  Failed:         	9
  LastCallStarted:	2021-01-06 08:51:35.608 +0000 UTC
```

- 列出 Channel 信息

```bash
channelzcli tree channel -k --addr localhost:9091

127.0.0.1:9090 (ID:4) [READY]
  [Calls] Started:4627, Succeeded:4627, Failed:0, Last:638ms
  [Subchannels]
    |-- [[[/127.0.0.1:9090]/{}]] (ID:6) [READY]
          [Calls]: Started:4627, Succeeded:4627, Failed:0, Last:638ms
          [Socket] ID:7, Name:CallTracingTransport{delegate=CallCredentialsApplyingTransport{delegate=NettyClientTransport{logId=7, remoteAddress=/127.0.0.1:9090, channel=[id: 0x83c4d921, L:/127.0.0.1:52321 - R:/127.0.0.1:9090]}}}, RemoteName:, Local:[127.0.0.1]:52321 Remote:[127.0.0.1]:9090```
```

### grpcurl 

是一个 CLI 工具，可以像 CURL 一样对 gRPC 服务发起请求，但是需要服务添加[反射服务](https://github.com/grpc/proposal/blob/master/A15-promote-reflection.md)，详情参考 [grpcurl](https://github.com/fullstorydev/grpcurl)

#### 安装

```bash
brew install grpcurl
```

#### 使用

- 获取 Server 列表

```bash
grpcurl -plaintext localhost:9090 grpc.channelz.v1.Channelz/GetServers

{
  "server": [
    {
      "ref": {
        "server_id": "2",
        "name": "ServerImpl{logId=2, transportServers=[NettyServer{logId=1, address=0.0.0.0/0.0.0.0:9090}]}"
      },
      "data": {
        "calls_started": "13438",
        "calls_succeeded": "13432",
        "calls_failed": "4",
        "last_call_started_timestamp": "2021-01-06T08:34:17.763Z"
      },
      "listen_socket": [
        {
          "socket_id": "3",
          "name": "ListenSocket{logId=3, channel=[id: 0xc703c330, L:/0:0:0:0:0:0:0:0:9090]}"
        }
      ]
    }
  ],
  "end": true
}
```

- 获取 Channel 列表

```bash
grpcurl -plaintext localhost:9091 grpc.channelz.v1.Channelz/GetTopChannels

{
  "channel": [
    {
      "ref": {
        "channel_id": "4",
        "name": "ManagedChannelImpl{logId=4, target=127.0.0.1:9090}"
      },
      "data": {
        "state": {
          "state": "READY"
        },
        "target": "127.0.0.1:9090",
        "calls_started": "45",
        "calls_succeeded": "45",
        "last_call_started_timestamp": "2021-01-06T07:38:45.217Z"
      },
      "subchannel_ref": [
        {
          "subchannel_id": "6",
          "name": "InternalSubchannel{logId=6, addressGroups=[[[/127.0.0.1:9090]/{}]]}"
        }
      ]
    }
  ],
  "end": true
}
```