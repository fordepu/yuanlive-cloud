package blog.yuanyuan.yuanlive.ai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dead-letter topology for AI risk-review messages. The source queue itself is
 * declared by the listener so its name remains configurable through Nacos.
 */
@Configuration
public class RiskMessageTopology {
    public static final String DEAD_LETTER_EXCHANGE = "live.analysis.dlx";
    public static final String DEAD_LETTER_QUEUE = "live.analysis.dead.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "risk.audit.dead";

    @Bean
    public DirectExchange riskDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue riskDeadLetterQueue() {
        return new Queue(DEAD_LETTER_QUEUE, true);
    }

    @Bean
    public Binding riskDeadLetterBinding(Queue riskDeadLetterQueue,
                                         DirectExchange riskDeadLetterExchange) {
        return BindingBuilder.bind(riskDeadLetterQueue)
                .to(riskDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
