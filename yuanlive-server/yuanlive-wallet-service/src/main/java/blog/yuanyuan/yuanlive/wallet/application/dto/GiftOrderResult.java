package blog.yuanyuan.yuanlive.wallet.application.dto;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;

public record GiftOrderResult(
        String orderNo,
        String status,
        Long coinAmount,
        Long anchorIncomeAmount,
        Long availableCoin) {

    public static GiftOrderResult from(GiftOrder order, Long availableCoin) {
        return new GiftOrderResult(
                order.getOrderNo(),
                order.getStatus(),
                order.getCoinAmount(),
                order.getAnchorIncomeAmount(),
                availableCoin);
    }
}
