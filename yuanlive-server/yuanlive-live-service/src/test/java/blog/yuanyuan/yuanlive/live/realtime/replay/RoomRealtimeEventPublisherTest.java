package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.message.notification.EventMessage;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.dispatch.RealtimeEventDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomRealtimeEventPublisherTest {
    @Test
    void appendsMessageBeforeDispatchingItsRoomEvent() {
        RoomEventBuffer buffer = mock(RoomEventBuffer.class);
        RealtimeEventDispatcher dispatcher = mock(RealtimeEventDispatcher.class);
        RoomRealtimeEventPublisher publisher = new RoomRealtimeEventPublisher(buffer, dispatcher, new ObjectMapper());
        RealtimeEvent event = new RealtimeEvent("event-1", 9L, ConnectionScope.ROOM, "room-1", "EVENT", 100L,
                JsonNodeFactory.instance.objectNode());
        when(buffer.append(eq("room-1"), eq("event-1"), eq("EVENT"), eq(100L), any())).thenReturn(event);
        EventMessage message = EventMessage.builder().roomId("room-1").msgId("event-1").timestamp(100L).build();

        RealtimeEvent result = publisher.publish(message);

        assertSame(event, result);
        verify(dispatcher).dispatchToRoom("room-1", event);
    }
}
