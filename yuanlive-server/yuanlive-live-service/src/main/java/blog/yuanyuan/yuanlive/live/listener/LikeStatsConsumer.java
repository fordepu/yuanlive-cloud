package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.live.util.PopularityUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 点赞是 BEST_EFFORT 统计消息，允许 TTL 和队列长度淘汰，热度可由 Redis 快照重算。 */
@Component
@RequiredArgsConstructor
public class LikeStatsConsumer {

    public static final String EXCHANGE = "live.stats.exchange";
    public static final String QUEUE = "live.stats.like.queue";
    public static final String ROUTING_KEY = "like";

    private final ObjectMapper objectMapper;
    private final PopularityUtil popularityUtil;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = QUEUE, durable = "true", arguments = {
                    @org.springframework.amqp.rabbit.annotation.Argument(name = "x-message-ttl", value = "10000"),
                    @org.springframework.amqp.rabbit.annotation.Argument(name = "x-max-length", value = "10000"),
                    @org.springframework.amqp.rabbit.annotation.Argument(name = "x-overflow", value = "drop-head")
            }),
            exchange = @Exchange(value = EXCHANGE, type = ExchangeTypes.TOPIC, durable = "true"),
            key = ROUTING_KEY))
    public void onMessage(String payload) throws Exception {
        JsonNode event = objectMapper.readTree(payload);
        String roomId = event.path("roomId").asText();
        long count = event.path("count").asLong(0L);
        if (roomId.isBlank() || count <= 0) return;
        popularityUtil.updatePopularity(roomId, count * event.path("weight").asDouble(1.0));
    }
}
