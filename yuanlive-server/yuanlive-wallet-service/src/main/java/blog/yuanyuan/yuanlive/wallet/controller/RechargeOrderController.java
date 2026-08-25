package blog.yuanyuan.yuanlive.wallet.controller;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.wallet.application.dto.RechargeOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.service.RechargeService;
import blog.yuanyuan.yuanlive.wallet.controller.dto.CreateRechargeOrderRequest;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/recharge-orders")
@RequiredArgsConstructor
public class RechargeOrderController {

    private final RechargeService rechargeService;

    @Value("${wallet.recharge.simulation-enabled:false}")
    private boolean simulationEnabled;

    @PostMapping
    public Result<RechargeOrderResult> createRechargeOrder(@RequestBody @Valid CreateRechargeOrderRequest request) {
        return Result.success(rechargeService.createRechargeOrder(StpUtil.getLoginIdAsLong(), request.coinAmount()));
    }

    @PostMapping("/{orderNo}/simulate-paid")
    public Result<RechargeOrderResult> simulatePaid(@PathVariable String orderNo) {
        if (!simulationEnabled) {
            // 模拟回调只允许开发或测试环境开启，生产环境必须由真实支付渠道回调驱动。
            throw new ApiException("模拟充值未启用");
        }
        return Result.success(rechargeService.simulatePaid(StpUtil.getLoginIdAsLong(), orderNo));
    }
}
