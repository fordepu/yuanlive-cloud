package blog.yuanyuan.yuanlive.wallet.infrastructure.metrics;

import blog.yuanyuan.yuanlive.wallet.listener.WalletRechargedConsumer;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WalletStageOneMetricsCollector {
    private final OutboxEventMapper mapper;
    private final RabbitAdmin rabbitAdmin;
    private final Map<String, AtomicLong> outbox = new ConcurrentHashMap<>();
    private final AtomicLong oldestAge = new AtomicLong();
    private final AtomicLong notificationBacklog = new AtomicLong();

    public WalletStageOneMetricsCollector(OutboxEventMapper mapper, ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                                         MeterRegistry registry) {
        this.mapper = mapper;
        this.rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        for (String status : new String[]{"NEW", "PUBLISHING", "RETRY_WAIT", "PUBLISHED", "DEAD"}) {
            AtomicLong value = new AtomicLong();
            outbox.put(status, value);
            registry.gauge("yuanlive.outbox.events", java.util.List.of(Tag.of("status", status)), value);
        }
        registry.gauge("yuanlive.outbox.oldest.pending.seconds", oldestAge);
        registry.gauge("yuanlive.rabbit.queue.messages", java.util.List.of(Tag.of("queue", WalletRechargedConsumer.QUEUE)), notificationBacklog);
    }

    @Scheduled(fixedDelayString = "${wallet.metrics.refresh-ms:15000}")
    public void refresh() {
        outbox.forEach((status, value) -> value.set(mapper.countByStatus(status)));
        oldestAge.set(mapper.oldestPendingAgeSeconds());
        if (rabbitAdmin != null) {
            notificationBacklog.set(queueMessages(WalletRechargedConsumer.QUEUE));
        }
    }

    private long queueMessages(String queue) {
        Properties properties = rabbitAdmin.getQueueProperties(queue);
        Object count = properties == null ? null : properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return count instanceof Number number ? number.longValue() : 0L;
    }
}
