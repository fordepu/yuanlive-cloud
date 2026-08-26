package blog.yuanyuan.yuanlive.live.infrastructure.metrics;

import blog.yuanyuan.yuanlive.live.config.GiftDeliveryRabbitTopology;
import blog.yuanyuan.yuanlive.live.listener.LikeStatsConsumer;
import blog.yuanyuan.yuanlive.live.mapper.LiveInboxEventMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class LiveStageOneMetricsCollector {
    private final LiveInboxEventMapper mapper;
    private final RabbitAdmin rabbitAdmin;
    private final long staleSeconds;
    private final AtomicLong staleInbox = new AtomicLong();
    private final AtomicLong giftBacklog = new AtomicLong();
    private final AtomicLong giftDead = new AtomicLong();
    private final AtomicLong likeBacklog = new AtomicLong();

    public LiveStageOneMetricsCollector(LiveInboxEventMapper mapper, ObjectProvider<RabbitAdmin> rabbitAdminProvider,
                                        MeterRegistry registry,
                                        @Value("${live.metrics.inbox-stale-seconds:60}") long staleSeconds) {
        this.mapper = mapper;
        this.rabbitAdmin = rabbitAdminProvider.getIfAvailable();
        this.staleSeconds = staleSeconds;
        registry.gauge("yuanlive.inbox.stale.processing", staleInbox);
        registry.gauge("yuanlive.rabbit.queue.messages", java.util.List.of(Tag.of("queue", GiftDeliveryRabbitTopology.GIFT_DELIVERED_QUEUE)), giftBacklog);
        registry.gauge("yuanlive.rabbit.queue.messages", java.util.List.of(Tag.of("queue", GiftDeliveryRabbitTopology.GIFT_DELIVERED_DEAD_QUEUE)), giftDead);
        registry.gauge("yuanlive.rabbit.queue.messages", java.util.List.of(Tag.of("queue", LikeStatsConsumer.QUEUE)), likeBacklog);
    }

    @Scheduled(fixedDelayString = "${live.metrics.refresh-ms:15000}")
    public void refresh() {
        staleInbox.set(mapper.countStaleProcessing(staleSeconds));
        if (rabbitAdmin != null) {
            giftBacklog.set(queueMessages(GiftDeliveryRabbitTopology.GIFT_DELIVERED_QUEUE));
            giftDead.set(queueMessages(GiftDeliveryRabbitTopology.GIFT_DELIVERED_DEAD_QUEUE));
            likeBacklog.set(queueMessages(LikeStatsConsumer.QUEUE));
        }
    }

    private long queueMessages(String queue) {
        Properties properties = rabbitAdmin.getQueueProperties(queue);
        Object count = properties == null ? null : properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
        return count instanceof Number number ? number.longValue() : 0L;
    }
}
