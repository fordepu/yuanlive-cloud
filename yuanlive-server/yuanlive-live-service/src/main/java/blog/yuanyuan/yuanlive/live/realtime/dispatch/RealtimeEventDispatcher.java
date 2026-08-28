package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.routing.AppConnectionRoute;
import blog.yuanyuan.yuanlive.live.realtime.routing.ConnectionRouteRegistry;
import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 根据 Redis 路由目录选择本机直发或实例专属队列。 */
@Component
public class RealtimeEventDispatcher {
    private final ConnectionRouteRegistry routes;
    private final LocalRealtimeEventDelivery localDelivery;
    private final RabbitTemplate rabbitTemplate;
    private final RealtimeInstanceIdentity identity;

    public RealtimeEventDispatcher(
            ConnectionRouteRegistry routes,
            LocalRealtimeEventDelivery localDelivery,
            RabbitTemplate rabbitTemplate,
            RealtimeInstanceIdentity identity) {
        this.routes = routes;
        this.localDelivery = localDelivery;
        this.rabbitTemplate = rabbitTemplate;
        this.identity = identity;
    }

    public void dispatchToApp(Long userId, RealtimeEvent event) {
        routes.resolveApp(userId).ifPresent(route -> dispatch(
                new RealtimeInstanceIdentity(route.instanceId(), route.epoch()),
                new InstanceDispatchMessage(route.instanceId(), route.epoch(), userId, route.connectionId(), event)));
    }

    public void dispatchToRoom(String roomId, RealtimeEvent event) {
        routes.resolveRoomInstances(roomId).forEach(target -> dispatch(
                target,
                new InstanceDispatchMessage(target.instanceId(), target.epoch(), null, null, event)));
    }

    private void dispatch(RealtimeInstanceIdentity target, InstanceDispatchMessage message) {
        if (identity.equals(target)) {
            localDelivery.deliver(message);
            return;
        }
        rabbitTemplate.convertAndSend(RealtimeDispatchTopology.EXCHANGE, RealtimeDispatchTopology.queueName(target), message);
    }
}
