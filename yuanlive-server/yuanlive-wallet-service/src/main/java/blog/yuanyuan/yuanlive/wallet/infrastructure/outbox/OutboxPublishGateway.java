package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

@FunctionalInterface
public interface OutboxPublishGateway {
    void publish(OutboxClaim claim);
}
