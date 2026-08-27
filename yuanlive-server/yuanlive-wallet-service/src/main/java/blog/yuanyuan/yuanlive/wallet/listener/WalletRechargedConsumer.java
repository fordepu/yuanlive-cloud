package blog.yuanyuan.yuanlive.wallet.listener;

import blog.yuanyuan.yuanlive.entity.wallet.entity.InboxEvent;
import blog.yuanyuan.yuanlive.common.metrics.StageOneMetrics;
import blog.yuanyuan.yuanlive.wallet.mapper.InboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

/**
 * 充值成功通知只记录一次消费事实，不回写钱包资金表，避免通知消费失败影响已经提交的充值。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletRechargedConsumer {

    public static final String QUEUE = "notification.wallet.recharged.queue";
    public static final String DEAD_QUEUE = "notification.wallet.recharged.dead.queue";
    public static final String EXCHANGE = "wallet.domain.exchange";
    public static final String DLX = "wallet.domain.dlx";
    public static final String ROUTING_KEY = "wallet.recharged";

    private final ObjectMapper objectMapper;
    private final InboxEventMapper inboxEventMapper;

    @Autowired(required = false)
    private StageOneMetrics metrics;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = QUEUE, durable = "true", arguments = {
                    @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-exchange", value = DLX),
                    @org.springframework.amqp.rabbit.annotation.Argument(name = "x-dead-letter-routing-key", value = ROUTING_KEY)
            }),
            exchange = @Exchange(value = EXCHANGE, type = ExchangeTypes.TOPIC, durable = "true"),
            key = ROUTING_KEY), containerFactory = "walletNotificationRabbitListenerContainerFactory")
    public void onMessage(Message message) throws Exception {
        // 领域事件的 body 固定为 JSON 对象；接收原始消息可绕过转换器将对象错误反序列化为 String 的失败。
        process(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean process(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        String eventId = required(event, "eventId");
        if (inboxEventMapper.selectByEventId(eventId) != null) {
            return false;
        }
        InboxEvent inbox = new InboxEvent();
        inbox.setEventId(eventId);
        inbox.setEventType(ROUTING_KEY);
        inbox.setBusinessId(required(event, "orderNo"));
        inbox.setStatus("SUCCEEDED");
        try {
            inboxEventMapper.insert(inbox);
        } catch (DuplicateKeyException duplicateKeyException) {
            return false;
        }
        if (metrics != null) metrics.rechargeNotificationProcessed();
        return true;
    }

    private String required(JsonNode event, String field) {
        JsonNode value = event.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("wallet.recharged 缺少字段: " + field);
        }
        return value.asText();
    }
}
