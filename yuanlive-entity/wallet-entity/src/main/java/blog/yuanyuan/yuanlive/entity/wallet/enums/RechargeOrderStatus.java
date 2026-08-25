package blog.yuanyuan.yuanlive.entity.wallet.enums;

/**
 * 充值订单状态使用稳定字符串落库，避免展示文案或枚举序号成为持久化协议。
 */
public enum RechargeOrderStatus {
    PENDING_PAYMENT,
    PAID,
    CLOSED
}
