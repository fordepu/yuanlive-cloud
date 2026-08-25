CREATE TABLE IF NOT EXISTS live_inbox_event (
    id BIGINT NOT NULL COMMENT '收件箱记录主键',
    event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
    event_type VARCHAR(64) NOT NULL COMMENT '领域事件类型',
    business_id VARCHAR(64) NOT NULL COMMENT '关联业务标识',
    status VARCHAR(32) NOT NULL COMMENT '事件处理状态',
    processed_at DATETIME NULL COMMENT '处理完成时间',
    failure_reason VARCHAR(512) NULL COMMENT '处理失败原因',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_inbox_event_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播礼物事件收件箱';
