package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.routing.AppConnectionRoute;
import blog.yuanyuan.yuanlive.live.realtime.routing.ConnectionRouteRegistry;
import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 根据 Redis 权威路由选择本机直发或目标实例专属队列。 */
@Component
public class RealtimeEventDispatcher {
    private final ConnectionRouteRegistry routes;
    private final LocalRealtimeEventDelivery localDelivery;
    private final RabbitTemplate rabbit;
    private final RealtimeInstanceIdentity identity;

    public RealtimeEventDispatcher(ConnectionRouteRegistry routes, LocalRealtimeEventDelivery localDelivery,
                                   RabbitTemplate rabbit, RealtimeInstanceIdentity identity) {
        this.routes = routes;
        this.localDelivery = localDelivery;
        this.rabbit = rabbit;
        this.identity = identity;
    }

    public void dispatchToApp(Long userId, RealtimeEvent event) {
        dispatchToApp(userId, event, 0);
    }

    public void dispatchToRoom(String roomId, RealtimeEvent event) {
        dispatchToRoom(roomId, event, 0);
    }

    private void dispatch(RealtimeInstanceIdentity target, InstanceDispatchMessage message) {
        if (identity.equals(target)) {
            if (localDelivery.deliver(message) == RealtimeDeliveryStatus.ROUTE_STALE) retry(message);
            return;
        }
        rabbit.convertAndSend(RealtimeDispatchTopology.EXCHANGE, RealtimeDispatchTopology.queueName(target), message);
    }

    /**
     * Redis 路由目录可能在连接断开、重连或实例重启的短窗口内滞后。
     * 仅使用原 eventId 重试一次，避免失效路由持续循环；ROOM 事件仍可由 seq 缓冲补拉。
     */
    public void retry(InstanceDispatchMessage staleMessage) {
        if (staleMessage.retryAttempt() >= 1) return;
        RealtimeEvent event = staleMessage.event();
        if (event.scope() == blog.yuanyuan.yuanlive.live.realtime.ConnectionScope.APP) {
            if (staleMessage.targetUserId() != null) dispatchToApp(staleMessage.targetUserId(), event, staleMessage.retryAttempt() + 1);
            return;
        }
        dispatchToRoom(event.roomId(), event, staleMessage.retryAttempt() + 1);
    }

    private void dispatchToApp(Long userId, RealtimeEvent event, int retryAttempt) {
        routes.resolveApp(userId).ifPresent(route -> dispatch(new RealtimeInstanceIdentity(route.instanceId(), route.epoch()),
                new InstanceDispatchMessage(route.instanceId(), route.epoch(), userId, route.connectionId(), event, retryAttempt)));
    }

    private void dispatchToRoom(String roomId, RealtimeEvent event, int retryAttempt) {
        routes.resolveRoomInstances(roomId).forEach(target -> dispatch(target,
                new InstanceDispatchMessage(target.instanceId(), target.epoch(), null, null, event, retryAttempt)));
    }
}
