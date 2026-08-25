package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

import blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OutboxStatusService {

    private final OutboxEventMapper outboxEventMapper;
    private final int maxRetries;

    public OutboxStatusService(OutboxEventMapper outboxEventMapper,
                               @Value("${wallet.outbox.max-retries:8}") int maxRetries) {
        this.outboxEventMapper = outboxEventMapper;
        this.maxRetries = maxRetries;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markPublished(String eventId, String leaseOwner) {
        return outboxEventMapper.markPublished(eventId, leaseOwner) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(String eventId, String leaseOwner, String failureReason) {
        OutboxEvent event = outboxEventMapper.selectPublishingForUpdate(eventId, leaseOwner);
        if (event == null) {
            // Return、Confirm 和租约过期后的迟到回调可能并发到达；旧 owner 无权覆盖新一轮领取结果。
            return false;
        }
        // 当前租约的失败只递增一次；未达上限按指数退避，达到上限保留为 DEAD，等待人工审计后重放。
        int retryCount = event.getRetryCount() + 1;
        boolean dead = retryCount >= maxRetries;
        LocalDateTime nextRetryAt = dead ? null : LocalDateTime.now().plusSeconds(backoffSeconds(retryCount));
        String status = dead ? "DEAD" : "RETRY_WAIT";
        return outboxEventMapper.markFailed(
                eventId, leaseOwner, status, retryCount, nextRetryAt, normalizeFailureReason(failureReason)) == 1;
    }

    private long backoffSeconds(int retryCount) {
        int exponent = Math.max(0, Math.min(retryCount - 1, 6));
        return Math.min(60L, 1L << exponent);
    }

    private String normalizeFailureReason(String failureReason) {
        if (failureReason == null || failureReason.isBlank()) {
            return "未知发布失败";
        }
        return failureReason;
    }
}
