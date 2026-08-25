package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.entity.live.entity.LiveInboxEvent;
import blog.yuanyuan.yuanlive.entity.live.enums.LiveInboxEventStatus;
import blog.yuanyuan.yuanlive.live.domain.dto.GiftDeliveredEvent;
import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.mapper.LiveInboxEventMapper;
import blog.yuanyuan.yuanlive.live.service.GiftDeliveredInboxService;
import blog.yuanyuan.yuanlive.live.service.GiftRealtimePublisher;
import blog.yuanyuan.yuanlive.live.service.exception.InvalidGiftDeliveredEventException;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GiftDeliveredInboxServiceImpl implements GiftDeliveredInboxService {

    private final LiveInboxEventMapper liveInboxEventMapper;
    private final GiftRealtimePublisher giftRealtimePublisher;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean process(GiftDeliveredEvent event) {
        validate(event);
        if (isSucceeded(event.eventId())) {
            return false;
        }

        LiveInboxEvent inbox = new LiveInboxEvent();
        inbox.setEventId(event.eventId());
        inbox.setEventType("gift.delivered");
        inbox.setBusinessId(event.orderNo());
        inbox.setStatus(LiveInboxEventStatus.PROCESSING.name());
        try {
            liveInboxEventMapper.insert(inbox);
        } catch (DuplicateKeyException exception) {
            // 并发重投由唯一索引裁决；只允许已经成功的事件直接确认，其他状态交给消息重试恢复。
            if (isSucceeded(event.eventId())) {
                return false;
            }
            throw exception;
        }

        GiftDisplayMessage displayMessage = GiftDisplayMessage.from(event);
        giftRealtimePublisher.publish(displayMessage);
        inbox.setStatus(LiveInboxEventStatus.SUCCEEDED.name());
        inbox.setProcessedAt(LocalDateTime.now());
        liveInboxEventMapper.updateById(inbox);
        return true;
    }

    private boolean isSucceeded(String eventId) {
        return liveInboxEventMapper.selectCount(Wrappers.<LiveInboxEvent>lambdaQuery()
                .eq(LiveInboxEvent::getEventId, eventId)
                .eq(LiveInboxEvent::getStatus, LiveInboxEventStatus.SUCCEEDED.name())) > 0;
    }

    private void validate(GiftDeliveredEvent event) {
        if (event == null || isBlank(event.eventId()) || isBlank(event.orderNo())
                || event.roomId() == null || event.senderId() == null || event.anchorId() == null
                || event.giftId() == null || event.giftCount() == null || event.unitCoinAmount() == null
                || event.coinAmount() == null || event.exchangeRate() == null || event.platformRate() == null
                || event.platformCoinAmount() == null || event.anchorIncomeAmount() == null
                || isBlank(event.giftCode()) || isBlank(event.giftName()) || isBlank(event.giftIcon())
                || isBlank(event.settlementRuleVersion())) {
            throw new InvalidGiftDeliveredEventException("gift.delivered 缺少钱包已提交字段");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
