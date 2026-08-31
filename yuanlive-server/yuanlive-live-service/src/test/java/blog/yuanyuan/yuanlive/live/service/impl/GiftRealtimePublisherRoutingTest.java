package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomRealtimeEventPublisher;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GiftRealtimePublisherRoutingTest {
    @Test
    void giftDisplayIsPublishedAsBufferedRoomEvent() {
        RoomRealtimeEventPublisher publisher = mock(RoomRealtimeEventPublisher.class);
        GiftDisplayMessage gift = new GiftDisplayMessage("event-1", "order-1", 1001L, 2001L, 3001L, 4001L,
                "ROCKET", "rocket", "gift.png", 1L, 100L, 100L, 10L, 2000L, 20L, 80L, "v1");

        new GiftRealtimePublisherImpl(publisher).publish(gift);

        verify(publisher).publish(eq("1001"), eq("event-1"), eq("GIFT_DISPLAY"), eq(gift));
    }
}
