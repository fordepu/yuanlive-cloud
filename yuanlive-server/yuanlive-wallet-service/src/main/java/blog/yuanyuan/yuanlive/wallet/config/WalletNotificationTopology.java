package blog.yuanyuan.yuanlive.wallet.config;

import blog.yuanyuan.yuanlive.wallet.listener.WalletRechargedConsumer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class WalletNotificationTopology {

    @Bean
    public Queue walletRechargedNotificationQueue() {
        return new Queue(WalletRechargedConsumer.QUEUE, true, false, false, Map.of(
                "x-dead-letter-exchange", WalletRechargedConsumer.DLX,
                "x-dead-letter-routing-key", WalletRechargedConsumer.ROUTING_KEY));
    }

    @Bean
    public Queue walletRechargedNotificationDeadQueue() {
        return new Queue(WalletRechargedConsumer.DEAD_QUEUE, true, false, false);
    }

    @Bean
    public Binding walletRechargedNotificationBinding(
            @Qualifier("walletRechargedNotificationQueue") Queue walletRechargedNotificationQueue) {
        return new Binding(WalletRechargedConsumer.QUEUE, Binding.DestinationType.QUEUE,
                WalletRechargedConsumer.EXCHANGE, WalletRechargedConsumer.ROUTING_KEY, null);
    }

    @Bean
    public Binding walletRechargedNotificationDeadBinding() {
        return new Binding(WalletRechargedConsumer.DEAD_QUEUE, Binding.DestinationType.QUEUE,
                WalletRechargedConsumer.DLX, WalletRechargedConsumer.ROUTING_KEY, null);
    }
}
