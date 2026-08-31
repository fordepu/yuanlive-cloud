package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomEventBufferTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ZSetOperations<String, String> zSets = mock(ZSetOperations.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private RoomEventBuffer buffer;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zSets);
        buffer = new RoomEventBuffer(redis, objectMapper);
    }

    @Test
    void appendsEventWithRedisAllocatedSequenceAndBoundedWindow() throws Exception {
        when(values.increment("ws:room:room-1:seq")).thenReturn(7L);

        RealtimeEvent event = buffer.append("room-1", "event-1", "CHAT", 100L,
                JsonNodeFactory.instance.objectNode().put("content", "hello"));

        assertEquals(7L, event.seq());
        verify(zSets).add(eq("ws:room:room-1:buffer"), eq(objectMapper.writeValueAsString(event)), eq(7D));
        verify(zSets).removeRange("ws:room:room-1:buffer", 0, -501);
    }

    @Test
    void requiresResyncWhenLastSequencePredatesBufferedWindow() {
        ZSetOperations.TypedTuple<String> earliest = tuple("ignored", 100D);
        when(zSets.rangeWithScores("ws:room:room-1:buffer", 0, -1)).thenReturn(Set.of(earliest));

        RoomReplayResult result = buffer.replayAfter("room-1", 1L);

        assertEquals(RoomReplayStatus.RESYNC_REQUIRED, result.status());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void returnsEventsAfterLastConfirmedSequence() throws Exception {
        RealtimeEvent event = new RealtimeEvent("event-8", 8L,
                blog.yuanyuan.yuanlive.live.realtime.ConnectionScope.ROOM, "room-1", "CHAT", 100L,
                JsonNodeFactory.instance.objectNode());
        ZSetOperations.TypedTuple<String> earliest = tuple("ignored", 7D);
        when(zSets.rangeWithScores("ws:room:room-1:buffer", 0, -1)).thenReturn(Set.of(earliest));
        when(zSets.rangeByScore(eq("ws:room:room-1:buffer"), eq(8D), anyDouble()))
                .thenReturn(Set.of(objectMapper.writeValueAsString(event)));

        RoomReplayResult result = buffer.replayAfter("room-1", 7L);

        assertEquals(RoomReplayStatus.REPLAYED, result.status());
        assertEquals(List.of(event), result.events());
    }

    private static ZSetOperations.TypedTuple<String> tuple(String value, double score) {
        ZSetOperations.TypedTuple<String> tuple = mock(ZSetOperations.TypedTuple.class);
        when(tuple.getValue()).thenReturn(value);
        when(tuple.getScore()).thenReturn(score);
        return tuple;
    }
}
