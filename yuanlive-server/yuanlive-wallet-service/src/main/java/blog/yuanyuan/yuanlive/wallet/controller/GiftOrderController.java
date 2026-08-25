package blog.yuanyuan.yuanlive.wallet.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderCommand;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.service.GiftOrderService;
import blog.yuanyuan.yuanlive.wallet.controller.dto.CreateGiftOrderRequest;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gift-orders")
@RequiredArgsConstructor
public class GiftOrderController {

    private final GiftOrderService giftOrderService;

    @PostMapping
    public Result<GiftOrderResult> sendGift(@RequestBody @Valid CreateGiftOrderRequest request) {
        GiftOrderCommand command = new GiftOrderCommand(
                request.requestId(), request.roomId(), request.giftId(), request.count());
        return Result.success(giftOrderService.sendGift(StpUtil.getLoginIdAsLong(), command));
    }
}
