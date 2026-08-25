package blog.yuanyuan.yuanlive.wallet.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.wallet.application.dto.WalletAccountResult;
import blog.yuanyuan.yuanlive.wallet.application.service.RechargeService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class WalletAccountController {

    private final RechargeService rechargeService;

    @GetMapping("/me")
    public Result<WalletAccountResult> getMyAccount() {
        return Result.success(rechargeService.getAccount(StpUtil.getLoginIdAsLong()));
    }
}
