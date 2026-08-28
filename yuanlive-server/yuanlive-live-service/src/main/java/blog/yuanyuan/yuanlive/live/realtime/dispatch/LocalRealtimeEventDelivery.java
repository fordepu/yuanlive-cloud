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

/** 将已路由到本实例的实时事件写入本机 WebSocket 连接。 */
@Component
public class LocalRealtimeEventDelivery {
    private final SessionManager sessionManager;
    private final RealtimeInstanceIdentity identity;
    private final ObjectMapper objectMapper;

    public LocalRealtimeEventDelivery(
            SessionManager sessionManager,
            RealtimeInstanceIdentity identity,
            ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.identity = identity;
        this.objectMapper = objectMapper;
    }

    public RealtimeDeliveryStatus deliver(InstanceDispatchMessage message) {
        if (!identity.instanceId().equals(message.targetInstanceId()) || !identity.epoch().equals(message.targetEpoch())) {
            return RealtimeDeliveryStatus.ROUTE_STALE;
        }
        try {
            String payload = objectMapper.writeValueAsString(message.event());
            if (message.event().scope() == ConnectionScope.APP) {
                return deliverApp(message, payload);
            }
            if (message.event().scope() == ConnectionScope.ROOM) {
                return deliverRoom(message.event().roomId(), payload);
            }
            return RealtimeDeliveryStatus.ROUTE_STALE;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("实时事件序列化失败", exception);
        }
    }

    private RealtimeDeliveryStatus deliverApp(InstanceDispatchMessage message, String payload) {
        if (message.targetUserId() == null || message.targetConnectionId() == null) return RealtimeDeliveryStatus.ROUTE_STALE;
        Channel channel = sessionManager.getUserChannel(message.targetUserId());
        if (channel == null || !channel.isActive()) return RealtimeDeliveryStatus.ROUTE_STALE;
        String currentConnectionId = channel.attr(SessionManager.KEY_CONNECTION_ID).get();
        if (!message.targetConnectionId().equals(currentConnectionId)) return RealtimeDeliveryStatus.ROUTE_STALE;
        channel.writeAndFlush(new TextWebSocketFrame(payload));
        return RealtimeDeliveryStatus.DELIVERED;
    }

    private RealtimeDeliveryStatus deliverRoom(String roomId, String payload) {
        ChannelGroup group = sessionManager.getRoomChannels(roomId);
        if (group == null || group.isEmpty()) return RealtimeDeliveryStatus.ROUTE_STALE;
        group.writeAndFlush(new TextWebSocketFrame(payload));
        return RealtimeDeliveryStatus.DELIVERED;
    }
}
