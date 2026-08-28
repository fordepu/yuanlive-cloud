package blog.yuanyuan.yuanlive.live.realtime.routing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 当前 live-service 进程在实时路由目录中的身份。 */
@Component
public record RealtimeInstanceIdentity(String instanceId, String epoch) {

    @Autowired
    public RealtimeInstanceIdentity(
            @Value("${spring.cloud.nacos.discovery.instance-id:}") String configuredInstanceId,
            @Value("${spring.application.name:live-service}") String serviceName,
            @Value("${HOSTNAME:localhost}") String hostName,
            @Value("${yuanlive.netty.port:18081}") int nettyPort) {
        this(configuredInstanceId == null || configuredInstanceId.isBlank()
                        ? serviceName + ":" + hostName + ":" + nettyPort
                        : configuredInstanceId,
                UUID.randomUUID().toString());
    }

    public String memberValue() {
        return instanceId + "|" + epoch;
    }
}
