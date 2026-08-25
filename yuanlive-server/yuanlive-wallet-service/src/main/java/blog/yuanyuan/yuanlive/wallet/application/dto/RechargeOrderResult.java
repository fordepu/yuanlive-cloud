package blog.yuanyuan.yuanlive.wallet.application.dto;

/**
 * 充值订单创建和模拟支付的统一结果，余额仅在支付成功后有值。
 */
public record RechargeOrderResult(String orderNo, String status, Long availableCoin) {
}
