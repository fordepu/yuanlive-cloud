package blog.yuanyuan.yuanlive.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** 阶段 1 可靠消息链路的最小指标集合，具体告警阈值交由部署环境配置。 */
@Component
public class StageOneMetrics {
    private final Counter outboxPublishFailures;
    private final Counter outboxReturns;
    private final Counter inboxFailures;
    private final Counter giftDisplayPublishes;
    private final Counter rechargeNotifications;

    public StageOneMetrics(MeterRegistry registry) {
        outboxPublishFailures = registry.counter("yuanlive.outbox.publish.failures");
        outboxReturns = registry.counter("yuanlive.outbox.returns");
        inboxFailures = registry.counter("yuanlive.inbox.failures");
        giftDisplayPublishes = registry.counter("yuanlive.gift.display.publishes");
        rechargeNotifications = registry.counter("yuanlive.wallet.recharge.notifications");
    }

    public void outboxPublishFailed() { outboxPublishFailures.increment(); }
    public void outboxReturned() { outboxReturns.increment(); }
    public void inboxFailed() { inboxFailures.increment(); }
    public void giftDisplayPublished() { giftDisplayPublishes.increment(); }
    public void rechargeNotificationProcessed() { rechargeNotifications.increment(); }
}
