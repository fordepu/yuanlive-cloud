package blog.yuanyuan.yuanlive.live.realtime.dispatch;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;

/** 已解析到目标实例后的实时消息，不信任任何客户端提交的实例字段。 */
public record InstanceDispatchMessage(
        String targetInstanceId,
        String targetEpoch,
        Long targetUserId,
        String targetConnectionId,
        RealtimeEvent event,
        int retryAttempt) {
}
