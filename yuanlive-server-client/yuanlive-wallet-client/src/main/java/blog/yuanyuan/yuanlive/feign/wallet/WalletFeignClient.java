package blog.yuanyuan.yuanlive.feign.wallet;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletBalanceQueryResult;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletOrderQueryResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 跨服务钱包只读查询契约。当前钱包服务尚未提供对应内部查询端点，后续实现只能补齐同名 GET 读取能力，
 * 不得借此客户端增加余额变更、订单创建或流水写入接口。
 */
@FeignClient(value = "wallet-service")
public interface WalletFeignClient {

    @GetMapping("/internal/wallet/accounts/{userId}")
    Result<WalletBalanceQueryResult> getBalance(@PathVariable("userId") Long userId);

    @GetMapping("/internal/wallet/orders/{orderNo}")
    Result<WalletOrderQueryResult> getOrder(@PathVariable("orderNo") String orderNo);
}
