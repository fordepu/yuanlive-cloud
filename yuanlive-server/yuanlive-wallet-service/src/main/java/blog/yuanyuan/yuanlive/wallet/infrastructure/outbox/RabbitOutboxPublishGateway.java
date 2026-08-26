package blog.yuanyuan.yuanlive.wallet.infrastructure.outbox;

import blog.yuanyuan.yuanlive.common.metrics.StageOneMetrics;
import blog.yuanyuan.yuanlive.wallet.config.WalletDomainTopology;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class RabbitOutboxPublishGateway implements OutboxPublishGateway {

    public static final String EVENT_ID_HEADER = "x-outbox-event-id";
    public static final String LEASE_OWNER_HEADER = "x-outbox-lease-owner";

    private static final Logger log = LoggerFactory.getLogger(RabbitOutboxPublishGateway.class);

    private final RabbitTemplate rabbitTemplate;
    private final OutboxStatusService outboxStatusService;

    @Autowired(required = false)
    private StageOneMetrics metrics;

    public RabbitOutboxPublishGateway(RabbitTemplate rabbitTemplate, OutboxStatusService outboxStatusService) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxStatusService = outboxStatusService;
    }

    @PostConstruct
    void configureCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnsCallback(this::handleReturn);
    }

    @Override
    public void publish(OutboxClaim claim) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setCorrelationId(claim.eventId());
        properties.setHeader(EVENT_ID_HEADER, claim.eventId());
        properties.setHeader(LEASE_OWNER_HEADER, claim.leaseOwner());
        Message message = new Message(claim.payload().getBytes(StandardCharsets.UTF_8), properties);

        CorrelationData correlationData = new CorrelationData(claim.eventId());
        correlationData.getFuture().whenComplete((confirm, failure) -> {
            if (failure != null) {
                if (metrics != null) metrics.outboxPublishFailed();
                outboxStatusService.markFailed(
                        claim.eventId(), claim.leaseOwner(), "Confirm异常: " + failure.getMessage());
            } else if (confirm.isAck()) {
                outboxStatusService.markPublished(claim.eventId(), claim.leaseOwner());
            } else {
                if (metrics != null) metrics.outboxPublishFailed();
                outboxStatusService.markFailed(
                        claim.eventId(), claim.leaseOwner(), "Confirm NACK: " + confirm.getReason());
            }
        });
        rabbitTemplate.send(WalletDomainTopology.DOMAIN_EXCHANGE, claim.eventType(), message, correlationData);
    }

    private void handleReturn(ReturnedMessage returned) {
        MessageProperties properties = returned.getMessage().getMessageProperties();
        String eventId = header(properties, EVENT_ID_HEADER);
        String leaseOwner = header(properties, LEASE_OWNER_HEADER);
        if (eventId == null || leaseOwner == null) {
            log.error("Outbox mandatory return缺少领取标识, exchange={}, routingKey={}",
                    returned.getExchange(), returned.getRoutingKey());
            return;
        }
        // mandatory return 与 Confirm ACK 可能相邻到达，owner + PUBLISHING 条件确保只有首个状态迁移生效。
        if (metrics != null) metrics.outboxReturned();
        outboxStatusService.markFailed(
                eventId,
                leaseOwner,
                "Mandatory Return " + returned.getReplyCode() + " " + returned.getReplyText());
    }

    private String header(MessageProperties properties, String name) {
        Object value = properties.getHeaders().get(name);
        return value == null ? null : value.toString();
    }
}
