package blog.yuanyuan.yuanlive.gateway.routing;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.DefaultResponse;
import org.springframework.cloud.client.loadbalancer.Request;
import org.springframework.cloud.client.loadbalancer.RequestDataContext;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.List;

/** 只改变 /ws 的新连接选址，普通 live-service HTTP 请求继续使用轮询策略。 */
public class WebSocketRendezvousLoadBalancer implements ReactorServiceInstanceLoadBalancer {
    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;
    private final RoundRobinLoadBalancer fallback;

    public WebSocketRendezvousLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider, String serviceId) {
        this.supplierProvider = supplierProvider;
        this.fallback = new RoundRobinLoadBalancer(supplierProvider, serviceId);
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        String routeKey = routeKey(request);
        if (routeKey == null) return fallback.choose(request);
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
        if (supplier == null) return fallback.choose(request);
        return supplier.get(request).next().flatMap(instances -> RendezvousHashSelector.select(routeKey,
                        instances.stream().map(this::candidate).toList())
                .flatMap(id -> instances.stream().filter(instance -> stableId(instance).equals(id)).findFirst())
                .<Mono<Response<ServiceInstance>>>map(instance -> Mono.just(new DefaultResponse(instance)))
                .orElseGet(() -> fallback.choose(request)));
    }

    private RendezvousHashSelector.Candidate candidate(ServiceInstance instance) {
        return new RendezvousHashSelector.Candidate(stableId(instance), "true".equalsIgnoreCase(instance.getMetadata().get("draining")));
    }

    private String stableId(ServiceInstance instance) {
        return instance.getServiceId() + "@" + instance.getHost() + ":" + instance.getPort();
    }

    private String routeKey(Request request) {
        if (!(request.getContext() instanceof RequestDataContext context) || !(context.getClientRequest() instanceof ServerHttpRequest httpRequest)) return null;
        String path = httpRequest.getPath().value();
        if (!"/ws".equals(path) && !path.startsWith("/ws/")) return null;
        String scope = httpRequest.getQueryParams().getFirst("scope");
        if ("ROOM".equalsIgnoreCase(scope)) return httpRequest.getQueryParams().getFirst("roomId");
        if (!"APP".equalsIgnoreCase(scope)) return null;
        String token = httpRequest.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (token == null || token.isBlank()) return null;
        try {
            Object userId = StpUtil.getLoginIdByToken(token);
            return userId == null ? null : String.valueOf(userId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
