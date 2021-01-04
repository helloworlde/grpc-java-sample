package io.github.helloworlde.grpc.loadbalcner;

import io.grpc.LoadBalancer;
import io.grpc.LoadBalancerProvider;

public class CustomLoadBalancerProvider extends LoadBalancerProvider {

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public String getPolicyName() {
        return "custom_round_robin";
    }

    @Override
    public LoadBalancer newLoadBalancer(LoadBalancer.Helper helper) {
        return new CustomLoadBalancer(helper);
    }
}
