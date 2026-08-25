SET NAMES utf8mb4;

CREATE TABLE wallet_account (
    id BIGINT NOT NULL COMMENT '账户主键',
    user_id BIGINT NOT NULL COMMENT '用户主键',
    available_coin BIGINT NOT NULL DEFAULT 0 COMMENT '可用金币余额',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '账户状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_account_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户消费金币账户';

CREATE TABLE wallet_ledger (
    id BIGINT NOT NULL COMMENT '流水主键',
    ledger_no VARCHAR(64) NOT NULL COMMENT '流水编号',
    account_id BIGINT NOT NULL COMMENT '账户主键',
    user_id BIGINT NOT NULL COMMENT '用户主键',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型',
    business_no VARCHAR(64) NOT NULL COMMENT '业务单号',
    direction VARCHAR(16) NOT NULL COMMENT '资金方向',
    amount BIGINT NOT NULL COMMENT '变动金币数量',
    balance_after BIGINT NOT NULL COMMENT '变动后金币余额',
    status VARCHAR(32) NOT NULL COMMENT '流水状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_ledger_no (ledger_no),
    UNIQUE KEY uk_wallet_ledger_business (business_type, business_no, direction),
    KEY idx_wallet_ledger_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包不可变流水';

CREATE TABLE recharge_order (
    id BIGINT NOT NULL COMMENT '充值订单主键',
    order_no VARCHAR(64) NOT NULL COMMENT '充值订单号',
    user_id BIGINT NOT NULL COMMENT '用户主键',
    coin_amount BIGINT NOT NULL COMMENT '充值金币数量',
    paid_amount_cent BIGINT NOT NULL COMMENT '支付金额分',
    exchange_rate BIGINT NOT NULL COMMENT '金币兑换比例',
    channel VARCHAR(32) NOT NULL COMMENT '支付渠道',
    channel_trade_no VARCHAR(128) NULL COMMENT '渠道交易号',
    status VARCHAR(32) NOT NULL COMMENT '充值订单状态',
    paid_time DATETIME(3) NULL COMMENT '支付完成时间',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_recharge_order_no (order_no),
    UNIQUE KEY uk_recharge_channel_trade_no (channel, channel_trade_no),
    KEY idx_recharge_order_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值订单';

CREATE TABLE gift_catalog (
    id BIGINT NOT NULL COMMENT '礼物主键',
    gift_code VARCHAR(64) NOT NULL COMMENT '礼物编码',
    gift_name VARCHAR(64) NOT NULL COMMENT '礼物名称',
    gift_icon VARCHAR(512) NOT NULL COMMENT '礼物图标地址',
    coin_amount BIGINT NOT NULL COMMENT '礼物单价金币',
    status VARCHAR(32) NOT NULL COMMENT '上架状态',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '展示排序值',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_catalog_code (gift_code),
    KEY idx_gift_catalog_status_sort (status, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼物目录';

CREATE TABLE gift_order (
    id BIGINT NOT NULL COMMENT '礼物订单主键',
    order_no VARCHAR(64) NOT NULL COMMENT '礼物订单号',
    sender_id BIGINT NOT NULL COMMENT '送礼用户主键',
    request_id VARCHAR(64) NOT NULL COMMENT '客户端幂等请求标识',
    room_id BIGINT NOT NULL COMMENT '直播间主键',
    anchor_id BIGINT NOT NULL COMMENT '主播用户主键',
    gift_id BIGINT NOT NULL COMMENT '礼物主键',
    gift_count INT NOT NULL COMMENT '礼物数量',
    coin_amount BIGINT NOT NULL COMMENT '礼物总金币',
    exchange_rate BIGINT NOT NULL COMMENT '金币兑换比例快照',
    platform_rate INT NOT NULL COMMENT '平台抽成比例万分比',
    platform_coin_amount BIGINT NOT NULL COMMENT '平台抽成金币',
    anchor_income_amount BIGINT NOT NULL COMMENT '主播收益金币',
    settlement_rule_version VARCHAR(32) NOT NULL COMMENT '结算规则版本',
    status VARCHAR(32) NOT NULL COMMENT '礼物订单状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gift_order_no (order_no),
    UNIQUE KEY uk_gift_order_sender_request (sender_id, request_id),
    KEY idx_gift_order_room_time (room_id, create_time),
    KEY idx_gift_order_anchor_time (anchor_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='礼物订单';

CREATE TABLE anchor_income (
    id BIGINT NOT NULL COMMENT '收益记录主键',
    income_no VARCHAR(64) NOT NULL COMMENT '收益编号',
    gift_order_no VARCHAR(64) NOT NULL COMMENT '礼物订单号',
    anchor_id BIGINT NOT NULL COMMENT '主播用户主键',
    gross_amount BIGINT NOT NULL COMMENT '礼物毛收益金币',
    platform_fee BIGINT NOT NULL COMMENT '平台抽成金币',
    income_amount BIGINT NOT NULL COMMENT '主播净收益金币',
    status VARCHAR(32) NOT NULL COMMENT '收益状态',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_anchor_income_no (income_no),
    UNIQUE KEY uk_anchor_income_gift_order_no (gift_order_no),
    KEY idx_anchor_income_anchor_time (anchor_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主播礼物收益';

CREATE TABLE outbox_event (
    id BIGINT NOT NULL COMMENT '发件箱记录主键',
    event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
    event_type VARCHAR(64) NOT NULL COMMENT '领域事件类型',
    business_id VARCHAR(64) NOT NULL COMMENT '关联业务标识',
    payload JSON NOT NULL COMMENT '事件负载',
    status VARCHAR(32) NOT NULL COMMENT '发布状态',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    next_retry_at DATETIME(3) NULL COMMENT '下次重试时间',
    lease_owner VARCHAR(128) NULL COMMENT '发布租约持有者',
    lease_until DATETIME(3) NULL COMMENT '发布租约到期时间',
    published_at DATETIME(3) NULL COMMENT '发布确认时间',
    failure_reason VARCHAR(500) NULL COMMENT '最近失败原因',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    KEY idx_outbox_event_pending (status, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包领域事件发件箱';

CREATE TABLE inbox_event (
    id BIGINT NOT NULL COMMENT '收件箱记录主键',
    event_id VARCHAR(64) NOT NULL COMMENT '领域事件唯一标识',
    event_type VARCHAR(64) NOT NULL COMMENT '领域事件类型',
    business_id VARCHAR(64) NOT NULL COMMENT '关联业务标识',
    status VARCHAR(32) NOT NULL COMMENT '事件处理状态',
    processed_at DATETIME(3) NULL COMMENT '处理完成时间',
    failure_reason VARCHAR(500) NULL COMMENT '处理失败原因',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inbox_event_id (event_id),
    KEY idx_inbox_event_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包领域事件收件箱';

CREATE TABLE event_replay_audit (
    id BIGINT NOT NULL COMMENT '重放审计主键',
    event_id VARCHAR(64) NOT NULL COMMENT '重放事件标识',
    event_type VARCHAR(64) NOT NULL COMMENT '重放事件类型',
    business_id VARCHAR(64) NOT NULL COMMENT '关联业务标识',
    operator_id BIGINT NOT NULL COMMENT '重放操作人主键',
    replay_reason VARCHAR(500) NOT NULL COMMENT '重放原因',
    replay_result VARCHAR(32) NOT NULL COMMENT '重放结果',
    replay_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '重放时间',
    PRIMARY KEY (id),
    KEY idx_event_replay_audit_event (event_id, replay_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信重放审计';

INSERT INTO gift_catalog (id, gift_code, gift_name, gift_icon, coin_amount, status, sort_order)
VALUES (750000000000000001, 'ROCKET', '火箭', 'gift/rocket.png', 100, 'ON_SHELF', 1);
