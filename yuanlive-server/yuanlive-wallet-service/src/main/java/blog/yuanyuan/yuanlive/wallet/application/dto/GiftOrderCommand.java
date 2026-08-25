package blog.yuanyuan.yuanlive.wallet.application.dto;

public record GiftOrderCommand(String requestId, Long roomId, Long giftId, Integer count) {
}
