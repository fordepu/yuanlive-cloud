package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.service.impl.GiftRealtimePublisherImpl;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import blog.yuanyuan.yuanlive.common.metrics.StageOneMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 礼物展示只需要广播到当前实例持有的 WebSocket 连接，因此每个实例使用独立临时队列。
 * 该队列不承载资金事实，实例下线时自动删除，客户端通过 eventId 处理重复展示。
 */
@Component
@ConditionalOnProperty(name = "live.gift-display.consumer-enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class GiftDisplayMessageConsumer {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    @Autowired(required = false)
    private StageOneMetrics metrics;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    value = "",
                    durable = "false",
                    autoDelete = "true",
                    exclusive = "true"
            ),
            exchange = @Exchange(value = GiftRealtimePublisherImpl.REALTIME_BROADCAST_EXCHANGE,
                    type = ExchangeTypes.FANOUT, durable = "true")
    ))
    public void onMessage(String payload) {
        try {
            GiftDisplayMessage message = objectMapper.readValue(payload, GiftDisplayMessage.class);
            ChannelGroup group = sessionManager.getRoomChannels(String.valueOf(message.roomId()));
            if (group == null || group.isEmpty()) {
                return;
            }
            group.writeAndFlush(new TextWebSocketFrame(objectMapper.writeValueAsString(message)));
            if (metrics != null) metrics.giftDisplayPublished();
        } catch (JsonProcessingException exception) {
            // 展示消息格式错误只能丢弃本次展示，不影响已提交的资金事实。
            log.warn("礼物展示消息格式错误，忽略本次展示", exception);
        }
    }
}
