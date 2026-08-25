package blog.yuanyuan.yuanlive.wallet.application.service;

import blog.yuanyuan.yuanlive.wallet.application.dto.RechargeOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.dto.WalletAccountResult;

public interface RechargeService {

    RechargeOrderResult createRechargeOrder(Long userId, Long coinAmount);

    RechargeOrderResult simulatePaid(Long userId, String orderNo);

    WalletAccountResult getAccount(Long userId);
}
