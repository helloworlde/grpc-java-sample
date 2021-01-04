package io.github.helloworlde.grpc.nameresolver;

import com.orbitz.consul.Consul;
import com.orbitz.consul.HealthClient;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.health.ServiceHealth;
import io.grpc.EquivalentAddressGroup;
import io.grpc.NameResolver;
import io.grpc.Status;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class CustomNameResolver extends NameResolver {

    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(10);

    private final String authority;
    private final Consul client;
    private Listener2 listener;

    public CustomNameResolver(String authority) {
        this.authority = authority;
        this.client = Consul.builder().build();
    }

    @Override
    public String getServiceAuthority() {
        return this.authority;
    }

    @Override
    public void shutdown() {
        this.executorService.shutdown();
    }

    @Override
    public void start(Listener2 listener) {
        this.listener = listener;
        // 定期从注册中心获取地址
        this.executorService.scheduleAtFixedRate(this::resolve, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void refresh() {
        this.resolve();
    }

    private void resolve() {
        log.info("开始解析服务: {}", this.authority);

        List<InetSocketAddress> addressList = getAddressList(this.authority);
        if (addressList == null || addressList.size() == 0) {
            log.error("解析服务: {} 失败，没有可用的节点", this.authority);
            listener.onError(Status.UNAVAILABLE.withDescription("没有可用的节点"));
            return;
        }

        List<EquivalentAddressGroup> equivalentAddressGroups = addressList.stream()
                                                                          .map(EquivalentAddressGroup::new)
                                                                          .collect(Collectors.toList());

        ResolutionResult resolutionResult = ResolutionResult.newBuilder()
                                                            .setAddresses(equivalentAddressGroups)
                                                            .build();

        this.listener.onResult(resolutionResult);
    }

    /**
     * 从 Consul 获取健康的服务
     *
     * @param serviceName 服务名称
     * @return 服务实例列表
     */
    private List<InetSocketAddress> getAddressList(String serviceName) {
        HealthClient healthClient = client.healthClient();
        ConsulResponse<List<ServiceHealth>> response = healthClient.getHealthyServiceInstances(serviceName);
        List<ServiceHealth> healthList = response.getResponse();

        log.info("从 Consul 中获取到服务: {} 共: {} 个实例", this.authority, healthList.size());

        return healthList.stream()
                         .map(ServiceHealth::getService)
                         .map(service -> new InetSocketAddress(service.getAddress(), service.getPort()))
                         .collect(Collectors.toList());
    }
}
