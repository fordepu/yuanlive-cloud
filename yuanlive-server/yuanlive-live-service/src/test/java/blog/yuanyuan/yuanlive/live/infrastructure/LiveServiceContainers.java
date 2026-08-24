package blog.yuanyuan.yuanlive.live.infrastructure;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.time.Duration;

@Testcontainers(disabledWithoutDocker = true)
public abstract class LiveServiceContainers {

    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_live")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            // First-run InnoDB initialization can exceed Testcontainers' 60s default.
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Container
    protected static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer("rabbitmq:4.0-management-alpine");

    protected static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.rabbitmq.host", RABBIT_MQ::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_MQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_MQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_MQ::getAdminPassword);
    }
}
