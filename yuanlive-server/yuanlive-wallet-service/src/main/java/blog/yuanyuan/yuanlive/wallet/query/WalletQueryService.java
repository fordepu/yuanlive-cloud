package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletBalanceQueryResult;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletOrderQueryResult;
import java.util.List;

public interface WalletQueryService {
    GiftOrderQueryResult getGiftOrder(String orderNo, Long requesterId);
    AdminGiftOrderQueryResult getGiftOrderForAdmin(String orderNo);
    List<GiftCatalogQueryResult> listGiftCatalog();
    CursorPage<AdminGiftOrderQueryResult> listGiftOrdersForAdmin(
            String orderNo, Long senderId, Long anchorId, String status, Long beforeId, int limit);
    WalletBalanceQueryResult getBalanceForInternal(Long userId);
    WalletOrderQueryResult getOrderForInternal(String orderNo);

    CursorPage<LedgerQueryResult> listLedgers(Long userId, Long beforeId, int limit);

    CursorPage<IncomeQueryResult> listIncomes(Long anchorId, Long beforeId, int limit);
    ReconciliationReport reconcileGiftOrder(String orderNo);
}
