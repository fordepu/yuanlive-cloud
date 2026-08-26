package blog.yuanyuan.yuanlive.wallet.config;

import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class WalletNotificationListenerConfig {

    @Bean("walletNotificationRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory walletNotificationRabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        RetryOperationsInterceptor retry = RetryInterceptorBuilder.stateless()
                .maxAttempts(4)
                .backOffOptions(1_000, 2.0, 4_000)
                .recoverer((message, cause) -> {
                    throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                            "wallet.recharged 通知重试耗尽", true, cause);
                })
                .build();
        factory.setAdviceChain(retry);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
