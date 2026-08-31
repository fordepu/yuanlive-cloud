package blog.yuanyuan.yuanlive.live.realtime.dispatch;

/** 本机投递结果；路由已过期时由发送方刷新 Redis 目录。 */
public enum RealtimeDeliveryStatus {
    DELIVERED,
    ROUTE_STALE,
    DUPLICATE
}
