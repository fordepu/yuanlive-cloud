package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.routing.RealtimeInstanceIdentity;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/** 校验路由与当前进程身份后写入本机连接，并以 eventId 抑制路由重叠造成的重复展示。 */
@Component
public class LocalRealtimeEventDelivery {
    private static final int MAX_EVENT_IDS = 10_000;
    private final SessionManager sessions;
    private final RealtimeInstanceIdentity identity;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Boolean> deliveredEventIds = new ConcurrentHashMap<>();

    public LocalRealtimeEventDelivery(SessionManager sessions, RealtimeInstanceIdentity identity, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.identity = identity;
        this.objectMapper = objectMapper;
    }

    public RealtimeDeliveryStatus deliver(InstanceDispatchMessage message) {
        if (!identity.instanceId().equals(message.targetInstanceId()) || !identity.epoch().equals(message.targetEpoch())) {
            return RealtimeDeliveryStatus.ROUTE_STALE;
        }
        if (deliveredEventIds.containsKey(message.event().eventId())) {
            return RealtimeDeliveryStatus.DUPLICATE;
        }
        try {
            String payload = objectMapper.writeValueAsString(message.event());
            RealtimeDeliveryStatus result = message.event().scope() == ConnectionScope.APP
                    ? deliverApp(message, payload)
                    : deliverRoom(message.event().roomId(), payload);
            // 仅在真正写入本机连接后去重；ROUTE_STALE 必须保留给发送方刷新目录后重投。
            if (result == RealtimeDeliveryStatus.DELIVERED) {
                deliveredEventIds.put(message.event().eventId(), Boolean.TRUE);
                if (deliveredEventIds.size() > MAX_EVENT_IDS) deliveredEventIds.clear();
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("实时事件序列化失败", exception);
        }
    }

    private RealtimeDeliveryStatus deliverApp(InstanceDispatchMessage message, String payload) {
        if (message.targetUserId() == null || message.targetConnectionId() == null) return RealtimeDeliveryStatus.ROUTE_STALE;
        Channel channel = sessions.getUserChannel(message.targetUserId());
        if (channel == null || !channel.isActive()) return RealtimeDeliveryStatus.ROUTE_STALE;
        if (!message.targetConnectionId().equals(channel.attr(SessionManager.KEY_CONNECTION_ID).get())) return RealtimeDeliveryStatus.ROUTE_STALE;
        channel.writeAndFlush(new TextWebSocketFrame(payload));
        return RealtimeDeliveryStatus.DELIVERED;
    }

    private RealtimeDeliveryStatus deliverRoom(String roomId, String payload) {
        ChannelGroup group = sessions.getRoomChannels(roomId);
        if (group == null || group.isEmpty()) return RealtimeDeliveryStatus.ROUTE_STALE;
        group.writeAndFlush(new TextWebSocketFrame(payload));
        return RealtimeDeliveryStatus.DELIVERED;
    }
}
