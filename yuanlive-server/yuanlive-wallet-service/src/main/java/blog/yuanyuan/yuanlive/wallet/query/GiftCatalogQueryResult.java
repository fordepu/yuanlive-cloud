package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftCatalog;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record GiftCatalogQueryResult(@JsonSerialize(using = ToStringSerializer.class) Long id, String code, String name, String icon,
                                     Long unitCoinAmount, String status) {
    public static GiftCatalogQueryResult from(GiftCatalog gift) {
        return new GiftCatalogQueryResult(gift.getId(), gift.getGiftCode(), gift.getGiftName(),
                gift.getGiftIcon(), gift.getCoinAmount(), gift.getStatus());
    }
}
