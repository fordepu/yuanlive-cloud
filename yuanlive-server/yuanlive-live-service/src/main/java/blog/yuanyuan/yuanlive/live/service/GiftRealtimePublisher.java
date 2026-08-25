package blog.yuanyuan.yuanlive.live.service;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;

public interface GiftRealtimePublisher {

    void publish(GiftDisplayMessage message);
}
