package blog.yuanyuan.yuanlive.gateway.filter;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class TraceIdFilter implements GlobalFilter, Ordered {
    private static final String TRACE_ID_HEADER = "traceId";
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = StrUtil.isBlank(incomingTraceId) ? IdUtil.fastSimpleUUID() : incomingTraceId;

        // 2. 放入 MDC (为了让网关自己的日志也能打印出 ID)
        // 注意：WebFlux 中 MDC 支持有限，但这行能保证当前线程的日志有 ID
        MDC.put(TRACE_ID_HEADER, traceId);
        log.info("========================================== Start ==========================================");

        // 3. 放入 Request Header (传递给下游微服务)
        ServerHttpRequest newRequest = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TRACE_ID_HEADER, traceId))
                .build();
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

        return chain.filter(exchange.mutate().request(newRequest).build())
                .doFinally(signalType -> {
                    // 4. 请求结束，清理 MDC，防止内存泄漏或线程污染
                    log.info("=========================================== End ===========================================");
                    MDC.remove(TRACE_ID_HEADER);
                });
    }

    @Override
    public int getOrder() {
        // 优先级设为最高，保证最先执行
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
