package blog.yuanyuan.yuanlive.wallet.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateRechargeOrderRequest(
        @NotNull(message = "充值金币不能为空")
        @Positive(message = "充值金币必须大于 0")
        Long coinAmount) {
}
