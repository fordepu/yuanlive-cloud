package blog.yuanyuan.yuanlive.live.service;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDeliveredEvent;

public interface GiftDeliveredInboxService {

    /**
     * @return 是否首次成功处理；false 代表已成功的幂等重投
     */
    boolean process(GiftDeliveredEvent event);
}
