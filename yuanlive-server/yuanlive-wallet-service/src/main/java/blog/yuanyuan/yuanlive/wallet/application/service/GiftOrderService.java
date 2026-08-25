package blog.yuanyuan.yuanlive.wallet.application.service;

import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderCommand;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderResult;

public interface GiftOrderService {

    GiftOrderResult sendGift(Long senderId, GiftOrderCommand command);
}
