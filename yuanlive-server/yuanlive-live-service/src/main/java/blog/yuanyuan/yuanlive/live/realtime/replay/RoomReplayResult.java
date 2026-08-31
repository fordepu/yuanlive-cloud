package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;

import java.util.List;

/** 发生序列缺口时不返回部分事件，调用方应要求客户端重新拉取房间状态。 */
public record RoomReplayResult(RoomReplayStatus status, List<RealtimeEvent> events) {
    public static RoomReplayResult replayed(List<RealtimeEvent> events) {
        return new RoomReplayResult(RoomReplayStatus.REPLAYED, List.copyOf(events));
    }

    public static RoomReplayResult resyncRequired() {
        return new RoomReplayResult(RoomReplayStatus.RESYNC_REQUIRED, List.of());
    }
}
