package blog.yuanyuan.yuanlive.live.realtime.replay;

import blog.yuanyuan.yuanlive.live.realtime.RealtimeEvent;

import java.util.List;

/** 房间重连补拉的事件集合；发生缺口时不返回部分事件。 */
public record RoomReplayResult(RoomReplayStatus status, List<RealtimeEvent> events) {
    public static RoomReplayResult replayed(List<RealtimeEvent> events) {
        return new RoomReplayResult(RoomReplayStatus.REPLAYED, List.copyOf(events));
    }

    public static RoomReplayResult resyncRequired() {
        return new RoomReplayResult(RoomReplayStatus.RESYNC_REQUIRED, List.of());
    }
}
