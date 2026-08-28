package blog.yuanyuan.yuanlive.live.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import cn.hutool.core.util.StrUtil;

/**
 * 跨实例和客户端共用的实时事件信封。
 * ROOM 事件必须同时携带房间号和序号，避免路由与断线补偿落入错误房间。
 */
public record RealtimeEvent(
        String eventId,
        Long seq,
        ConnectionScope scope,
        String roomId,
        String type,
        Long timestamp,
        JsonNode data) {

    public RealtimeEvent {
        if (StrUtil.isBlank(eventId)) throw new IllegalArgumentException("eventId不能为空");
        if (scope == null) throw new IllegalArgumentException("scope不能为空");
        if (StrUtil.isBlank(type)) throw new IllegalArgumentException("type不能为空");
        if (timestamp == null) throw new IllegalArgumentException("timestamp不能为空");
        if (scope == ConnectionScope.ROOM && (StrUtil.isBlank(roomId) || seq == null || seq < 1)) {
            throw new IllegalArgumentException("ROOM事件必须携带roomId和正序号seq");
        }
    }
}
