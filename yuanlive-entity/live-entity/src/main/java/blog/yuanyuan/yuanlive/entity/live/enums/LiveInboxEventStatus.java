package blog.yuanyuan.yuanlive.entity.live.enums;

/**
 * 直播侧 Inbox 的稳定处理状态，便于死信重放和人工审计区分未完成与已完成事件。
 */
public enum LiveInboxEventStatus {
    PROCESSING,
    SUCCEEDED,
    FAILED
}
