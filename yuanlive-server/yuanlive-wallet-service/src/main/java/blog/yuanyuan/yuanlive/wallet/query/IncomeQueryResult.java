package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.AnchorIncome;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

public record IncomeQueryResult(@JsonSerialize(using = ToStringSerializer.class) Long id, String incomeNo, String giftOrderNo,
                                @JsonSerialize(using = ToStringSerializer.class) Long anchorId,
                                Long grossAmount, Long platformFee, Long incomeAmount,
                                String status, LocalDateTime createTime) {
    public static IncomeQueryResult from(AnchorIncome income) {
        return new IncomeQueryResult(income.getId(), income.getIncomeNo(), income.getGiftOrderNo(), income.getAnchorId(),
                income.getGrossAmount(), income.getPlatformFee(), income.getIncomeAmount(), income.getStatus(), income.getCreateTime());
    }
}
