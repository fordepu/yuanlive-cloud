package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

import blog.yuanyuan.yuanlive.feign.live.LiveFeignClient;
import blog.yuanyuan.yuanlive.wallet.WalletApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = {WalletApplication.class, OutboxPublisherIntegrationTest.RabbitTestTopology.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        args = "--spring.config.location=optional:classpath:/wallet-test.yml",
        properties = {
                "spring.main.banner-mode=off",
                "spring.rabbitmq.publisher-confirm-type=correlated",
                "spring.rabbitmq.publisher-returns=true",
                "spring.rabbitmq.template.mandatory=true",
                "wallet.outbox.publish-interval-ms=3600000",
                "wallet.outbox.batch-size=10",
                "wallet.outbox.lease-seconds=30",
                "wallet.outbox.max-retries=2"
        })
@Testcontainers
class OutboxPublisherIntegrationTest {

    private static final String ACK_QUEUE = "wallet.outbox.publisher.integration.queue";
    private static final String EVENT_ID_HEADER = "x-outbox-event-id";
    private static final String LEASE_OWNER_HEADER = "x-outbox-lease-owner";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_wallet")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withInitScript("db/wallet-schema.sql")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    private static final RabbitMQContainer RABBITMQ = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:4.1.3-management-alpine"))
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private OutboxClaimService outboxClaimService;

    @Autowired
    private OutboxStatusService outboxStatusService;

    @MockBean
    private LiveFeignClient liveFeignClient;

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @BeforeEach
    void cleanOutbox() {
        jdbcTemplate.update("DELETE FROM outbox_event");
        rabbitAdmin.purgeQueue(ACK_QUEUE, true);
    }

    @Test
    void confirmAckPublishesOriginalPayloadAndMarksEventPublished() throws Exception {
        String eventId = "event-ack-001";
        String payload = "{\"eventId\":\"event-ack-001\",\"orderNo\":\"gift-001\"}";
        insertEvent(9001L, eventId, "gift.delivered", payload, "NEW", 0, null, null, null);
        String committedPayload = jdbcTemplate.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_id = ?", String.class, eventId);

        outboxPublisher.publishAvailable();

        Message message = rabbitTemplate.receive(ACK_QUEUE, 10_000);
        assertThat(message).isNotNull();
        String publishedPayload = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(publishedPayload).isEqualTo(committedPayload);
        JsonNode publishedJson = objectMapper.readTree(publishedPayload);
        assertThat(publishedJson.path("eventId").asText()).isEqualTo("event-ack-001");
        assertThat(publishedJson.path("orderNo").asText()).isEqualTo("gift-001");
        assertThat((Object) message.getMessageProperties().getHeader(EVENT_ID_HEADER)).isEqualTo(eventId);
        assertThat(message.getMessageProperties().getCorrelationId()).isEqualTo(eventId);
        assertThat((String) message.getMessageProperties().getHeader(LEASE_OWNER_HEADER)).isNotBlank();
        awaitStatus(eventId, "PUBLISHED");

        Map<String, Object> stored = event(eventId);
        assertThat(stored.get("published_at")).isNotNull();
        assertThat(stored.get("lease_owner")).isNull();
        assertThat(stored.get("event_id")).isEqualTo(eventId);
    }

    @Test
    void mandatoryReturnKeepsEventIdAndMovesEventToRetryWait() {
        String eventId = "event-return-001";
        LocalDateTime beforePublish = LocalDateTime.now();
        insertEvent(9002L, eventId, "unroutable.event", "{\"eventId\":\"event-return-001\"}",
                "NEW", 0, null, null, null);

        outboxPublisher.publishAvailable();

        awaitStatus(eventId, "RETRY_WAIT");
        Map<String, Object> stored = event(eventId);
        assertThat(stored.get("event_id")).isEqualTo(eventId);
        assertThat(((Number) stored.get("retry_count")).intValue()).isEqualTo(1);
        assertThat(asLocalDateTime(stored.get("next_retry_at"))).isAfter(beforePublish);
        assertThat(stored.get("failure_reason").toString()).contains("NO_ROUTE");
    }

    @Test
    void sendExceptionKeepsEventIdAndSchedulesRetry() {
        String eventId = "event-exception-001";
        insertEvent(9003L, eventId, "gift.delivered", "{\"eventId\":\"event-exception-001\"}",
                "NEW", 0, null, null, null);
        OutboxPublisher throwingPublisher = new OutboxPublisher(
                outboxClaimService,
                outboxStatusService,
                claim -> {
                    throw new AmqpIOException(new java.io.IOException("broker unavailable"));
                });

        throwingPublisher.publishAvailable();

        Map<String, Object> stored = event(eventId);
        assertThat(stored.get("event_id")).isEqualTo(eventId);
        assertThat(stored.get("status")).isEqualTo("RETRY_WAIT");
        assertThat(((Number) stored.get("retry_count")).intValue()).isEqualTo(1);
        assertThat(stored.get("next_retry_at")).isNotNull();
        assertThat(stored.get("failure_reason").toString()).contains("broker unavailable");
    }

    @Test
    void expiredLeaseCanBeReclaimedAndLateOldOwnerConfirmCannotOverwriteIt() {
        String eventId = "event-lease-001";
        String oldOwner = "expired-owner";
        insertEvent(9004L, eventId, "gift.delivered", "{\"eventId\":\"event-lease-001\"}",
                "PUBLISHING", 0, null, oldOwner, LocalDateTime.now().minusSeconds(5));

        List<OutboxClaim> claims = outboxClaimService.claimAvailable();

        assertThat(claims).hasSize(1);
        OutboxClaim reclaimed = claims.get(0);
        assertThat(reclaimed.eventId()).isEqualTo(eventId);
        assertThat(reclaimed.leaseOwner()).isNotEqualTo(oldOwner);
        assertThat(outboxStatusService.markPublished(eventId, oldOwner)).isFalse();
        Map<String, Object> stored = event(eventId);
        assertThat(stored.get("status")).isEqualTo("PUBLISHING");
        assertThat(stored.get("lease_owner")).isEqualTo(reclaimed.leaseOwner());
    }

    @Test
    void repeatedFailureAtRetryLimitMarksEventDeadWithoutDeletingIt() {
        String eventId = "event-dead-001";
        insertEvent(9005L, eventId, "gift.delivered", "{\"eventId\":\"event-dead-001\"}",
                "RETRY_WAIT", 1, LocalDateTime.now().minusSeconds(1), null, null);
        OutboxPublisher throwingPublisher = new OutboxPublisher(
                outboxClaimService,
                outboxStatusService,
                claim -> {
                    throw new AmqpIOException(new java.io.IOException("still unavailable"));
                });

        throwingPublisher.publishAvailable();

        Map<String, Object> stored = event(eventId);
        assertThat(stored.get("event_id")).isEqualTo(eventId);
        assertThat(stored.get("status")).isEqualTo("DEAD");
        assertThat(((Number) stored.get("retry_count")).intValue()).isEqualTo(2);
        assertThat(stored.get("next_retry_at")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE event_id = ?", Long.class, eventId)).isEqualTo(1L);
    }

    private void awaitStatus(String eventId, String status) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(event(eventId).get("status")).isEqualTo(status));
    }

    private Map<String, Object> event(String eventId) {
        return jdbcTemplate.queryForMap("SELECT * FROM outbox_event WHERE event_id = ?", eventId);
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        return ((Timestamp) value).toLocalDateTime();
    }

    private void insertEvent(long id,
                             String eventId,
                             String eventType,
                             String payload,
                             String status,
                             int retryCount,
                             LocalDateTime nextRetryAt,
                             String leaseOwner,
                             LocalDateTime leaseUntil) {
        jdbcTemplate.update("""
                        INSERT INTO outbox_event
                            (id, event_id, event_type, business_id, payload, status, retry_count,
                             next_retry_at, lease_owner, lease_until)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id, eventId, eventType, "business-" + id, payload, status, retryCount,
                nextRetryAt, leaseOwner, leaseUntil);
    }

    @TestConfiguration
    static class RabbitTestTopology {

        @Bean
        Queue outboxPublisherAckQueue() {
            return new Queue(ACK_QUEUE, true, false, false);
        }

        @Bean
        Binding outboxPublisherAckBinding(
                @Qualifier("outboxPublisherAckQueue") Queue outboxPublisherAckQueue,
                @Qualifier("walletDomainExchange") org.springframework.amqp.core.TopicExchange exchange) {
            return BindingBuilder.bind(outboxPublisherAckQueue).to(exchange).with("gift.delivered");
        }
    }
}
