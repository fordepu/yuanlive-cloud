package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.util.Attribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalRealtimeEventDeliveryTest {
    private final SessionManager sessions = mock(SessionManager.class);
    private final LocalRealtimeEventDelivery delivery = new LocalRealtimeEventDelivery(
            sessions, new RealtimeInstanceIdentity("live-a:8080", "epoch-a"), new ObjectMapper());

    @Test
    void rejectsMessageForSameAddressButPreviousEpoch() {
        RealtimeDeliveryStatus result = delivery.deliver(message("epoch-old", roomEvent("event-1")));

        assertEquals(RealtimeDeliveryStatus.ROUTE_STALE, result);
    }

    @Test
    void suppressesDuplicateRoomEventIds() {
        ChannelGroup group = mock(ChannelGroup.class);
        when(group.isEmpty()).thenReturn(false);
        when(sessions.getRoomChannels("room-1")).thenReturn(group);

        assertEquals(RealtimeDeliveryStatus.DELIVERED, delivery.deliver(message("epoch-a", roomEvent("event-1"))));
        assertEquals(RealtimeDeliveryStatus.DUPLICATE, delivery.deliver(message("epoch-a", roomEvent("event-1"))));
        verify(group).writeAndFlush(any());
    }

    @Test
    void sendsAppEventOnlyWhenConnectionIdMatchesCurrentChannel() {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked") Attribute<String> connectionId = mock(Attribute.class);
        when(sessions.getUserChannel(1001L)).thenReturn(channel);
        when(channel.isActive()).thenReturn(true);
        when(channel.attr(SessionManager.KEY_CONNECTION_ID)).thenReturn(connectionId);
        when(connectionId.get()).thenReturn("connection-a");
        RealtimeEvent event = new RealtimeEvent("event-app", null, ConnectionScope.APP, null, "NOTICE", 1L,
                JsonNodeFactory.instance.objectNode());

        assertEquals(RealtimeDeliveryStatus.DELIVERED,
                delivery.deliver(new InstanceDispatchMessage("live-a:8080", "epoch-a", 1001L, "connection-a", event, 0)));
    }

    @Test
    void doesNotMarkStaleRouteAsDuplicateBeforeRetry() {
        RealtimeEvent event = roomEvent("event-retry");
        when(sessions.getRoomChannels("room-1")).thenReturn(null);
        assertEquals(RealtimeDeliveryStatus.ROUTE_STALE, delivery.deliver(message("epoch-a", event)));

        ChannelGroup group = mock(ChannelGroup.class);
        when(group.isEmpty()).thenReturn(false);
        when(sessions.getRoomChannels("room-1")).thenReturn(group);

        assertEquals(RealtimeDeliveryStatus.DELIVERED, delivery.deliver(message("epoch-a", event)));
    }

    private static InstanceDispatchMessage message(String epoch, RealtimeEvent event) {
        return new InstanceDispatchMessage("live-a:8080", epoch, null, null, event, 0);
    }

    private static RealtimeEvent roomEvent(String eventId) {
        return new RealtimeEvent(eventId, 1L, ConnectionScope.ROOM, "room-1", "CHAT", 1L,
                JsonNodeFactory.instance.objectNode());
    }
}
