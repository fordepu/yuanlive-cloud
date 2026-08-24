package blog.yuanyuan.yuanlive.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskMessageTopologyTest {

    @Test
    void declaresDurableDeadLetterRouteForRiskMessages() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RiskMessageTopology.class)) {
            Queue queue = context.getBean("riskDeadLetterQueue", Queue.class);
            DirectExchange exchange = context.getBean("riskDeadLetterExchange", DirectExchange.class);

            assertEquals(RiskMessageTopology.DEAD_LETTER_QUEUE, queue.getName());
            assertTrue(queue.isDurable());
            assertEquals(RiskMessageTopology.DEAD_LETTER_EXCHANGE, exchange.getName());
            assertTrue(exchange.isDurable());
        }
    }
}
