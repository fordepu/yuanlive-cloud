package blog.yuanyuan.yuanlive.wallet.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletBalanceQueryResult;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletOrderQueryResult;
import blog.yuanyuan.yuanlive.wallet.query.WalletQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供服务间只读调用，禁止在此增加资金写入操作。 */
@RestController
@RequestMapping("/internal/wallet")
@RequiredArgsConstructor
public class InternalWalletQueryController {

    private final WalletQueryService walletQueryService;

    @GetMapping("/accounts/{userId}")
    public Result<WalletBalanceQueryResult> getBalance(@PathVariable Long userId) {
        return Result.success(walletQueryService.getBalanceForInternal(userId));
    }

    @GetMapping("/orders/{orderNo}")
    public Result<WalletOrderQueryResult> getOrder(@PathVariable String orderNo) {
        return Result.success(walletQueryService.getOrderForInternal(orderNo));
    }
}
