package blog.yuanyuan.yuanlive.live.realtime.dispatch;

/** 实例本机投递结果，发送端据此决定是否刷新 Redis 路由。 */
public enum RealtimeDeliveryStatus {
    DELIVERED,
    ROUTE_STALE
}
