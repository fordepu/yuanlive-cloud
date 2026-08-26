package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.group.ChannelGroup;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GiftDisplayMessageConsumerTest {

    @Test
    void broadcastsDisplayMessageOnlyToTheLocalRoomGroup() throws Exception {
        SessionManager sessionManager = mock(SessionManager.class);
        ChannelGroup roomGroup = mock(ChannelGroup.class);
        when(sessionManager.getRoomChannels("1001")).thenReturn(roomGroup);
        GiftDisplayMessageConsumer consumer = new GiftDisplayMessageConsumer(new ObjectMapper(), sessionManager);

        consumer.onMessage(new ObjectMapper().writeValueAsString(message(1001L)));

        verify(roomGroup).writeAndFlush(any());
        verify(sessionManager).getRoomChannels("1001");
    }

    @Test
    void doesNotBroadcastWhenTheLocalInstanceHasNoMatchingRoom() throws Exception {
        SessionManager sessionManager = mock(SessionManager.class);
        when(sessionManager.getRoomChannels("2002")).thenReturn(null);
        GiftDisplayMessageConsumer consumer = new GiftDisplayMessageConsumer(new ObjectMapper(), sessionManager);

        consumer.onMessage(new ObjectMapper().writeValueAsString(message(2002L)));

        verify(sessionManager).getRoomChannels("2002");
        verify(sessionManager, never()).getRoomChannels("1001");
    }

    private GiftDisplayMessage message(Long roomId) {
        return new GiftDisplayMessage("event-001", "order-001", roomId, 2001L, 3001L, 4001L,
                "ROCKET", "rocket", "gift/rocket.png", 1L, 100L, 100L, 10L, 2000L,
                20L, 80L, "v1");
    }
}
