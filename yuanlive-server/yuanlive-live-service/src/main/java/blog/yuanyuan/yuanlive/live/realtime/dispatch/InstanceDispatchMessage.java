package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;

/** 已解析到目标实例后的定向实时消息。 */
public record InstanceDispatchMessage(
        String targetInstanceId,
        String targetEpoch,
        Long targetUserId,
        String targetConnectionId,
        RealtimeEvent event) {
}
