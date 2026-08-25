package blog.yuanyuan.yuanlive.wallet.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WalletDomainTopology {

    public static final String DOMAIN_EXCHANGE = "wallet.domain.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "wallet.domain.dlx";

    @Bean
    public TopicExchange walletDomainExchange() {
        return new TopicExchange(DOMAIN_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange walletDomainDeadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }
}
