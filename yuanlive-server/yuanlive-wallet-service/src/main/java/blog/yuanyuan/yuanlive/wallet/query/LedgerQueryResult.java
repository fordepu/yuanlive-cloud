package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

public record LedgerQueryResult(@JsonSerialize(using = ToStringSerializer.class) Long id, String ledgerNo, String businessType, String businessNo,
                                String direction, Long amount, Long balanceAfter,
                                String status, LocalDateTime createTime) {
    public static LedgerQueryResult from(WalletLedger ledger) {
        return new LedgerQueryResult(ledger.getId(), ledger.getLedgerNo(), ledger.getBusinessType(),
                ledger.getBusinessNo(), ledger.getDirection(), ledger.getAmount(), ledger.getBalanceAfter(),
                ledger.getStatus(), ledger.getCreateTime());
    }
}
