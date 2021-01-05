# gRPC 服务使用 TLS 加密

gRPC 支持使用 TLS 对请求进行加密

> SSL(Secure Socket Layer，安全套接字)，是面向连接的网络层和应用层协议之间的一种协议层；SSL 通过互相认证、使用数字签名确保完整性、使用加密确保隐私性，以实现客户端和服务端之间的安全通讯
> 
> TLS(Transport Layer Security, 传输层安全协议)，用于两个应用程序之间提供保密性和数据完整性
> 
> SSL是基于 HTTP 之下 TCP 之上的一个协议层，在SSL更新到3.0时，IETF对SSL3.0进行了标准化，并添加了少数机制(但是几乎和SSL3.0无差异)，标准化后的IETF更名为TLS1.0(Transport Layer Security 安全传输层协议)，可以说TLS就是SSL的新版本3.1

## 生成证书

可以通过 openssl 生成一个自签名的证书，用于加密

1. 添加配置

指定证书的配置，其中 `CN` 指定了访问的域名，如果实际域名与证书域名不一致，会导致连接失败

- certificate.conf

```conf
[req]
default_bits = 4096
prompt = no
default_md = sha256
req_extensions = req_ext
distinguished_name = dn
[dn]
C = CN
ST = BJ
O = helloworlde
CN = localhost
[req_ext]
subjectAltName = @alt_names
[alt_names]
DNS.1 = localhost
IP.1 = ::1
IP.2 = 127.0.0.1
```

2. 生成证书

生成自签名的证书，因为 Netty 的 `SslContextBuilder` 和 `SslContext` 仅支持 `PKCS8` 格式的 key，所以需要将其他格式的 key 转换为 `PKCS8` 格式

```bash
openssl genrsa -out ca.key 4096 
openssl req -new -x509 -key ca.key -sha256 -subj "/C=US/ST=NJ/O=CA, Inc." -days 3650 -out ca.cert 
openssl genrsa -out private.key 4096 
openssl req -new -key private.key -out private.csr -config certificate.conf 
openssl x509 -req -in private.csr -CA ca.cert -CAkey ca.key -CAcreateserial -out server.pem -days 3650 -sha256 -extfile certificate.conf -extensions req_ext 
openssl pkcs8 -topk8 -nocrypt -in private.key -out server.key
```

执行命名后，会生成多个文件，其中 Server 端需要私钥 `server.key` 以及证书 `server.pem`，客户端需要证书 `server.pem` 


## Server 端

- 配置 SSL 

```diff
@Slf4j
public class TlsServer {

    @SneakyThrows
    public static void main(String[] args) {
        // 初始化 SSL 上下文
+       File keyCertChainFile = new File("tls/src/main/resources/cert/server.pem");
+       File keyFile = new File("tls/src/main/resources/cert/server.key");
+       SslContextBuilder builder = SslContextBuilder.forServer(keyCertChainFile, keyFile);
+       SslContext sslContext = GrpcSslContexts.configure(builder).build();

        // 构建 Server
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress(9090))
                                          // 添加服务
                                          .addService(new HelloServiceImpl())
+                                         .sslContext(sslContext)
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

## Client 端

- 配置 SSL

为 Channel 指定了 SSL 上下文配置，并且覆盖了 `authority`，要和证书中的配置一致，用于建立连接时校验

```diff
@Slf4j
public class TlsClient {

    @SneakyThrows
    public static void main(String[] args) {

+       File trustCertCollectionFile = new File("tls/src/main/resources/cert/server.pem");
+       SslContextBuilder builder = GrpcSslContexts.forClient();
+       SslContext sslContext = builder.trustManager(trustCertCollectionFile).build();

        // 构建 Channel
        ManagedChannel channel = NettyChannelBuilder.forAddress("127.0.0.1", 9090)
+                                                   .overrideAuthority("localhost")
+                                                   .sslContext(sslContext)
                                                    .build();

        // 使用 Channel 构建 BlockingStub
        HelloServiceGrpc.HelloServiceBlockingStub blockingStub = HelloServiceGrpc.newBlockingStub(channel);

        // 构建消息
        HelloMessage message = HelloMessage.newBuilder()
                                           .setMessage("TLS")
                                           .build();

        // 发送消息，并返回响应
        HelloResponse helloResponse = blockingStub.sayHello(message);
        log.info(helloResponse.getMessage());

        // 等待终止
        channel.awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

## 测试

1. 调整日志级别 
```java
    setLogger("io.grpc");

    private static void setLogger(String className) {
        Logger logger = Logger.getLogger(className);
        logger.setLevel(Level.ALL);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
    }
```
2. 启动 Serve 端

3. 启动 Client 端，发起请求

```
一月 05, 2021 5:22:21 下午 io.grpc.netty.ProtocolNegotiators logSslEngineDetails
较详细: TLS negotiation succeeded.
SSLEngine Details: [
    JDK9 ALPN
    TLS Protocol: TLSv1.2
    Application Protocol: h2
    Need Client Auth: false
    Want Client Auth: false
    Supported protocols=[SSLv2Hello, SSLv3, TLSv1, TLSv1.1, TLSv1.2]
    Enabled protocols=[TLSv1, TLSv1.1, TLSv1.2]
    Supported ciphers=[TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384, TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384, TLS_RSA_WITH_AES_256_CBC_SHA256, TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384, TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384, TLS_DHE_RSA_WITH_AES_256_CBC_SHA256, TLS_DHE_DSS_WITH_AES_256_CBC_SHA256, TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA, TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA, TLS_RSA_WITH_AES_256_CBC_SHA, TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA, TLS_ECDH_RSA_WITH_AES_256_CBC_SHA, TLS_DHE_RSA_WITH_AES_256_CBC_SHA, TLS_DHE_DSS_WITH_AES_256_CBC_SHA, TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256, TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256, TLS_RSA_WITH_AES_128_CBC_SHA256, TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256, TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256, TLS_DHE_RSA_WITH_AES_128_CBC_SHA256, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256, TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA, TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA, TLS_RSA_WITH_AES_128_CBC_SHA, TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA, TLS_ECDH_RSA_WITH_AES_128_CBC_SHA, TLS_DHE_RSA_WITH_AES_128_CBC_SHA, TLS_DHE_DSS_WITH_AES_128_CBC_SHA, TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, TLS_RSA_WITH_AES_256_GCM_SHA384, TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384, TLS_DHE_RSA_WITH_AES_256_GCM_SHA384, TLS_DHE_DSS_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, TLS_RSA_WITH_AES_128_GCM_SHA256, TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256, TLS_DHE_RSA_WITH_AES_128_GCM_SHA256, TLS_DHE_DSS_WITH_AES_128_GCM_SHA256, TLS_EMPTY_RENEGOTIATION_INFO_SCSV]
    Enabled ciphers=[TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256]
]
一月 05, 2021 5:22:21 下午 io.grpc.ChannelLogger log
非常详细: [NettyClientTransport<4>: (/127.0.0.1:9090)] ClientTls completed
```



## 参考文档

- [Authentication](https://grpc.io/docs/guides/auth/)
- [Authentication](https://github.com/grpc/grpc-java/blob/master/SECURITY.md)
- [在线RSA PKCS#1、PKCS#8格式转换工具](http://www.metools.info/code/c84.html)
- [SslContextBuilder and Private Key](https://netty.io/wiki/sslcontextbuilder-and-private-key.html)
- [配置密钥](https://opendocs.alipay.com/open/common/104740)
- [grpc-tls](https://github.com/nleiva/grpc-tls)
- [TLS详解](https://www.jianshu.com/p/1fc7130eb2c2)