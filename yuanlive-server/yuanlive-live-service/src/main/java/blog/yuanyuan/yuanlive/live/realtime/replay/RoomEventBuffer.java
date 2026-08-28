package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 保存短期房间事件，用于 ROOM WebSocket 断线后按 seq 补拉。
 * Redis INCR 是跨实例唯一的序列来源；事件窗口过期后客户端必须重新拉取页面状态，不能用残缺弹幕继续拼接。
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
        if (sequence == null || sequence < 1) {
            throw new IllegalStateException("无法分配房间事件序号");
        }
        RealtimeEvent event = new RealtimeEvent(eventId, sequence, ConnectionScope.ROOM, roomId, type, timestamp, data);
        try {
            redis.opsForZSet().add(eventsKey(roomId), objectMapper.writeValueAsString(event), sequence.doubleValue());
            // 只删除窗口外的旧事件，避免每次写入都全量扫描房间历史。
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
        if (buffered == null || buffered.isEmpty()) {
            return RoomReplayResult.replayed(List.of());
        }
        double earliestSeq = buffered.stream()
                .map(ZSetOperations.TypedTuple::getScore)
                .filter(score -> score != null)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("房间事件缓冲缺少序号"));
        if (lastSeq < (long) earliestSeq - 1) {
            return RoomReplayResult.resyncRequired();
        }
        Set<String> serializedEvents = redis.opsForZSet().rangeByScore(eventsKey(roomId), lastSeq + 1D, Double.MAX_VALUE);
        if (serializedEvents == null || serializedEvents.isEmpty()) {
            return RoomReplayResult.replayed(List.of());
        }
        try {
            List<RealtimeEvent> events = new ArrayList<>(serializedEvents.size());
            for (String serializedEvent : serializedEvents) {
                events.add(objectMapper.readValue(serializedEvent, RealtimeEvent.class));
            }
            events.sort(Comparator.comparing(RealtimeEvent::seq));
            return RoomReplayResult.replayed(events);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("房间实时事件反序列化失败", exception);
        }
    }

    private static String sequenceKey(String roomId) {
        return "ws:room:" + roomId + ":seq";
    }

    private static String eventsKey(String roomId) {
        return "ws:room:" + roomId + ":events";
    }
}
