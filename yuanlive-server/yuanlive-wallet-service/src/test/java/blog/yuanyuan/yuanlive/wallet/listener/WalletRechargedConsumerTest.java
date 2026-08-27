package blog.yuanyuan.yuanlive.wallet.listener;

import blog.yuanyuan.yuanlive.entity.wallet.entity.InboxEvent;
import blog.yuanyuan.yuanlive.wallet.mapper.InboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WalletRechargedConsumerTest {

    @Test
    void listenerAcceptsRawAmqpMessageForJsonObjectPayload() throws Exception {
        Method listener = WalletRechargedConsumer.class.getMethod("onMessage", Message.class);

        assertThat(listener.getParameterTypes()).containsExactly(Message.class);
    }

    @Test
    void recordsEachRechargeEventOnceByEventId() throws Exception {
        InboxEventMapper mapper = mock(InboxEventMapper.class);
        when(mapper.selectByEventId("event-001")).thenReturn(null);
        WalletRechargedConsumer consumer = new WalletRechargedConsumer(new ObjectMapper(), mapper);

        consumer.process(new ObjectMapper().writeValueAsString(Map.of(
                "eventId", "event-001", "orderNo", "R001", "userId", 1001L, "coinAmount", 100L)));

        verify(mapper).insert(any(InboxEvent.class));
    }

    @Test
    void duplicateRechargeEventIsIgnored() throws Exception {
        InboxEventMapper mapper = mock(InboxEventMapper.class);
        InboxEvent existing = new InboxEvent();
        existing.setEventId("event-001");
        existing.setStatus("SUCCEEDED");
        when(mapper.selectByEventId("event-001")).thenReturn(existing);
        WalletRechargedConsumer consumer = new WalletRechargedConsumer(new ObjectMapper(), mapper);

        assertThat(consumer.process(new ObjectMapper().writeValueAsString(Map.of(
                "eventId", "event-001", "orderNo", "R001", "userId", 1001L, "coinAmount", 100L)))).isFalse();
    }
}
