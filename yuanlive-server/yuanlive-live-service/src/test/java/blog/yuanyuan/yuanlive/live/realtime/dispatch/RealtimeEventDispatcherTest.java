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
    private final LocalRealtimeEventDelivery localDelivery = mock(LocalRealtimeEventDelivery.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final RealtimeInstanceIdentity identity = new RealtimeInstanceIdentity("live-a", "epoch-a");
    private final RealtimeEventDispatcher dispatcher = new RealtimeEventDispatcher(routes, localDelivery, rabbitTemplate, identity);

    @Test
    void broadcastsSingleInstanceRoomLocallyWithoutRabbitMq() {
        when(routes.resolveRoomInstances("room-1")).thenReturn(List.of(identity));

        dispatcher.dispatchToRoom("room-1", roomEvent());

        verify(localDelivery).deliver(any(InstanceDispatchMessage.class));
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void sendsRoomEventOnlyToResolvedRemoteInstanceQueue() {
        RealtimeInstanceIdentity remote = new RealtimeInstanceIdentity("live-b", "epoch-b");
        when(routes.resolveRoomInstances("room-1")).thenReturn(List.of(remote));

        dispatcher.dispatchToRoom("room-1", roomEvent());

        verify(rabbitTemplate).convertAndSend(eq(RealtimeDispatchTopology.EXCHANGE),
                eq(RealtimeDispatchTopology.queueName(remote)), any(InstanceDispatchMessage.class));
        verifyNoInteractions(localDelivery);
    }

    @Test
    void sendsApplicationEventToTheResolvedConnectionOnly() {
        AppConnectionRoute route = new AppConnectionRoute("live-b", "epoch-b", "connection-1");
        when(routes.resolveApp(1001L)).thenReturn(Optional.of(route));

        dispatcher.dispatchToApp(1001L, appEvent());

        verify(rabbitTemplate).convertAndSend(eq(RealtimeDispatchTopology.EXCHANGE),
                eq(RealtimeDispatchTopology.queueName(new RealtimeInstanceIdentity("live-b", "epoch-b"))),
                any(InstanceDispatchMessage.class));
    }

    private static RealtimeEvent roomEvent() {
        return new RealtimeEvent("event-1", 1L, ConnectionScope.ROOM, "room-1", "CHAT", 1L,
                JsonNodeFactory.instance.objectNode());
    }

    private static RealtimeEvent appEvent() {
        return new RealtimeEvent("event-1", null, ConnectionScope.APP, null, "NOTICE", 1L,
                JsonNodeFactory.instance.objectNode());
    }
}
