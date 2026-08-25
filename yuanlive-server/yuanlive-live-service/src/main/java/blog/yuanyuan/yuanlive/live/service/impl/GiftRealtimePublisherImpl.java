package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.service.GiftRealtimePublisher;
import blog.yuanyuan.yuanlive.live.service.exception.TransientGiftDeliveryException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class GiftRealtimePublisherImpl implements GiftRealtimePublisher {

    public static final String REALTIME_BROADCAST_EXCHANGE = "live.realtime.broadcast.exchange";
    private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(GiftDisplayMessage message) {
        CorrelationData correlationData = new CorrelationData(message.eventId());
        rabbitTemplate.convertAndSend(REALTIME_BROADCAST_EXCHANGE, "", message, correlationData);
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new TransientGiftDeliveryException("礼物展示消息收到 RabbitMQ NACK: " + confirm.getReason());
            }

            // Spring AMQP 会先处理同一消息的 mandatory return 再完成 confirm，随后检查可避免 ACK 与无路由回调竞态。
            ReturnedMessage returned = correlationData.getReturned();
            if (returned != null) {
                throw new TransientGiftDeliveryException("礼物展示消息无法路由: " + returned.getReplyText());
            }
        } catch (TimeoutException exception) {
            throw new TransientGiftDeliveryException("等待礼物展示消息 RabbitMQ confirm 超时", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransientGiftDeliveryException("等待礼物展示消息 RabbitMQ confirm 被中断", exception);
        } catch (ExecutionException exception) {
            throw new TransientGiftDeliveryException("等待礼物展示消息 RabbitMQ confirm 异常", exception.getCause());
        }
    }
}
