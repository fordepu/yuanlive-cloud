package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.common.metrics.StageOneMetrics;
import blog.yuanyuan.yuanlive.live.config.GiftDeliveryRabbitTopology;
import blog.yuanyuan.yuanlive.live.domain.dto.GiftDeliveredEvent;
import blog.yuanyuan.yuanlive.live.service.GiftDeliveredInboxService;
import blog.yuanyuan.yuanlive.live.service.exception.InvalidGiftDeliveredEventException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class GiftDeliveredConsumer {

    private final ObjectMapper objectMapper;
    private final GiftDeliveredInboxService giftDeliveredInboxService;

    @Autowired(required = false)
    private StageOneMetrics metrics;

    @RabbitListener(
            queues = GiftDeliveryRabbitTopology.GIFT_DELIVERED_QUEUE,
            containerFactory = "giftDeliveredManualAckListenerContainerFactory")
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            GiftDeliveredEvent event = objectMapper.readValue(message.getBody(), GiftDeliveredEvent.class);
            giftDeliveredInboxService.process(event);
            channel.basicAck(deliveryTag, false);
        } catch (JsonProcessingException | InvalidGiftDeliveredEventException exception) {
            // 负载已提交且不可修复时不能确认；显式拒绝让原消息带着 eventId 进入死信队列供人工审计。
            log.error("gift.delivered 不可恢复，转入死信队列", exception);
            if (metrics != null) metrics.inboxFailed();
            channel.basicReject(deliveryTag, false);
        }
    }
}
