package blog.yuanyuan.yuanlive.wallet.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import me.ahoo.cosid.annotation.CosId;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class WalletSchemaIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_wallet")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withInitScript("db/wallet-schema.sql")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void createsWalletTablesWithRequiredUniqueKeysAndInitialGift() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            assertTableExists(connection, "wallet_account");
            assertTableExists(connection, "wallet_ledger");
            assertTableExists(connection, "recharge_order");
            assertTableExists(connection, "gift_catalog");
            assertTableExists(connection, "gift_order");
            assertTableExists(connection, "anchor_income");
            assertTableExists(connection, "outbox_event");
            assertTableExists(connection, "inbox_event");

            assertUniqueIndexExists(connection, "wallet_account", "uk_wallet_account_user_id");
            assertUniqueIndexExists(connection, "gift_order", "uk_gift_order_sender_request");
            assertUniqueIndexExists(connection, "outbox_event", "uk_outbox_event_id");
            assertUniqueIndexExists(connection, "inbox_event", "uk_inbox_event_id");

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM gift_catalog WHERE status = 'ON_SHELF' AND coin_amount > 0")) {
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertTrue(resultSet.getLong(1) > 0, "至少需要一条可送出的初始礼物");
                }
            }
        }
    }

    @Test
    void rejectsDuplicateGiftRequestAndDuplicateEventId() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            insertGiftOrder(connection, 10001L, 100L, "request-001");
            assertThrows(SQLException.class, () -> insertGiftOrder(connection, 10002L, 100L, "request-001"));

            insertOutboxEvent(connection, 20001L, "event-001");
            assertThrows(SQLException.class, () -> insertOutboxEvent(connection, 20002L, "event-001"));
        }
    }

    @Test
    void configuresWalletEntityIdsWithCosIdInsteadOfDatabaseAutoIncrement() throws ReflectiveOperationException {
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.WalletAccount");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.RechargeOrder");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.GiftCatalog");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.AnchorIncome");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent");
        assertCosIdPrimaryKey("blog.yuanyuan.yuanlive.entity.wallet.entity.InboxEvent");
    }

    @Test
    void upgradesAllWalletCommentsWithoutLosingExistingDataOrUniqueKeys() throws SQLException {
        try (Connection connection = MYSQL.createConnection("")) {
            insertGiftOrder(connection, 30001L, 300L, "comment-upgrade-request");
            Map<String, Map<String, ColumnDefinition>> originalDefinitions = readColumnDefinitions(connection);

            assertWalletColumnComments(connection);
            assertWalletTableComments(connection);
            executeCommentUpgrade(connection);
            executeCommentUpgrade(connection);

            assertEquals(originalDefinitions, readColumnDefinitions(connection), "升级注释不能改变既有列定义");
            assertWalletColumnComments(connection);
            assertWalletTableComments(connection);
            assertGiftOrderStillExists(connection, 30001L);
            assertThrows(SQLException.class,
                    () -> insertGiftOrder(connection, 30002L, 300L, "comment-upgrade-request"));
        }
    }

    private void assertTableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1L, resultSet.getLong(1), "缺少数据表: " + tableName);
            }
        }
    }

    private void assertUniqueIndexExists(Connection connection, String tableName, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? AND non_unique = 0")) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertTrue(resultSet.getLong(1) > 0, "缺少唯一索引: " + indexName);
            }
        }
    }

    private void insertGiftOrder(Connection connection, long id, long senderId, String requestId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO gift_order (id, order_no, sender_id, request_id, room_id, anchor_id, gift_id, gift_count, "
                        + "coin_amount, exchange_rate, platform_rate, platform_coin_amount, anchor_income_amount, "
                        + "settlement_rule_version, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, "G" + id);
            statement.setLong(3, senderId);
            statement.setString(4, requestId);
            statement.setLong(5, 1L);
            statement.setLong(6, 2L);
            statement.setLong(7, 1L);
            statement.setInt(8, 1);
            statement.setLong(9, 100L);
            statement.setLong(10, 10L);
            statement.setInt(11, 2000);
            statement.setLong(12, 20L);
            statement.setLong(13, 80L);
            statement.setString(14, "v1");
            statement.setString(15, "PAID");
            statement.executeUpdate();
        }
    }

    private void insertOutboxEvent(Connection connection, long id, String eventId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO outbox_event (id, event_id, event_type, business_id, payload, status, retry_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setLong(1, id);
            statement.setString(2, eventId);
            statement.setString(3, "gift.delivered");
            statement.setString(4, "G" + id);
            statement.setString(5, "{}");
            statement.setString(6, "NEW");
            statement.setInt(7, 0);
            statement.executeUpdate();
        }
    }

    private void assertCosIdPrimaryKey(String className) throws ReflectiveOperationException {
        Class<?> entityClass = Class.forName(className);
        TableId tableId = entityClass.getDeclaredField("id").getAnnotation(TableId.class);
        assertTrue(entityClass.getDeclaredField("id").isAnnotationPresent(CosId.class));
        assertEquals(IdType.INPUT, tableId.type(), className + " 必须由 CosId 在写入前生成主键");
    }

    private void executeCommentUpgrade(Connection connection) {
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/V20260825_01__add_wallet_comments.sql"));
    }

    private void assertWalletColumnComments(Connection connection) throws SQLException {
        Map<String, Map<String, String>> expectedComments = walletColumnComments();
        for (Map.Entry<String, Map<String, String>> table : expectedComments.entrySet()) {
            Map<String, String> actualComments = readColumnComments(connection, table.getKey());
            assertEquals(table.getValue(), actualComments, table.getKey() + " 的字段中文注释不完整或不正确");
        }
    }

    private Map<String, String> readColumnComments(Connection connection, String tableName) throws SQLException {
        Map<String, String> comments = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name, column_comment FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    comments.put(resultSet.getString("column_name"), resultSet.getString("column_comment"));
                }
            }
        }
        return comments;
    }

    private Map<String, Map<String, ColumnDefinition>> readColumnDefinitions(Connection connection) throws SQLException {
        Map<String, Map<String, ColumnDefinition>> definitions = new LinkedHashMap<>();
        for (String tableName : walletColumnComments().keySet()) {
            Map<String, ColumnDefinition> tableDefinitions = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT column_name, column_type, is_nullable, column_default, extra FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = ? ORDER BY ordinal_position")) {
                statement.setString(1, tableName);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        tableDefinitions.put(resultSet.getString("column_name"), new ColumnDefinition(
                                resultSet.getString("column_type"),
                                resultSet.getString("is_nullable"),
                                resultSet.getString("column_default"),
                                resultSet.getString("extra")));
                    }
                }
            }
            definitions.put(tableName, tableDefinitions);
        }
        return definitions;
    }

    private void assertWalletTableComments(Connection connection) throws SQLException {
        Map<String, String> expectedComments = Map.of(
                "wallet_account", "用户消费金币账户",
                "wallet_ledger", "钱包不可变流水",
                "recharge_order", "充值订单",
                "gift_catalog", "礼物目录",
                "gift_order", "礼物订单",
                "anchor_income", "主播礼物收益",
                "outbox_event", "钱包领域事件发件箱",
                "inbox_event", "钱包领域事件收件箱",
                "event_replay_audit", "死信重放审计");
        for (Map.Entry<String, String> table : expectedComments.entrySet()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT table_comment FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?")) {
                statement.setString(1, table.getKey());
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next(), "缺少数据表: " + table.getKey());
                    assertEquals(table.getValue(), resultSet.getString(1), table.getKey() + " 的表注释必须是正确中文");
                }
            }
        }
    }

    private void assertGiftOrderStillExists(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM gift_order WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(1L, resultSet.getLong(1), "升级注释不能删除既有礼物订单数据");
            }
        }
    }

    private Map<String, Map<String, String>> walletColumnComments() {
        return Map.of(
                "wallet_account", Map.ofEntries(
                        Map.entry("id", "账户主键"), Map.entry("user_id", "用户主键"),
                        Map.entry("available_coin", "可用金币余额"), Map.entry("version", "乐观锁版本号"),
                        Map.entry("status", "账户状态"), Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "wallet_ledger", Map.ofEntries(
                        Map.entry("id", "流水主键"), Map.entry("ledger_no", "流水编号"), Map.entry("account_id", "账户主键"),
                        Map.entry("user_id", "用户主键"), Map.entry("business_type", "业务类型"), Map.entry("business_no", "业务单号"),
                        Map.entry("direction", "资金方向"), Map.entry("amount", "变动金币数量"), Map.entry("balance_after", "变动后金币余额"),
                        Map.entry("status", "流水状态"), Map.entry("create_time", "创建时间")),
                "recharge_order", Map.ofEntries(
                        Map.entry("id", "充值订单主键"), Map.entry("order_no", "充值订单号"), Map.entry("user_id", "用户主键"),
                        Map.entry("coin_amount", "充值金币数量"), Map.entry("paid_amount_cent", "支付金额分"), Map.entry("exchange_rate", "金币兑换比例"),
                        Map.entry("channel", "支付渠道"), Map.entry("channel_trade_no", "渠道交易号"), Map.entry("status", "充值订单状态"),
                        Map.entry("paid_time", "支付完成时间"), Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "gift_catalog", Map.ofEntries(
                        Map.entry("id", "礼物主键"), Map.entry("gift_code", "礼物编码"), Map.entry("gift_name", "礼物名称"),
                        Map.entry("gift_icon", "礼物图标地址"), Map.entry("coin_amount", "礼物单价金币"), Map.entry("status", "上架状态"),
                        Map.entry("sort_order", "展示排序值"), Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "gift_order", Map.ofEntries(
                        Map.entry("id", "礼物订单主键"), Map.entry("order_no", "礼物订单号"), Map.entry("sender_id", "送礼用户主键"), Map.entry("request_id", "客户端幂等请求标识"),
                        Map.entry("room_id", "直播间主键"), Map.entry("anchor_id", "主播用户主键"), Map.entry("gift_id", "礼物主键"), Map.entry("gift_count", "礼物数量"),
                        Map.entry("coin_amount", "礼物总金币"), Map.entry("exchange_rate", "金币兑换比例快照"), Map.entry("platform_rate", "平台抽成比例万分比"),
                        Map.entry("platform_coin_amount", "平台抽成金币"), Map.entry("anchor_income_amount", "主播收益金币"), Map.entry("settlement_rule_version", "结算规则版本"),
                        Map.entry("status", "礼物订单状态"), Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "anchor_income", Map.ofEntries(
                        Map.entry("id", "收益记录主键"), Map.entry("income_no", "收益编号"), Map.entry("gift_order_no", "礼物订单号"), Map.entry("anchor_id", "主播用户主键"),
                        Map.entry("gross_amount", "礼物毛收益金币"), Map.entry("platform_fee", "平台抽成金币"), Map.entry("income_amount", "主播净收益金币"),
                        Map.entry("status", "收益状态"), Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "outbox_event", Map.ofEntries(
                        Map.entry("id", "发件箱记录主键"), Map.entry("event_id", "领域事件唯一标识"), Map.entry("event_type", "领域事件类型"), Map.entry("business_id", "关联业务标识"),
                        Map.entry("payload", "事件负载"), Map.entry("status", "发布状态"), Map.entry("retry_count", "已重试次数"), Map.entry("next_retry_at", "下次重试时间"),
                        Map.entry("lease_owner", "发布租约持有者"), Map.entry("lease_until", "发布租约到期时间"), Map.entry("published_at", "发布确认时间"), Map.entry("failure_reason", "最近失败原因"),
                        Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "inbox_event", Map.ofEntries(
                        Map.entry("id", "收件箱记录主键"), Map.entry("event_id", "领域事件唯一标识"), Map.entry("event_type", "领域事件类型"), Map.entry("business_id", "关联业务标识"),
                        Map.entry("status", "事件处理状态"), Map.entry("processed_at", "处理完成时间"), Map.entry("failure_reason", "处理失败原因"), Map.entry("create_time", "创建时间"), Map.entry("update_time", "更新时间")),
                "event_replay_audit", Map.ofEntries(
                        Map.entry("id", "重放审计主键"), Map.entry("event_id", "重放事件标识"), Map.entry("event_type", "重放事件类型"), Map.entry("business_id", "关联业务标识"),
                        Map.entry("operator_id", "重放操作人主键"), Map.entry("replay_reason", "重放原因"), Map.entry("replay_result", "重放结果"), Map.entry("replay_time", "重放时间")));
    }

    private record ColumnDefinition(String columnType, String nullable, String defaultValue, String extra) {
    }
}
