package blog.yuanyuan.yuanlive.live.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class GiftDeliveryRabbitTopology {

    public static final String GIFT_DELIVERED_QUEUE = "live.gift.delivered.queue";
    public static final String GIFT_DELIVERED_DEAD_QUEUE = "live.gift.delivered.dead.queue";
    public static final String WALLET_DOMAIN_EXCHANGE = "wallet.domain.exchange";
    public static final String WALLET_DOMAIN_DLX = "wallet.domain.dlx";
    public static final String GIFT_DELIVERED_ROUTING_KEY = "gift.delivered";

    @Bean
    public Queue liveGiftDeliveredQueue() {
        return new Queue(GIFT_DELIVERED_QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", WALLET_DOMAIN_DLX,
                "x-dead-letter-routing-key", GIFT_DELIVERED_ROUTING_KEY));
    }

    @Bean
    public Queue liveGiftDeliveredDeadQueue() {
        return new Queue(GIFT_DELIVERED_DEAD_QUEUE, true, false, false);
    }

    @Bean
    public Binding liveGiftDeliveredBinding(@Qualifier("liveGiftDeliveredQueue") Queue liveGiftDeliveredQueue) {
        // 钱包拥有领域交换机；直播服务只声明自己的队列及绑定，避免消费者越权创建生产者资源。
        return new Binding(GIFT_DELIVERED_QUEUE, Binding.DestinationType.QUEUE,
                WALLET_DOMAIN_EXCHANGE, GIFT_DELIVERED_ROUTING_KEY, null);
    }

    @Bean
    public Binding liveGiftDeliveredDeadBinding(@Qualifier("liveGiftDeliveredDeadQueue") Queue liveGiftDeliveredDeadQueue) {
        return new Binding(GIFT_DELIVERED_DEAD_QUEUE, Binding.DestinationType.QUEUE,
                WALLET_DOMAIN_DLX, GIFT_DELIVERED_ROUTING_KEY, null);
    }

}
