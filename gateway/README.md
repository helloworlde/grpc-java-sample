# gRPC Gateway 使用

gRPC Gateway 可以代理 gRPC 服务，接收 HTTP 请求，并转为 gRPC 请求由服务进行处理，并将返回结果转换为 HTTP 响应发送给调用者 gRPC Gateway

支持代理单个服务或者多个服务，当代理多个服务时，可以通过命名解析实现转发请求

关于 Gateway 的使用细节可以参考 [helloworlde/grpc-gateway](https://github.com/helloworlde/grpc-gateway)

## 快速使用

进入项目 gateway 目录，执行 Makefile 的命令

- 启动 Server 端

会执行构建，并启动 Server 端

```bash
make start-server 
```

- 启动 Gateway

会构建并启动 Gateway，构建依赖 `buf`，详细请参考 [buf](https://docs.buf.build/installation)

```bash
make start-gw
```

- 访问

```bash
curl http://localhost:8090/hello\?message\=Gateway

{"message":"Hello gateway"}%
```

## 实现

Server 端和 [helloworld](../helloworld) 一样


### 实现 Gateway

Gateway 的详细内容请参考 [helloworlde/grpc-gateway](https://github.com/helloworlde/grpc-gateway)

- 添加 google.api 的 proto

添加 [annotations.proto](https://github.com/grpc-ecosystem/grpc-gateway/blob/master/third_party/googleapis/google/api/annotations.proto)和 [http.proto](https://github.com/grpc-ecosystem/grpc-gateway/blob/master/third_party/googleapis/google/api/http.proto)文件到 `proto/google/api/`下；这两个文件用于支持 gRPC Gateway 代理

- 修改业务的 proto 文件

```diff
  syntax = "proto3";
  
  package io.github.helloworlde.grpc;
  
+ import "google/api/annotations.proto";
  
+ option go_package = "api;grpc_gateway";
  option java_package = "io.github.helloworlde.grpc";
  option java_multiple_files = true;
  option java_outer_classname = "HelloWorldGrpc";
  
  service HelloService{
    rpc SayHello(HelloMessage) returns (HelloResponse){
+     option (google.api.http) = {
+       get: "/hello"
+     };
    }
  }
  
  message HelloMessage {
    string message = 1;
  }
  
  message HelloResponse {
    string message = 1;
  }
```

- 修改 buf.gen.yaml，添加生成 Gateway 代码的配置

```diff
version: v1beta1
plugins:
  - name: go
    out: proto
    opt: paths=source_relative
  - name: go-grpc
    out: proto
    opt: paths=source_relative,require_unimplemented_servers=false
+ - name: grpc-gateway
+   out: proto
+   opt: paths=source_relative
```

- 生成 Gateway 的代码

会生成 `*.gw.go` 格式的文件，该文件是 gRPC Gateway 代理具体服务的实现

```bash
buf generete
```

- 添加 gRPC Gateway 代理 Server

启动 `0.0.0.0:9090` 就是要被代理的 Server 的地址，如果代理多个，则应该命名解析支持的格式

```go
func StartGwServer() {
	conn, _ := grpc.DialContext(
		context.Background(),
		"0.0.0.0:9090",
		grpc.WithBlock(),
		grpc.WithInsecure(),
	)

	mux := runtime.NewServeMux()
	// 注册服务
	pb.RegisterHelloServiceHandler(context.Background(), mux, conn)

	server := &http.Server{
		Addr:    ":8090",
		Handler: mux,
	}

	server.ListenAndServe()
}
```

- 启动 Gateway

```go
func main() {
	server.StartGwServer()
}
```

## 参考文档

- [grpc-gateway](https://github.com/grpc-ecosystem/grpc-gateway)
- [buf](https://buf.build/)
- [grpc-gateway document](https://grpc-ecosystem.github.io/grpc-gateway/)
- [grpc-gateway-usage](https://github.com/helloworlde/grpc-gateway)