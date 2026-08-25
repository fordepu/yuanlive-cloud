package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.live.domain.dto.GiftDisplayMessage;
import blog.yuanyuan.yuanlive.live.service.exception.TransientGiftDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class GiftRealtimePublisherImplTest {

    @Test
    void brokerNackPreventsSuccessfulPublication() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        GiftDisplayMessage message = message();
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "broker nacked message"));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(GiftRealtimePublisherImpl.REALTIME_BROADCAST_EXCHANGE),
                eq(""), eq(message), any(CorrelationData.class));

        assertThatThrownBy(() -> new GiftRealtimePublisherImpl(rabbitTemplate).publish(message))
                .isInstanceOf(TransientGiftDeliveryException.class)
                .hasMessageContaining("NACK");
    }

    @Test
    void confirmTimeoutPreventsSuccessfulPublication() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        assertThatThrownBy(() -> new GiftRealtimePublisherImpl(rabbitTemplate).publish(message()))
                .isInstanceOf(TransientGiftDeliveryException.class)
                .hasMessageContaining("超时");
    }

    @Test
    void mandatoryReturnPreventsSuccessfulPublicationEvenAfterConfirmAck() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        GiftDisplayMessage message = message();
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()), 312, "NO_ROUTE",
                    GiftRealtimePublisherImpl.REALTIME_BROADCAST_EXCHANGE, ""));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(eq(GiftRealtimePublisherImpl.REALTIME_BROADCAST_EXCHANGE),
                eq(""), eq(message), any(CorrelationData.class));

        assertThatThrownBy(() -> new GiftRealtimePublisherImpl(rabbitTemplate).publish(message))
                .isInstanceOf(TransientGiftDeliveryException.class)
                .hasMessageContaining("无法路由");
    }

    @Test
    void sendExceptionPreventsSuccessfulPublication() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        GiftDisplayMessage message = message();
        doThrow(new AmqpException("connection lost")).when(rabbitTemplate)
                .convertAndSend(eq(GiftRealtimePublisherImpl.REALTIME_BROADCAST_EXCHANGE),
                        eq(""), eq(message), any(CorrelationData.class));

        assertThatThrownBy(() -> new GiftRealtimePublisherImpl(rabbitTemplate).publish(message))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("connection lost");
    }

    private GiftDisplayMessage message() {
        return new GiftDisplayMessage("event-001", "order-001", 1001L, 2001L, 3001L, 4001L,
                "ROCKET", "rocket", "gift/rocket.png", 3L, 100L, 300L, 10L, 2000L,
                60L, 240L, "v1");
    }
}
