package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

public record OutboxClaim(String eventId, String eventType, String payload, String leaseOwner) {
}
