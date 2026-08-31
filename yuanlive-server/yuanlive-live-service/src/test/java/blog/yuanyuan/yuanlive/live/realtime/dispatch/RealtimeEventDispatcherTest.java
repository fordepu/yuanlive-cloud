package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.routing.AppConnectionRoute;
import blog.yuanyuan.yuanlive.live.realtime.routing.ConnectionRouteRegistry;
import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RealtimeEventDispatcherTest {
    private final ConnectionRouteRegistry routes = mock(ConnectionRouteRegistry.class);
    private final LocalRealtimeEventDelivery local = mock(LocalRealtimeEventDelivery.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final RealtimeInstanceIdentity localIdentity = new RealtimeInstanceIdentity("live-a:8080", "epoch-a");
    private final RealtimeEventDispatcher dispatcher = new RealtimeEventDispatcher(routes, local, rabbit, localIdentity);

    @Test
    void sendsSingleInstanceRoomEventLocallyWithoutRabbitMq() {
        when(routes.resolveRoomInstances("room-1")).thenReturn(List.of(localIdentity));

        dispatcher.dispatchToRoom("room-1", roomEvent());

        verify(local).deliver(any(InstanceDispatchMessage.class));
        verifyNoInteractions(rabbit);
    }

    @Test
    void sendsRoomEventOnlyToResolvedRemoteInstanceQueue() {
        RealtimeInstanceIdentity remote = new RealtimeInstanceIdentity("live-b:8080", "epoch-b");
        when(routes.resolveRoomInstances("room-1")).thenReturn(List.of(remote));

        dispatcher.dispatchToRoom("room-1", roomEvent());

        verify(rabbit).convertAndSend(eq(RealtimeDispatchTopology.EXCHANGE), eq(RealtimeDispatchTopology.queueName(remote)),
                any(InstanceDispatchMessage.class));
        verifyNoInteractions(local);
    }

    @Test
    void sendsAppEventToOnlyTheResolvedConnection() {
        AppConnectionRoute route = new AppConnectionRoute("live-b:8080", "epoch-b", "connection-b");
        when(routes.resolveApp(1001L)).thenReturn(Optional.of(route));

        dispatcher.dispatchToApp(1001L, appEvent());

        verify(rabbit).convertAndSend(eq(RealtimeDispatchTopology.EXCHANGE),
                eq(RealtimeDispatchTopology.queueName(new RealtimeInstanceIdentity("live-b:8080", "epoch-b"))),
                any(InstanceDispatchMessage.class));
    }

    @Test
    void refreshesAppRouteAndRetriesOnceWhenLocalDeliveryIsStale() {
        AppConnectionRoute oldRoute = new AppConnectionRoute("live-a:8080", "epoch-a", "connection-old");
        AppConnectionRoute newRoute = new AppConnectionRoute("live-b:8080", "epoch-b", "connection-new");
        when(routes.resolveApp(1001L)).thenReturn(Optional.of(oldRoute), Optional.of(newRoute));
        when(local.deliver(any(InstanceDispatchMessage.class))).thenReturn(RealtimeDeliveryStatus.ROUTE_STALE);

        dispatcher.dispatchToApp(1001L, appEvent());

        verify(rabbit).convertAndSend(eq(RealtimeDispatchTopology.EXCHANGE),
                eq(RealtimeDispatchTopology.queueName(new RealtimeInstanceIdentity("live-b:8080", "epoch-b"))),
                any(InstanceDispatchMessage.class));
    }

    private static RealtimeEvent roomEvent() {
        return new RealtimeEvent("event-room-1", 1L, ConnectionScope.ROOM, "room-1", "CHAT", 1L,
                JsonNodeFactory.instance.objectNode());
    }

    private static RealtimeEvent appEvent() {
        return new RealtimeEvent("event-app-1", null, ConnectionScope.APP, null, "NOTICE", 1L,
                JsonNodeFactory.instance.objectNode());
    }
}
