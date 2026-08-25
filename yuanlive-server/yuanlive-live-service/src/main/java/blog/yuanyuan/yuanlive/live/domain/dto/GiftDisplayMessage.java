package blog.yuanyuan.yuanlive.live.domain.dto;

/**
 * 发送给实时展示层的礼物消息，客户端可按 eventId 去重重复投递。
 */
public record GiftDisplayMessage(
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

    public static GiftDisplayMessage from(GiftDeliveredEvent event) {
        return new GiftDisplayMessage(
                event.eventId(), event.orderNo(), event.roomId(), event.senderId(), event.anchorId(), event.giftId(),
                event.giftCode(), event.giftName(), event.giftIcon(), event.giftCount(), event.unitCoinAmount(),
                event.coinAmount(), event.exchangeRate(), event.platformRate(), event.platformCoinAmount(),
                event.anchorIncomeAmount(), event.settlementRuleVersion());
    }
}
