# grpc-java

## 模块

- alts: [ALTS](https://cloud.google.com/security/encryption-in-transit/application-layer-transport-security/) 是传输层安全协议，是 Google 内部的安全协议，该模块是用于 Google 相关的工具
- api: 对外提供的接口，有常用的 Channel、NameResolver、LoadBalancer、拦截器等
- auth: Google 鉴权相关的类
- benchmarks: 性能测试
- bom: 用于生成 gRPC Bom
- census: Census 相关的类，用于统计，已被 Telemetry 代替
- context: gRPC 上下文相关的类
- core: gRPC 核心实现
- grpclb: 负载均衡策略，通过 DNS 发现服务后，支持与双向流同步实例信息
- netty: gRPC netty 扩展实现
- okhttp: gRPC OkHTTP 扩展实现
- protobuf: protobuf 的扩展
- protobuf-lite: protobuf 部分扩展
- rls: LRU 负载均衡策略
- services: gRPC 工具类服务实现，包括二进制日志、健康检查、Channelz 统计、反射服务
- stub: gRPC 各类 Stub 的实现
- xds: xDS 协议相关的实现

## 工具

- [grpcurl](https://github.com/fullstorydev/grpcurl)

与 curl 定位一样，用于在命令行调用 gRPC 接口

- [grpc-swagger](https://github.com/grpc-swagger/grpc-swagger)

Java 服务，通过反射生成 gRPC 接口的 Swagger 文档，可以方便的进行调用测试

- [buf](https://buf.build/)

可以替代 protoc，通过配置文件，可以简化生成命令，扩展更多功能