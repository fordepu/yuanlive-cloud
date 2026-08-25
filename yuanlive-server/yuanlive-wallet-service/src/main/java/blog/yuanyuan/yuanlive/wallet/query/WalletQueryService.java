package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletBalanceQueryResult;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletOrderQueryResult;

public interface WalletQueryService {
    GiftOrderQueryResult getGiftOrder(String orderNo, Long requesterId);
    WalletBalanceQueryResult getBalanceForInternal(Long userId);
    WalletOrderQueryResult getOrderForInternal(String orderNo);

    CursorPage<LedgerQueryResult> listLedgers(Long userId, Long beforeId, int limit);

    CursorPage<IncomeQueryResult> listIncomes(Long anchorId, Long beforeId, int limit);
    ReconciliationReport reconcileGiftOrder(String orderNo);
}
