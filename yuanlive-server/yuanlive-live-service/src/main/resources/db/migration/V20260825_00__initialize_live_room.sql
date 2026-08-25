-- 仅为房间预校验建立 LiveRoom 映射所需的最小基表；已有库保持原表结构不被此迁移重建。
CREATE TABLE IF NOT EXISTS live_room (
    id BIGINT NOT NULL COMMENT '房间主键ID',
    anchor_id BIGINT NOT NULL COMMENT '主播ID',
    anchor_name VARCHAR(255) DEFAULT NULL COMMENT '主播名称',
    title VARCHAR(128) NOT NULL COMMENT '直播间标题',
    cover_img VARCHAR(255) DEFAULT NULL COMMENT '直播间封面图URL',
    room_status TINYINT(1) NOT NULL DEFAULT 0 COMMENT '直播状态 0:未开播 1:直播中',
    view_count INT DEFAULT NULL COMMENT '当前在线人数',
    category_id INT DEFAULT NULL COMMENT '分类ID',
    last_start_time DATETIME DEFAULT NULL COMMENT '最近一次开播时间',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    update_time DATETIME DEFAULT NULL COMMENT '更新时间',
    notification VARCHAR(255) DEFAULT NULL COMMENT '直播间公告',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播间表';
