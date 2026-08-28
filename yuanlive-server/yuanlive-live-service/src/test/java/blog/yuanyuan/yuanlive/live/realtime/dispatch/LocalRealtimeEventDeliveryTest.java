package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalRealtimeEventDeliveryTest {
    private final RealtimeInstanceIdentity identity = new RealtimeInstanceIdentity("live-a", "epoch-a");
    private final SessionManager sessionManager = mock(SessionManager.class);
    private final LocalRealtimeEventDelivery delivery = new LocalRealtimeEventDelivery(sessionManager, identity, new ObjectMapper());

    @Test
    void deliversApplicationEventOnlyToMatchingCurrentConnection() {
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        Attribute<String> connectionId = mock(Attribute.class);
        when(sessionManager.getUserChannel(1001L)).thenReturn(channel);
        when(channel.attr(SessionManager.KEY_CONNECTION_ID)).thenReturn(connectionId);
        when(connectionId.get()).thenReturn("connection-1");
        when(channel.isActive()).thenReturn(true);

        RealtimeDeliveryStatus result = delivery.deliver(new InstanceDispatchMessage(
                "live-a", "epoch-a", 1001L, "connection-1", event(ConnectionScope.APP, null)));

        assertEquals(RealtimeDeliveryStatus.DELIVERED, result);
        verify(channel).writeAndFlush(any(TextWebSocketFrame.class));
    }

    @Test
    void rejectsStaleTargetBeforeWritingAnyChannel() {
        RealtimeDeliveryStatus result = delivery.deliver(new InstanceDispatchMessage(
                "live-b", "epoch-b", 1001L, "connection-1", event(ConnectionScope.APP, null)));

        assertEquals(RealtimeDeliveryStatus.ROUTE_STALE, result);
    }

    @Test
    void broadcastsRoomEventToLocalChannelGroup() {
        ChannelGroup group = mock(ChannelGroup.class);
        when(group.isEmpty()).thenReturn(false);
        when(sessionManager.getRoomChannels("room-1")).thenReturn(group);

        RealtimeDeliveryStatus result = delivery.deliver(new InstanceDispatchMessage(
                "live-a", "epoch-a", null, null, event(ConnectionScope.ROOM, "room-1")));

        assertEquals(RealtimeDeliveryStatus.DELIVERED, result);
        verify(group).writeAndFlush(any(TextWebSocketFrame.class));
    }

    private static RealtimeEvent event(ConnectionScope scope, String roomId) {
        return new RealtimeEvent("event-1", scope == ConnectionScope.ROOM ? 1L : null, scope, roomId,
                "CHAT", 1L, JsonNodeFactory.instance.objectNode().put("content", "hello"));
    }
}
