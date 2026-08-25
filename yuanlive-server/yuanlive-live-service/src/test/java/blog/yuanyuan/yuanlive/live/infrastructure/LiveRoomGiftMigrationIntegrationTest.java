package blog.yuanyuan.yuanlive.live.infrastructure;

import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import blog.yuanyuan.yuanlive.live.mapper.LiveRoomMapper;
import blog.yuanyuan.yuanlive.live.service.GiftRoomValidationPolicy;
import blog.yuanyuan.yuanlive.live.service.impl.LiveRoomServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class LiveRoomGiftMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> EXISTING_SCHEMA_MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_live")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    private static final MySQLContainer<?> EMPTY_SCHEMA_MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_live")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void migratesExistingLiveRoomWithBaselineAndPreservesDefaultGiftAcceptance() throws Exception {
        try (Connection connection = EXISTING_SCHEMA_MYSQL.createConnection("")) {
            createExistingLiveRoom(connection);
        }

        MigrateResult migrateResult = migrate(EXISTING_SCHEMA_MYSQL);

        assertThat(migrateResult.success).isTrue();
        assertThat(migrateResult.migrationsExecuted).isEqualTo(3);

        assertMigratedLiveRoomIsUsable(EXISTING_SCHEMA_MYSQL);
    }

    @Test
    void migratesEmptySchemaToUsableLiveRoom() throws Exception {
        MigrateResult migrateResult = migrate(EMPTY_SCHEMA_MYSQL);

        assertThat(migrateResult.success).isTrue();
        assertThat(migrateResult.migrationsExecuted).isEqualTo(3);

        assertMigratedLiveRoomIsUsable(EMPTY_SCHEMA_MYSQL);
    }

    private MigrateResult migrate(MySQLContainer<?> mysql) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }

    private void assertMigratedLiveRoomIsUsable(MySQLContainer<?> mysql) throws Exception {
        try (Connection connection = mysql.createConnection("")) {
            assertDefaultValue(connection);
            assertDefaultAppliesToNewRoom(connection);
            assertGiftValidationIndex(connection);
            assertLiveInboxSchema(connection);
            assertLiveInboxColumnComments(connection);
        }
        assertServicePrevalidationReadsMigratedColumn(mysql);
    }

    private void createExistingLiveRoom(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TABLE live_room (
                    id BIGINT NOT NULL,
                    anchor_id BIGINT NOT NULL,
                    anchor_name VARCHAR(255) NULL,
                    title VARCHAR(128) NOT NULL,
                    cover_img VARCHAR(255) NULL,
                    room_status TINYINT(1) NOT NULL DEFAULT 0,
                    view_count INT NULL,
                    category_id INT NULL,
                    last_start_time DATETIME NULL,
                    create_time DATETIME NULL,
                    update_time DATETIME NULL,
                    notification VARCHAR(255) NULL,
                    PRIMARY KEY (id)
                ) ENGINE=InnoDB
                """)) {
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO live_room (id, anchor_id, title, room_status) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, 1L);
            statement.setLong(2, 2L);
            statement.setString(3, "已有直播间");
            statement.setInt(4, 1);
            statement.executeUpdate();
        }
    }

    private void assertDefaultValue(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_default FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'live_room' AND column_name = 'accepting_gifts'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo("1");
            }
        }
    }

    private void assertDefaultAppliesToNewRoom(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO live_room (id, anchor_id, title, room_status) VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, 3L);
            statement.setLong(2, 4L);
            statement.setString(3, "默认收礼直播间");
            statement.setInt(4, 1);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT accepting_gifts FROM live_room WHERE id = 3")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    private void assertGiftValidationIndex(Connection connection) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'live_room' "
                        + "AND index_name = 'idx_live_room_gift_validation' ORDER BY seq_in_index")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
        }
        assertThat(columns).containsExactly("room_status", "accepting_gifts");
    }

    private void assertLiveInboxSchema(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT extra FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'live_inbox_event' AND column_name = 'id'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString(1)).doesNotContain("auto_increment");
            }
        }
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = 'live_inbox_event' "
                        + "AND index_name = 'uk_live_inbox_event_event_id' ORDER BY seq_in_index")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    columns.add(resultSet.getString(1));
                }
            }
        }
        assertThat(columns).containsExactly("event_id");
    }

    private void assertLiveInboxColumnComments(Connection connection) throws SQLException {
        Map<String, String> expectedComments = Map.of(
                "id", "收件箱记录主键",
                "event_id", "领域事件唯一标识",
                "event_type", "领域事件类型",
                "business_id", "关联业务标识",
                "status", "事件处理状态",
                "processed_at", "处理完成时间",
                "failure_reason", "处理失败原因",
                "create_time", "创建时间",
                "update_time", "更新时间");
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT column_name, column_comment FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'live_inbox_event'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String column = resultSet.getString("column_name");
                    if (expectedComments.containsKey(column)) {
                        assertThat(resultSet.getString("column_comment"))
                                .as("字段 %s 的中文注释", column)
                                .isEqualTo(expectedComments.get(column));
                    }
                }
            }
        }
    }

    private void assertServicePrevalidationReadsMigratedColumn(MySQLContainer<?> mysql) throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(LiveRoomMapper.class);
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()));
        factoryBean.setConfiguration(configuration);
        factoryBean.afterPropertiesSet();
        SqlSessionFactory sqlSessionFactory = factoryBean.getObject();

        try (SqlSession session = sqlSessionFactory.openSession()) {
            LiveRoomServiceImpl liveRoomService = new LiveRoomServiceImpl();
            ReflectionTestUtils.setField(liveRoomService, "baseMapper", session.getMapper(LiveRoomMapper.class));
            ReflectionTestUtils.setField(liveRoomService, "giftRoomValidationPolicy", new GiftRoomValidationPolicy());

            GiftRoomValidationResult result = liveRoomService.validateGiftRoom(3L);

            assertThat(result.roomId()).isEqualTo(3L);
            assertThat(result.acceptingGifts()).isTrue();
            assertThat(result.rejectionReason()).isNull();
        }
    }
}
