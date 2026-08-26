package blog.yuanyuan.yuanlive.live.listener;

import blog.yuanyuan.yuanlive.live.util.PopularityUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LikeStatsConsumerTest {

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
