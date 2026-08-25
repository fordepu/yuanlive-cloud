package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

import blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final OutboxEventMapper outboxEventMapper;
    private final int batchSize;
    private final long leaseSeconds;

    public OutboxClaimService(OutboxEventMapper outboxEventMapper,
                              @Value("${wallet.outbox.batch-size:50}") int batchSize,
                              @Value("${wallet.outbox.lease-seconds:30}") long leaseSeconds) {
        this.outboxEventMapper = outboxEventMapper;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxClaim> claimAvailable() {
        List<OutboxEvent> events = outboxEventMapper.selectClaimableForUpdate(batchSize);
        List<OutboxClaim> claims = new ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            String leaseOwner = UUID.randomUUID().toString();
            outboxEventMapper.claim(event.getId(), leaseOwner, leaseSeconds);
            claims.add(new OutboxClaim(event.getEventId(), event.getEventType(), event.getPayload(), leaseOwner));
        }
        // 这里只领取并提交租约；RabbitMQ 发送由事务外的发布器执行，避免数据库锁跨越网络调用。
        return claims;
    }
}
