package io.github.helloworlde.grpc.loadbalcner;

import io.grpc.ConnectivityState;
import io.grpc.ConnectivityStateInfo;
import io.grpc.LoadBalancer;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

import static io.github.helloworlde.grpc.loadbalcner.CustomLoadBalancer.STATE_INFO;
import static io.grpc.ConnectivityState.CONNECTING;
import static io.grpc.ConnectivityState.IDLE;
import static io.grpc.ConnectivityState.READY;
import static io.grpc.ConnectivityState.SHUTDOWN;
import static io.grpc.ConnectivityState.TRANSIENT_FAILURE;

@Slf4j
public class CustomSubchannelStateListener implements LoadBalancer.SubchannelStateListener {
    private final LoadBalancer.Subchannel subchannel;
    private final LoadBalancer.Helper helper;
    private final CustomLoadBalancer loadBalancer;

    public CustomSubchannelStateListener(CustomLoadBalancer customLoadBalancer,
                                         LoadBalancer.Subchannel subchannel,
                                         LoadBalancer.Helper helper) {
        this.loadBalancer = customLoadBalancer;
        this.subchannel = subchannel;
        this.helper = helper;
    }

    @Override
    public void onSubchannelState(ConnectivityStateInfo stateInfo) {
        Ref<ConnectivityState> stateInfoRef = subchannel.getAttributes().get(STATE_INFO);
        ConnectivityState currentState = stateInfoRef.getValue();
        ConnectivityState newState = stateInfo.getState();

        log.info("{} 状态变化:{}", subchannel, newState);

        if (newState == SHUTDOWN) {
            log.info("关闭 {}", subchannel);
            return;
        }

        if (newState == READY) {
            subchannel.requestConnection();
        }

        if (currentState == TRANSIENT_FAILURE) {
            if (newState == CONNECTING || newState == IDLE) {
                log.info("{} 建立连接或者失败", subchannel);
                return;
            }
        }

        stateInfoRef.setValue(newState);
        updateLoadBalancerState();
    }

    private void updateLoadBalancerState() {
        List<LoadBalancer.Subchannel> readySubchannels = loadBalancer.getSubchannelMap()
                                                                     .values()
                                                                     .stream()
                                                                     .filter(s -> s.getAttributes().get(STATE_INFO).getValue() == READY)
                                                                     .collect(Collectors.toList());

        if (readySubchannels.isEmpty()) {
            log.info("更新 LB 状态为 CONNECTING，没有 READY 的 Subchannel");
            helper.updateBalancingState(CONNECTING, new CustomSubchannelPicker(LoadBalancer.PickResult.withNoResult()));
        } else {
            log.info("更新 LB 状态为 READY，Subchannel 为:{}", readySubchannels.toArray());
            helper.updateBalancingState(READY, new CustomSubchannelPicker(readySubchannels));
        }
    }
}
