package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.message.Message;
import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;
import blog.yuanyuan.yuanlive.live.realtime.dispatch.RealtimeEventDispatcher;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 房间展示事件的唯一入口：先持久短期补拉缓冲，再按实际承载实例定向下发。 */
@Component
public class RoomRealtimeEventPublisher {
    private final RoomEventBuffer buffer;
    private final RealtimeEventDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public RoomRealtimeEventPublisher(RoomEventBuffer buffer, RealtimeEventDispatcher dispatcher, ObjectMapper objectMapper) {
        this.buffer = buffer;
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper;
    }

    public RealtimeEvent publish(Message message) {
        if (message == null || StrUtil.isBlank(message.getRoomId()) || message.getCmd() == null) {
            throw new IllegalArgumentException("房间实时消息缺少roomId或cmd");
        }
        String eventId = StrUtil.isBlank(message.getMsgId()) ? UUID.randomUUID().toString() : message.getMsgId();
        long timestamp = message.getTimestamp() == null ? System.currentTimeMillis() / 1000 : message.getTimestamp();
        RealtimeEvent event = buffer.append(message.getRoomId(), eventId, message.getCmd().name(), timestamp,
                objectMapper.valueToTree(message));
        dispatcher.dispatchToRoom(message.getRoomId(), event);
        return event;
    }
}
