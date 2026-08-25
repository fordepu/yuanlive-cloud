package blog.yuanyuan.yuanlive.live.config;

import org.aopalliance.aop.Advice;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class GiftDeliveredRabbitListenerConfig {

    @Bean("giftDeliveredManualAckListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory giftDeliveredManualAckListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(1_000, 2.0, 4_000)
                .recoverer((message, cause) -> {
                    // 手动确认模式必须显式标记 rejectManual，耗尽重试后容器才会 nack 并将资金事实送入死信队列。
                    throw new AmqpRejectAndDontRequeueException("gift.delivered 重试耗尽", true, cause);
                })
                .build();
        factory.setAdviceChain(new Advice[]{retryInterceptor});
        // 资金事实只能在 Inbox 本地事务与展示消息发送完成后显式确认，异常交给有限重试或死信处理。
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
