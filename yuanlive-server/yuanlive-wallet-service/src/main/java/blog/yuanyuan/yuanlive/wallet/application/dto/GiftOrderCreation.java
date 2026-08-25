package blog.yuanyuan.yuanlive.wallet.application.dto;

public record GiftOrderCreation(
        String requestId,
        Long roomId,
        Long anchorId,
        Long giftId,
        String giftCode,
        String giftName,
        String giftIcon,
        Integer giftCount,
        Long unitCoinAmount,
        Long coinAmount,
        Long exchangeRate,
        Integer platformRate,
        Long platformCoinAmount,
        Long anchorIncomeAmount,
        String settlementRuleVersion) {
}
