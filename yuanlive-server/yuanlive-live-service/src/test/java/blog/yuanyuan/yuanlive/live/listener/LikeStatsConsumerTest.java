package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.live.util.PopularityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LikeStatsConsumerTest {

    @Test
    void consumesQueueDeclaredByRabbitTopologyOnly() throws NoSuchMethodException {
        Method method = LikeStatsConsumer.class.getDeclaredMethod("onMessage", String.class);
        RabbitListener listener = method.getAnnotation(RabbitListener.class);

        assertArrayEquals(new String[]{LikeStatsConsumer.QUEUE}, listener.queues());
        assertEquals(0, listener.bindings().length);
    }

    @Test
    void appliesAggregatedLikeWeightToRoomPopularity() throws Exception {
        PopularityUtil popularityUtil = mock(PopularityUtil.class);
        LikeStatsConsumer consumer = new LikeStatsConsumer(new ObjectMapper(), popularityUtil);

        consumer.onMessage("{\"roomId\":\"1001\",\"count\":3,\"weight\":2.0}");

        verify(popularityUtil).updatePopularity(eq("1001"), eq(6.0));
    }

    @Test
    void ignoresMalformedBestEffortStats() throws Exception {
        PopularityUtil popularityUtil = mock(PopularityUtil.class);
        LikeStatsConsumer consumer = new LikeStatsConsumer(new ObjectMapper(), popularityUtil);

        consumer.onMessage("{\"roomId\":\"\",\"count\":0}");

        org.mockito.Mockito.verifyNoInteractions(popularityUtil);
    }
}
