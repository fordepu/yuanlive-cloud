package blog.yuanyuan.yuanlive.wallet.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateGiftOrderRequest(
        @NotBlank(message = "请求幂等键不能为空")
        @Size(max = 64, message = "请求幂等键长度不能超过 64")
        String requestId,
        @NotNull(message = "直播间不能为空")
        @Positive(message = "直播间格式错误")
        Long roomId,
        @NotNull(message = "礼物不能为空")
        @Positive(message = "礼物格式错误")
        Long giftId,
        @NotNull(message = "礼物数量不能为空")
        @Min(value = 1, message = "礼物数量不能小于 1")
        @Max(value = 99, message = "礼物数量不能大于 99")
        Integer count) {
}
