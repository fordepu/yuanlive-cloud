package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

/** 每个实例拥有独立队列，跨实例消息只投递给实际承载连接的实例。 */
@Configuration
public class RealtimeDispatchTopology {
    public static final String EXCHANGE = "live.ws.dispatch.exchange";

    public static String queueName(RealtimeInstanceIdentity identity) {
        return "live.ws.dispatch.instance." + identity.instanceId() + "." + identity.epoch();
    }

    @Bean
    public DirectExchange realtimeDispatchExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue realtimeInstanceDispatchQueue(RealtimeInstanceIdentity identity) {
        return new Queue(queueName(identity), false, true, true);
    }

    @Bean
    public Binding realtimeInstanceDispatchBinding(
            @Qualifier("realtimeInstanceDispatchQueue") Queue realtimeInstanceDispatchQueue,
            @Qualifier("realtimeDispatchExchange") DirectExchange realtimeDispatchExchange) {
        return BindingBuilder.bind(realtimeInstanceDispatchQueue)
                .to(realtimeDispatchExchange)
                .with(realtimeInstanceDispatchQueue.getName());
    }
}
