package blog.yuanyuan.yuanlive.live.config;

import blog.yuanyuan.yuanlive.live.listener.LikeStatsConsumer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class LikeStatsRabbitTopology {

    @Bean
    public TopicExchange liveStatsExchange() {
        return new TopicExchange(LikeStatsConsumer.EXCHANGE, true, false);
    }

    @Bean
    public Queue likeStatsQueue() {
        return new Queue(LikeStatsConsumer.QUEUE, true, false, false, Map.of(
                "x-message-ttl", 10_000,
                "x-max-length", 10_000,
                "x-overflow", "drop-head"));
    }

    @Bean
    public Binding likeStatsBinding() {
        return new Binding(LikeStatsConsumer.QUEUE, Binding.DestinationType.QUEUE,
                LikeStatsConsumer.EXCHANGE, LikeStatsConsumer.ROUTING_KEY, null);
    }
}
