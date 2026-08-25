package blog.yuanyuan.yuanlive.live.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class GiftRabbitPublisherConfigurationTest {

    @Test
    void productionDefaultsEnableConfirmReturnAndMandatoryPublishing() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        assertThat(properties)
                .containsEntry("spring.rabbitmq.publisher-confirm-type", "correlated")
                .containsEntry("spring.rabbitmq.publisher-returns", true)
                .containsEntry("spring.rabbitmq.template.mandatory", true);
    }
}
