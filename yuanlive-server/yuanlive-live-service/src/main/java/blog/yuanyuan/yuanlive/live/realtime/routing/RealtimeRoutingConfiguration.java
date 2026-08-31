package blog.yuanyuan.yuanlive.live.realtime.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/** 为每次服务启动生成新的 epoch，确保重启前后的同地址进程不会混用实时队列。 */
@Configuration
public class RealtimeRoutingConfiguration {
    @Bean
    public RealtimeInstanceIdentity realtimeInstanceIdentity(
            @Value("${spring.cloud.nacos.discovery.ip:127.0.0.1}") String host,
            @Value("${server.port:8080}") String port) {
        return new RealtimeInstanceIdentity(host + ":" + port, UUID.randomUUID().toString());
    }
}
