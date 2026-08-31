package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.message.Message;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** APP 通知不经过房间 fanout，按用户 Redis 路由定向投递。 */
@Component
public class AppRealtimeEventPublisher {
    private final RealtimeEventDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public AppRealtimeEventPublisher(RealtimeEventDispatcher dispatcher, ObjectMapper objectMapper) {
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    public void publish(Long userId, Message message) {
        if (userId == null || message == null || message.getCmd() == null) return;
        String eventId = message.getMsgId() == null || message.getMsgId().isBlank() ? UUID.randomUUID().toString() : message.getMsgId();
        long timestamp = message.getTimestamp() == null ? System.currentTimeMillis() / 1000 : message.getTimestamp();
        dispatcher.dispatchToApp(userId, new RealtimeEvent(eventId, null, ConnectionScope.APP, null,
                message.getCmd().name(), timestamp, objectMapper.valueToTree(message)));
    }
}
