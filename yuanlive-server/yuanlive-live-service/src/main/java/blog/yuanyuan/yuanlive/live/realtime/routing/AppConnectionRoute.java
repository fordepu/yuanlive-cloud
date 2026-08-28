package blog.yuanyuan.yuanlive.live.realtime.routing;

/** 用户应用级主连接的跨实例路由记录。 */
public record AppConnectionRoute(String instanceId, String epoch, String connectionId) {
}
