package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.AnchorIncome;
import java.time.LocalDateTime;

public record IncomeQueryResult(Long id, String incomeNo, String giftOrderNo, Long anchorId,
                                Long grossAmount, Long platformFee, Long incomeAmount,
                                String status, LocalDateTime createTime) {
    public static IncomeQueryResult from(AnchorIncome income) {
        return new IncomeQueryResult(income.getId(), income.getIncomeNo(), income.getGiftOrderNo(), income.getAnchorId(),
                income.getGrossAmount(), income.getPlatformFee(), income.getIncomeAmount(), income.getStatus(), income.getCreateTime());
    }
}
