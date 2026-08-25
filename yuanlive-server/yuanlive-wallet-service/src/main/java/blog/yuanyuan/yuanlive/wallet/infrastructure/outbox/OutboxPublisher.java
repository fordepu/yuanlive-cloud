package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService outboxClaimService;
    private final OutboxStatusService outboxStatusService;
    private final OutboxPublishGateway outboxPublishGateway;

    public OutboxPublisher(OutboxClaimService outboxClaimService,
                           OutboxStatusService outboxStatusService,
                           OutboxPublishGateway outboxPublishGateway) {
        this.outboxClaimService = outboxClaimService;
        this.outboxStatusService = outboxStatusService;
        this.outboxPublishGateway = outboxPublishGateway;
    }

    @Scheduled(
            initialDelayString = "${wallet.outbox.publish-interval-ms:1000}",
            fixedDelayString = "${wallet.outbox.publish-interval-ms:1000}")
    public void publishAvailable() {
        for (OutboxClaim claim : outboxClaimService.claimAvailable()) {
            try {
                outboxPublishGateway.publish(claim);
            } catch (RuntimeException exception) {
                // 单条同步发送异常只推进该事件重试，不能阻断同批其他已领取事实的投递。
                outboxStatusService.markFailed(
                        claim.eventId(), claim.leaseOwner(), "发送异常: " + rootMessage(exception));
                log.warn("Outbox事件同步发送失败, eventId={}", claim.eventId(), exception);
            }
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
