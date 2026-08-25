package blog.yuanyuan.yuanlive.live.domain.dto;

/**
 * 钱包 Outbox 已提交的 gift.delivered 事实；字段必须与钱包发布负载一一对应。
 */
public record GiftDeliveredEvent(
        String eventId,
        String orderNo,
        Long roomId,
        Long senderId,
        Long anchorId,
        Long giftId,
        String giftCode,
        String giftName,
        String giftIcon,
        Long giftCount,
        Long unitCoinAmount,
        Long coinAmount,
        Long exchangeRate,
        Long platformRate,
        Long platformCoinAmount,
        Long anchorIncomeAmount,
        String settlementRuleVersion) {
}
