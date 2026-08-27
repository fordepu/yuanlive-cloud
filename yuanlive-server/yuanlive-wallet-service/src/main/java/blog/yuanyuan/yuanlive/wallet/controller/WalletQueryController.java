package blog.yuanyuan.yuanlive.wallet.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.wallet.query.GiftOrderQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.ReconciliationReport;
import blog.yuanyuan.yuanlive.wallet.query.WalletQueryService;
import blog.yuanyuan.yuanlive.wallet.query.CursorPage;
import blog.yuanyuan.yuanlive.wallet.query.LedgerQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.IncomeQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.GiftCatalogQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.AdminGiftOrderQueryResult;
import java.util.List;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class WalletQueryController {

    private final WalletQueryService walletQueryService;

    @GetMapping("/gift-orders/{orderNo}")
    public Result<GiftOrderQueryResult> getGiftOrder(@PathVariable("orderNo") String orderNo) {
        return Result.success(walletQueryService.getGiftOrder(orderNo, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/gifts")
    public Result<List<GiftCatalogQueryResult>> listGiftCatalog() {
        return Result.success(walletQueryService.listGiftCatalog());
    }

    @GetMapping("/admin/gift-orders")
    @SaCheckRole("ADMIN")
    public Result<CursorPage<AdminGiftOrderQueryResult>> listGiftOrdersForAdmin(
            @RequestParam(value = "orderNo", required = false) String orderNo,
            @RequestParam(value = "senderId", required = false) Long senderId,
            @RequestParam(value = "anchorId", required = false) Long anchorId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(walletQueryService.listGiftOrdersForAdmin(
                orderNo, senderId, anchorId, status, beforeId, limit));
    }

    @GetMapping("/admin/gift-orders/{orderNo}")
    @SaCheckRole("ADMIN")
    public Result<AdminGiftOrderQueryResult> getGiftOrderForAdmin(@PathVariable("orderNo") String orderNo) {
        return Result.success(walletQueryService.getGiftOrderForAdmin(orderNo));
    }

    @GetMapping("/ledgers")
    public Result<CursorPage<LedgerQueryResult>> listLedgers(
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(walletQueryService.listLedgers(StpUtil.getLoginIdAsLong(), beforeId, limit));
    }

    @GetMapping("/incomes")
    public Result<CursorPage<IncomeQueryResult>> listIncomes(
            @RequestParam(value = "beforeId", required = false) Long beforeId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return Result.success(walletQueryService.listIncomes(StpUtil.getLoginIdAsLong(), beforeId, limit));
    }

    /** 对账只读报告，不做补账、冲正或状态修复。 */
    @GetMapping("/reconciliation/gift-orders/{orderNo}")
    @SaCheckRole("ADMIN")
    public Result<ReconciliationReport> reconcile(@PathVariable("orderNo") String orderNo) {
        return Result.success(walletQueryService.reconcileGiftOrder(orderNo));
    }
}
