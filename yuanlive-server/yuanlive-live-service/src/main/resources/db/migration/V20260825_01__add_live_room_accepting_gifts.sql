-- 旧库可能已由人工变更提前加入该列；迁移需能重新登记，不能因重复 DDL 阻断服务启动。
SET @accepting_gifts_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'live_room'
      AND column_name = 'accepting_gifts'
);
SET @add_accepting_gifts_sql := IF(
    @accepting_gifts_exists = 0,
    'ALTER TABLE live_room ADD COLUMN accepting_gifts TINYINT(1) NOT NULL DEFAULT 1 COMMENT ''是否允许接收礼物 0:否 1:是'' AFTER room_status',
    'SELECT 1'
);
PREPARE add_accepting_gifts_statement FROM @add_accepting_gifts_sql;
EXECUTE add_accepting_gifts_statement;
DEALLOCATE PREPARE add_accepting_gifts_statement;

SET @gift_validation_index_exists := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'live_room'
      AND index_name = 'idx_live_room_gift_validation'
);
SET @add_gift_validation_index_sql := IF(
    @gift_validation_index_exists = 0,
    'ALTER TABLE live_room ADD INDEX idx_live_room_gift_validation (room_status, accepting_gifts)',
    'SELECT 1'
);
PREPARE add_gift_validation_index_statement FROM @add_gift_validation_index_sql;
EXECUTE add_gift_validation_index_statement;
DEALLOCATE PREPARE add_gift_validation_index_statement;
