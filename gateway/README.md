# gRPC Gateway 使用

gRPC Gateway 可以代理 gRPC 服务，接收 HTTP 请求，并转为 gRPC 请求由服务进行处理，并将返回结果转换为 HTTP 响应发送给调用者 gRPC Gateway

支持代理单个服务或者多个服务，当代理多个服务时，可以通过命名解析实现转发请求

## 快速使用

- 启动项目

```bash
git clone https://github.com/helloworlde/grpc-gateway.git & cd grpc-gateway
make all 
```

- 访问

```bash
curl localhost:8090/hello\?message=world

{"result":"Hello world"}%
```

## 实现

### 安装依赖

- 安装 buf

buf 用于代替 protoc 进行生成代码，可以避免使用复杂的 protoc 命令，避免 protoc 各种失败问题

```bash
brew tap bufbuild/buf
brew install buf
```

- 安装 grpc-gateway

```bash
go install \
    github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-grpc-gateway \
    github.com/grpc-ecosystem/grpc-gateway/v2/protoc-gen-openapiv2
```

- 添加 buf 配置文件 buf.gen.yaml

```diff
version: v1beta1
plugins:
  - name: go
    out: proto
    opt: paths=source_relative
  - name: go-grpc
    out: proto
    opt: paths=source_relative,require_unimplemented_servers=false
```

- 添加配置文件 buf.yaml

```yaml
version: v1beta1
build:
  roots:
    - proto
```

### 实现服务端

- 定义 proto

```protobuf
syntax = "proto3";

package io.github.helloworlde;
option go_package = "github.com/helloworlde/grpc-gateway;grpc_gateway";
option java_package = "io.github.helloworlde";
option java_multiple_files = true;
option java_outer_classname = "HelloGrpc";

service HelloService {
    rpc Hello (HelloMessage) returns (HelloResponse) {
    }
}

message HelloMessage {
    string message = 1;
}

message HelloResponse {
    string result = 1;
}
```

- 生成代码

```bash
buf generate
```

- 实现接口

```go
import (
    "context"

    pb "github.com/helloworlde/grpc-gateway/proto/api"
)

type HelloService struct {
}

func (h *HelloService) Hello(ctx context.Context, message *pb.HelloMessage) (*pb.HelloResponse, error) {
	helloMessage := "Hello " + message.GetMessage()

	response := pb.HelloResponse{Result: helloMessage}

	return &response, nil
}

```

- 启动 Server

```go
func StartGrpcServer() {
	listener, err := net.Listen("tcp", ":9090")
	if err != nil {
		log.Fatalln("Listen gRPC port failed: ", err)
	}

	server := grpc.NewServer()
	pb.RegisterHelloServiceServer(server, &helloService)

	log.Println("Start gRPC Server on 0.0.0.0:9090")
	err = server.Serve(listener)
	if err != nil {
		log.Fatalln("Start gRPC Server failed: ", err)
	}

}
```

```go
func main() {
  server.StartGrpcServer()
}
```

启动 Server 后，会监听 8090 端口，对外提供服务

### 实现 Gateway

- 添加 google.api 的 proto

添加 [annotations.proto](https://github.com/grpc-ecosystem/grpc-gateway/blob/master/third_party/googleapis/google/api/annotations.proto)和 [http.proto](https://github.com/grpc-ecosystem/grpc-gateway/blob/master/third_party/googleapis/google/api/http.proto)文件到 `proto/google/api/`下；这两个文件用于支持 gRPC Gateway 代理

- 修改业务的 proto 文件

```diff

+import "google/api/annotations.proto";

service HelloService{
  rpc Hello(HelloMessage) returns (HelloResponse){
+    option (google.api.http) = {
+      get: "/hello"
+    };
  }
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
	go server.StartGrpcServer()
	server.StartGwServer()
}
```

### 测试

- 启动应用

```bash
curl localhost:8090/hello\?message=world

{"result":"Hello world"}%
```

## 参考文档

- [grpc-gateway](https://github.com/grpc-ecosystem/grpc-gateway)
- [buf](https://buf.build/)
- [grpc-gateway document](https://grpc-ecosystem.github.io/grpc-gateway/)
- [grpc-gateway-usage](https://github.com/helloworlde/grpc-gateway)