package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis INCR 提供跨实例单调 seq，ZSet 保存可短期补拉的房间事件。
 * 过期窗口之外的序列不能静默丢失，必须通知客户端重新同步直播页面状态。
 */
@Component
public class RoomEventBuffer {
    static final int MAX_EVENTS = 500;
    static final long EVENT_TTL_MINUTES = 10;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RoomEventBuffer(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public RealtimeEvent append(String roomId, String eventId, String type, long timestamp, JsonNode data) {
        Long sequence = redis.opsForValue().increment(sequenceKey(roomId));
        if (sequence == null || sequence < 1) throw new IllegalStateException("无法分配房间事件序号");
        RealtimeEvent event = new RealtimeEvent(eventId, sequence, ConnectionScope.ROOM, roomId, type, timestamp, data);
        try {
            redis.opsForZSet().add(eventsKey(roomId), objectMapper.writeValueAsString(event), sequence.doubleValue());
            redis.opsForZSet().removeRange(eventsKey(roomId), 0, -MAX_EVENTS - 1L);
            redis.expire(eventsKey(roomId), EVENT_TTL_MINUTES, TimeUnit.MINUTES);
            redis.expire(sequenceKey(roomId), EVENT_TTL_MINUTES, TimeUnit.MINUTES);
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("房间实时事件序列化失败", exception);
        }
    }

    public RoomReplayResult replayAfter(String roomId, long lastSeq) {
        Set<ZSetOperations.TypedTuple<String>> buffered = redis.opsForZSet().rangeWithScores(eventsKey(roomId), 0, -1);
        if (buffered == null || buffered.isEmpty()) return RoomReplayResult.replayed(List.of());
        double earliest = buffered.stream().map(ZSetOperations.TypedTuple::getScore)
                .filter(score -> score != null).min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("房间事件缓冲缺少序号"));
        if (lastSeq < (long) earliest - 1) return RoomReplayResult.resyncRequired();
        Set<String> serialized = redis.opsForZSet().rangeByScore(eventsKey(roomId), lastSeq + 1D, Double.MAX_VALUE);
        if (serialized == null || serialized.isEmpty()) return RoomReplayResult.replayed(List.of());
        try {
            List<RealtimeEvent> events = new ArrayList<>(serialized.size());
            for (String value : serialized) events.add(objectMapper.readValue(value, RealtimeEvent.class));
            events.sort(Comparator.comparing(RealtimeEvent::seq));
            return RoomReplayResult.replayed(events);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("房间实时事件反序列化失败", exception);
        }
    }

    private static String sequenceKey(String roomId) { return "ws:room:" + roomId + ":seq"; }
    private static String eventsKey(String roomId) { return "ws:room:" + roomId + ":buffer"; }
}
