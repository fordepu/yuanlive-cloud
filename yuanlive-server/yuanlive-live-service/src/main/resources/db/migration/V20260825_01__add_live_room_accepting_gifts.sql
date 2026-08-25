ALTER TABLE live_room
    ADD COLUMN accepting_gifts TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许接收礼物 0:否 1:是' AFTER room_status,
    ADD INDEX idx_live_room_gift_validation (room_status, accepting_gifts);
