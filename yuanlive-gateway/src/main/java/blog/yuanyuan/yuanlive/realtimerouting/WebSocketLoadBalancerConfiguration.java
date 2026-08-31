package blog.yuanyuan.yuanlive.realtimerouting;

import blog.yuanyuan.yuanlive.gateway.routing.WebSocketRendezvousLoadBalancer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class WebSocketLoadBalancerConfiguration {
    @Bean
    ReactorServiceInstanceLoadBalancer liveServiceLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider) {
        return new WebSocketRendezvousLoadBalancer(supplierProvider, "live-service");
    }
}
