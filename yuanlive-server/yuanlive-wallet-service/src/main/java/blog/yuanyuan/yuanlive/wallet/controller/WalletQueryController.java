package blog.yuanyuan.yuanlive.wallet.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.wallet.query.GiftOrderQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.ReconciliationReport;
import blog.yuanyuan.yuanlive.wallet.query.WalletQueryService;
import blog.yuanyuan.yuanlive.wallet.query.CursorPage;
import blog.yuanyuan.yuanlive.wallet.query.LedgerQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.IncomeQueryResult;
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
    public Result<GiftOrderQueryResult> getGiftOrder(@PathVariable String orderNo) {
        return Result.success(walletQueryService.getGiftOrder(orderNo, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/ledgers")
    public Result<CursorPage<LedgerQueryResult>> listLedgers(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(walletQueryService.listLedgers(StpUtil.getLoginIdAsLong(), beforeId, limit));
    }

    @GetMapping("/incomes")
    public Result<CursorPage<IncomeQueryResult>> listIncomes(
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.success(walletQueryService.listIncomes(StpUtil.getLoginIdAsLong(), beforeId, limit));
    }

    /** 对账只读报告，不做补账、冲正或状态修复。 */
    @GetMapping("/reconciliation/gift-orders/{orderNo}")
    @SaCheckRole("ADMIN")
    public Result<ReconciliationReport> reconcile(@PathVariable String orderNo) {
        return Result.success(walletQueryService.reconcileGiftOrder(orderNo));
    }
}
