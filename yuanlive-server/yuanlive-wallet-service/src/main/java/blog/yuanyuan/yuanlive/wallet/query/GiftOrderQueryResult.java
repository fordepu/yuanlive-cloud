package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record GiftOrderQueryResult(
        String orderNo, @JsonSerialize(using = ToStringSerializer.class) Long senderId,
        @JsonSerialize(using = ToStringSerializer.class) Long roomId,
        @JsonSerialize(using = ToStringSerializer.class) Long anchorId,
        @JsonSerialize(using = ToStringSerializer.class) Long giftId,
        Integer giftCount, Long coinAmount, Integer platformRate, Long platformCoinAmount,
        Long anchorIncomeAmount, String status) {
    public static GiftOrderQueryResult from(GiftOrder order) {
        return new GiftOrderQueryResult(order.getOrderNo(), order.getSenderId(), order.getRoomId(),
                order.getAnchorId(), order.getGiftId(), order.getGiftCount(), order.getCoinAmount(),
                order.getPlatformRate(), order.getPlatformCoinAmount(), order.getAnchorIncomeAmount(), order.getStatus());
    }
}
