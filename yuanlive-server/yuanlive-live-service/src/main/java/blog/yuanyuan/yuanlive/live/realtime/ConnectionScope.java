package blog.yuanyuan.yuanlive.live.realtime;

/** WebSocket 连接的职责范围。 */
public enum ConnectionScope {
    APP,
    ROOM,
    /**
     * 旧客户端尚未携带 scope 时的过渡状态。
     * 保持其首帧 JOIN_ROOM 的既有语义，待所有客户端升级后再移除。
     */
    LEGACY
}
