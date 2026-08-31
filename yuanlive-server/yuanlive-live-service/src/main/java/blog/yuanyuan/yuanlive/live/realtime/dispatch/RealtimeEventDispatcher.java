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
        routes.resolveApp(userId).ifPresent(route -> dispatch(new RealtimeInstanceIdentity(route.instanceId(), route.epoch()),
                new InstanceDispatchMessage(route.instanceId(), route.epoch(), userId, route.connectionId(), event)));
    }

    public void dispatchToRoom(String roomId, RealtimeEvent event) {
        routes.resolveRoomInstances(roomId).forEach(target -> dispatch(target,
                new InstanceDispatchMessage(target.instanceId(), target.epoch(), null, null, event)));
    }

    private void dispatch(RealtimeInstanceIdentity target, InstanceDispatchMessage message) {
        if (identity.equals(target)) {
            localDelivery.deliver(message);
            return;
        }
        rabbit.convertAndSend(RealtimeDispatchTopology.EXCHANGE, RealtimeDispatchTopology.queueName(target), message);
    }
}
