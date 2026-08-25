package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;

public record GiftOrderQueryResult(
        String orderNo, Long senderId, Long roomId, Long anchorId, Long giftId,
        Integer giftCount, Long coinAmount, Integer platformRate, Long platformCoinAmount,
        Long anchorIncomeAmount, String status) {
    public static GiftOrderQueryResult from(GiftOrder order) {
        return new GiftOrderQueryResult(order.getOrderNo(), order.getSenderId(), order.getRoomId(),
                order.getAnchorId(), order.getGiftId(), order.getGiftCount(), order.getCoinAmount(),
                order.getPlatformRate(), order.getPlatformCoinAmount(), order.getAnchorIncomeAmount(), order.getStatus());
    }
}
