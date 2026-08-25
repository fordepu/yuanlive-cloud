package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.common.config.MessageConfig;
import blog.yuanyuan.yuanlive.live.config.GiftDeliveredRabbitListenerConfig;
import blog.yuanyuan.yuanlive.live.config.GiftDeliveryRabbitTopology;
import blog.yuanyuan.yuanlive.live.mapper.LiveInboxEventMapper;
import blog.yuanyuan.yuanlive.live.service.GiftDeliveredInboxService;
import blog.yuanyuan.yuanlive.live.service.GiftRealtimePublisher;
import blog.yuanyuan.yuanlive.live.service.exception.InvalidGiftDeliveredEventException;
import blog.yuanyuan.yuanlive.live.service.exception.TransientGiftDeliveryException;
import blog.yuanyuan.yuanlive.live.service.impl.GiftDeliveredInboxServiceImpl;
import blog.yuanyuan.yuanlive.live.service.impl.GiftRealtimePublisherImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 覆盖领域事实到直播展示的真实 broker 与数据库边界；展示层队列仅用于观察，不替代资金事实队列。
 */
@SpringBootTest(
        classes = {GiftDeliveredConsumerIntegrationTest.GiftDeliveryTestApplication.class,
                GiftDeliveredConsumerIntegrationTest.RabbitTestTopology.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        args = "--spring.config.location=optional:classpath:/live-test.yml",
        properties = {
                "spring.main.banner-mode=off",
                "spring.flyway.enabled=true",
                "spring.rabbitmq.listener.simple.auto-startup=true"
        })
@Testcontainers
class GiftDeliveredConsumerIntegrationTest {

    private static final String DOMAIN_EXCHANGE = "wallet.domain.exchange";
    private static final String DISPLAY_QUEUE = "live.gift.delivered.display.integration.queue";
    private static final String DEAD_QUEUE = "live.gift.delivered.dead.queue";
    private static final String SOURCE_QUEUE = GiftDeliveryRabbitTopology.GIFT_DELIVERED_QUEUE;
    private static final HttpClient MANAGEMENT_CLIENT = HttpClient.newHttpClient();
    private static final Set<String> DISPLAY_FIELDS = Set.of(
            "eventId", "orderNo", "roomId", "senderId", "anchorId", "giftId", "giftCode", "giftName",
            "giftIcon", "giftCount", "unitCoinAmount", "coinAmount", "exchangeRate", "platformRate",
            "platformCoinAmount", "anchorIncomeAmount", "settlementRuleVersion");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_live")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    private static final RabbitMQContainer RABBIT_MQ = new RabbitMQContainer("rabbitmq:4.0-management-alpine")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("giftDisplayBinding")
    private Binding giftDisplayBinding;

    @SpyBean
    private GiftRealtimePublisher giftRealtimePublisher;

    @SpyBean
    private GiftDeliveredInboxService giftDeliveredInboxService;

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT_MQ::getHost);
        registry.add("spring.rabbitmq.port", RABBIT_MQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT_MQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT_MQ::getAdminPassword);
    }

    @BeforeEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM live_inbox_event");
        rabbitAdmin.purgeQueue(SOURCE_QUEUE, true);
        rabbitAdmin.purgeQueue(DISPLAY_QUEUE, true);
        rabbitAdmin.purgeQueue(DEAD_QUEUE, true);
        rabbitAdmin.declareBinding(giftDisplayBinding);
    }

    @Test
    void firstEventCreatesInboxAcknowledgesAndPublishesCompleteDisplayPayload() throws Exception {
        publish(event("gift-event-001"));

        Message display = receiveDisplayMessage();
        JsonNode payload = objectMapper.readTree(new String(display.getBody(), StandardCharsets.UTF_8));
        assertThat(payload.fieldNames()).toIterable().containsAll(DISPLAY_FIELDS);
        assertThat(payload.path("eventId").asText()).isEqualTo("gift-event-001");
        assertThat(payload.path("orderNo").asText()).isEqualTo("gift-order-001");
        assertThat(payload.path("coinAmount").asLong()).isEqualTo(300L);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(inboxCount("gift-event-001")).isEqualTo(1L);
            assertThat(inboxStatus("gift-event-001")).isEqualTo("SUCCEEDED");
            assertThat(queueState(SOURCE_QUEUE)).isEqualTo(QueueState.EMPTY);
        });
    }

    @Test
    void duplicateEventAcknowledgesWithoutPublishingDisplayTwice() {
        String event = event("gift-event-duplicate");
        publish(event);
        receiveDisplayMessage();

        publish(event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(giftDeliveredInboxService, times(2)).process(any());
            assertThat(inboxCount("gift-event-duplicate")).isEqualTo(1L);
            assertThat(queueState(SOURCE_QUEUE)).isEqualTo(QueueState.EMPTY);
        });
        assertThat(rabbitTemplate.receive(DISPLAY_QUEUE, 1_000)).isNull();
    }

    @Test
    void transientFailureIsRetriedAndEventuallyPublishes() {
        doThrow(new TransientGiftDeliveryException("broker temporarily unavailable"))
                .doCallRealMethod()
                .when(giftRealtimePublisher)
                .publish(any());

        publish(event("gift-event-retry"));

        assertThat(receiveDisplayMessage()).isNotNull();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(inboxCount("gift-event-retry")).isEqualTo(1L);
            assertThat(inboxStatus("gift-event-retry")).isEqualTo("SUCCEEDED");
        });
    }

    @Test
    void unrecoverableFailureIsRejectedToDeadLetterQueueWithoutInboxSuccess() {
        doThrow(new InvalidGiftDeliveredEventException("immutable payload is invalid"))
                .when(giftRealtimePublisher)
                .publish(any());

        publish(event("gift-event-dead"));

        Message deadLetter = rabbitTemplate.receive(DEAD_QUEUE, 10_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains("gift-event-dead");
        assertThat(inboxCount("gift-event-dead")).isZero();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queueState(SOURCE_QUEUE)).isEqualTo(QueueState.EMPTY));
    }

    @Test
    void unroutableDisplayReturnPreventsAcknowledgementAndMovesEventToDeadLetterQueue() {
        rabbitAdmin.removeBinding(giftDisplayBinding);

        publish(event("gift-event-return"));

        Message deadLetter = rabbitTemplate.receive(DEAD_QUEUE, 12_000);
        assertThat(deadLetter).isNotNull();
        assertThat(new String(deadLetter.getBody(), StandardCharsets.UTF_8)).contains("gift-event-return");
        assertThat(inboxCount("gift-event-return")).isZero();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(queueState(SOURCE_QUEUE)).isEqualTo(QueueState.EMPTY));
    }

    private void publish(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(DOMAIN_EXCHANGE, "gift.delivered",
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private Message receiveDisplayMessage() {
        Message message = rabbitTemplate.receive(DISPLAY_QUEUE, 10_000);
        assertThat(message).isNotNull();
        return message;
    }

    private long inboxCount(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM live_inbox_event WHERE event_id = ?", Long.class, eventId);
    }

    private String inboxStatus(String eventId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM live_inbox_event WHERE event_id = ?", String.class, eventId);
    }

    private QueueState queueState(String queueName) {
        String authorization = Base64.getEncoder().encodeToString((RABBIT_MQ.getAdminUsername()
                + ":" + RABBIT_MQ.getAdminPassword()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(RABBIT_MQ.getHttpUrl()
                        + "/api/queues/%2F/" + queueName))
                .header("Authorization", "Basic " + authorization)
                .GET()
                .build();
        try {
            HttpResponse<String> response = MANAGEMENT_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            JsonNode queue = objectMapper.readTree(response.body());
            return new QueueState(queue.path("messages_ready").asLong(),
                    queue.path("messages_unacknowledged").asLong());
        } catch (IOException exception) {
            throw new IllegalStateException("无法查询 RabbitMQ 队列状态", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("查询 RabbitMQ 队列状态被中断", exception);
        }
    }

    private String event(String eventId) {
        return """
                {"eventId":"%s","orderNo":"gift-order-001","roomId":1001,"senderId":2001,"anchorId":3001,
                "giftId":4001,"giftCode":"ROCKET","giftName":"rocket","giftIcon":"gift/rocket.png",
                "giftCount":3,"unitCoinAmount":100,"coinAmount":300,"exchangeRate":10,"platformRate":2000,
                "platformCoinAmount":60,"anchorIncomeAmount":240,"settlementRuleVersion":"v1"}
                """.formatted(eventId);
    }

    private record QueueState(long ready, long unacknowledged) {
        private static final QueueState EMPTY = new QueueState(0L, 0L);
    }

    @TestConfiguration
    static class RabbitTestTopology {

        @Bean
        TopicExchange walletDomainExchangeForTest() {
            return new TopicExchange(DOMAIN_EXCHANGE, true, false);
        }

        @Bean
        TopicExchange walletDomainDeadLetterExchangeForTest() {
            return new TopicExchange("wallet.domain.dlx", true, false);
        }

        @Bean
        Queue giftDisplayQueue() {
            return new Queue(DISPLAY_QUEUE, true, false, false);
        }

        @Bean
        Binding giftDisplayBinding(
                @Qualifier("giftDisplayQueue") Queue giftDisplayQueue,
                @Qualifier("liveRealtimeBroadcastExchange") org.springframework.amqp.core.FanoutExchange exchange) {
            return BindingBuilder.bind(giftDisplayQueue).to(exchange);
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = LiveInboxEventMapper.class)
    @Import({
            MessageConfig.class,
            GiftDeliveryRabbitTopology.class,
            GiftDeliveredRabbitListenerConfig.class,
            GiftRealtimePublisherImpl.class,
            GiftDeliveredInboxServiceImpl.class,
            GiftDeliveredConsumer.class
    })
    static class GiftDeliveryTestApplication {
    }
}
