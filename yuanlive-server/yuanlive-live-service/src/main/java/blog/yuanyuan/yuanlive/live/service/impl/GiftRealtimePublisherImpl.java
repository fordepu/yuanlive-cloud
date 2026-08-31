package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.service.GiftRealtimePublisher;
import lombok.RequiredArgsConstructor;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomRealtimeEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GiftRealtimePublisherImpl implements GiftRealtimePublisher {
    private final RoomRealtimeEventPublisher roomPublisher;

    @Override
    public void publish(GiftDisplayMessage message) {
        roomPublisher.publish(String.valueOf(message.roomId()), message.eventId(), "GIFT_DISPLAY", message);
    }
}
