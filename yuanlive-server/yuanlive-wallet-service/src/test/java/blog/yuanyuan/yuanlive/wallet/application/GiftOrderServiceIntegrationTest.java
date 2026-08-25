package blog.yuanyuan.yuanlive.wallet.application;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.feign.live.LiveFeignClient;
import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import blog.yuanyuan.yuanlive.wallet.WalletApplication;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderCommand;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.service.GiftOrderService;
import blog.yuanyuan.yuanlive.wallet.application.service.impl.GiftOrderServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = WalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        args = "--spring.config.location=optional:classpath:/wallet-test.yml",
        properties = {
                "spring.main.banner-mode=off",
                "wallet.gift.default-platform-rate=2000",
                "wallet.gift.settlement-rule-version=v1"
        })
@Testcontainers
class GiftOrderServiceIntegrationTest {

    private static final long GIFT_ID = 750000000000000001L;
    private static final long ROOM_ID = 2001L;
    private static final long ANCHOR_ID = 3001L;

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_wallet")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withInitScript("db/wallet-schema.sql")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private GiftOrderService giftOrderService;

    @Autowired
    private GiftOrderServiceImpl giftOrderServiceImpl;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LiveFeignClient liveFeignClient;

    private ExecutorService executorService;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @BeforeEach
    void cleanWalletTables() {
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM anchor_income");
        jdbcTemplate.update("DELETE FROM wallet_ledger");
        jdbcTemplate.update("DELETE FROM gift_order");
        jdbcTemplate.update("DELETE FROM recharge_order");
        jdbcTemplate.update("DELETE FROM wallet_account");
        jdbcTemplate.update("UPDATE gift_catalog SET status = 'ON_SHELF', coin_amount = 100 WHERE id = ?", GIFT_ID);
        ReflectionTestUtils.setField(giftOrderServiceImpl, "platformRate", 2000);
        ReflectionTestUtils.setField(giftOrderServiceImpl, "settlementRuleVersion", "v1");
        when(liveFeignClient.validateGiftRoom(ROOM_ID)).thenReturn(Result.success(
                new GiftRoomValidationResult(ROOM_ID, ANCHOR_ID, 1, true, null)));
    }

    @AfterEach
    void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void successfulGiftPersistsOrderDebitIncomeAndOutboxSnapshots() throws Exception {
        insertAccount(101L, 1_000L);

        GiftOrderResult result = giftOrderService.sendGift(101L, command("gift-success", 2));

        assertThat(result.status()).isEqualTo("PAID");
        assertThat(result.coinAmount()).isEqualTo(200L);
        assertThat(result.anchorIncomeAmount()).isEqualTo(160L);
        assertThat(result.availableCoin()).isEqualTo(800L);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 101")).isEqualTo(800L);

        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT * FROM gift_order WHERE order_no = ?", result.orderNo());
        assertThat(order)
                .containsEntry("sender_id", 101L)
                .containsEntry("request_id", "gift-success")
                .containsEntry("room_id", ROOM_ID)
                .containsEntry("anchor_id", ANCHOR_ID)
                .containsEntry("gift_id", GIFT_ID)
                .containsEntry("gift_count", 2)
                .containsEntry("coin_amount", 200L)
                .containsEntry("exchange_rate", 10L)
                .containsEntry("platform_rate", 2000)
                .containsEntry("platform_coin_amount", 40L)
                .containsEntry("anchor_income_amount", 160L)
                .containsEntry("settlement_rule_version", "v1")
                .containsEntry("status", "PAID");

        Map<String, Object> ledger = jdbcTemplate.queryForMap(
                "SELECT * FROM wallet_ledger WHERE business_no = ?", result.orderNo());
        assertThat(ledger)
                .containsEntry("user_id", 101L)
                .containsEntry("business_type", "GIFT")
                .containsEntry("direction", "DEBIT")
                .containsEntry("amount", 200L)
                .containsEntry("balance_after", 800L)
                .containsEntry("status", "CONFIRMED");

        Map<String, Object> income = jdbcTemplate.queryForMap(
                "SELECT * FROM anchor_income WHERE gift_order_no = ?", result.orderNo());
        assertThat(income)
                .containsEntry("anchor_id", ANCHOR_ID)
                .containsEntry("gross_amount", 200L)
                .containsEntry("platform_fee", 40L)
                .containsEntry("income_amount", 160L)
                .containsEntry("status", "CONFIRMED");

        Map<String, Object> outbox = jdbcTemplate.queryForMap(
                "SELECT * FROM outbox_event WHERE business_id = ?", result.orderNo());
        assertThat(outbox)
                .containsEntry("event_type", "gift.delivered")
                .containsEntry("status", "NEW")
                .containsEntry("retry_count", 0);
        JsonNode payload = objectMapper.readTree((String) outbox.get("payload"));
        assertThat(payload.path("eventId").asText()).isEqualTo(outbox.get("event_id"));
        assertThat(payload.path("orderNo").asText()).isEqualTo(result.orderNo());
        assertThat(payload.path("roomId").asLong()).isEqualTo(ROOM_ID);
        assertThat(payload.path("anchorId").asLong()).isEqualTo(ANCHOR_ID);
        assertThat(payload.path("senderId").asLong()).isEqualTo(101L);
        assertThat(payload.path("giftId").asLong()).isEqualTo(GIFT_ID);
        assertThat(payload.path("giftCode").asText()).isEqualTo("ROCKET");
        assertThat(payload.path("giftName").asText()).isEqualTo("火箭");
        assertThat(payload.path("giftIcon").asText()).isEqualTo("gift/rocket.png");
        assertThat(payload.path("giftCount").asInt()).isEqualTo(2);
        assertThat(payload.path("unitCoinAmount").asLong()).isEqualTo(100L);
        assertThat(payload.path("coinAmount").asLong()).isEqualTo(200L);
        assertThat(payload.path("exchangeRate").asLong()).isEqualTo(10L);
        assertThat(payload.path("platformRate").asInt()).isEqualTo(2000);
        assertThat(payload.path("platformCoinAmount").asLong()).isEqualTo(40L);
        assertThat(payload.path("anchorIncomeAmount").asLong()).isEqualTo(160L);
        assertThat(payload.path("settlementRuleVersion").asText()).isEqualTo("v1");
        assertSideEffectCounts(1L);
    }

    @Test
    void insufficientBalanceRollsBackEveryFinancialSideEffect() {
        insertAccount(102L, 150L);

        assertThatThrownBy(() -> giftOrderService.sendGift(102L, command("gift-insufficient", 2)))
                .isInstanceOf(ApiException.class)
                .hasMessage("钱包余额不足");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 102")).isEqualTo(150L);
        assertSideEffectCounts(0L);
    }

    @Test
    void repeatedRequestReturnsOriginalOrderWithoutDuplicateDebit() {
        insertAccount(103L, 500L);
        GiftOrderCommand command = command("gift-repeat", 1);

        GiftOrderResult first = giftOrderService.sendGift(103L, command);
        GiftOrderResult repeated = giftOrderService.sendGift(103L, command);

        assertThat(repeated).isEqualTo(first);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 103")).isEqualTo(400L);
        assertSideEffectCounts(1L);
    }

    @Test
    void concurrentRequestsCannotOverdrawAccount() throws Exception {
        insertAccount(104L, 100L);
        executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<GiftOrderResult>> futures = new ArrayList<>();
        futures.add(submitConcurrentGift(104L, command("gift-concurrent-a", 1), ready, start));
        futures.add(submitConcurrentGift(104L, command("gift-concurrent-b", 1), ready, start));

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int successes = 0;
        int insufficientFailures = 0;
        for (Future<GiftOrderResult> future : futures) {
            try {
                assertThat(future.get(20, TimeUnit.SECONDS).status()).isEqualTo("PAID");
                successes++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause())
                        .isInstanceOf(ApiException.class)
                        .hasMessage("钱包余额不足");
                insufficientFailures++;
            }
        }

        assertThat(successes).isEqualTo(1);
        assertThat(insufficientFailures).isEqualTo(1);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 104")).isZero();
        assertSideEffectCounts(1L);
    }

    @Test
    void concurrentRepeatedRequestReturnsSameOrderWithOneFinancialSideEffect() throws Exception {
        insertAccount(110L, 500L);
        executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        GiftOrderCommand command = command("gift-concurrent-repeat", 1);
        Future<GiftOrderResult> first = submitConcurrentGift(110L, command, ready, start);
        Future<GiftOrderResult> second = submitConcurrentGift(110L, command, ready, start);

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        GiftOrderResult firstResult = first.get(20, TimeUnit.SECONDS);
        GiftOrderResult secondResult = second.get(20, TimeUnit.SECONDS);
        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 110")).isEqualTo(400L);
        assertSideEffectCounts(1L);
    }

    @Test
    void concurrentRepeatedRequestWithExactBalanceReturnsPaidWinnerToBothCallers() throws Exception {
        insertAccount(117L, 100L);
        executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        GiftOrderCommand command = command("gift-concurrent-exact-balance", 1);
        Future<GiftOrderResult> first = submitConcurrentGift(117L, command, ready, start);
        Future<GiftOrderResult> second = submitConcurrentGift(117L, command, ready, start);

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        GiftOrderResult firstResult = first.get(20, TimeUnit.SECONDS);
        GiftOrderResult secondResult = second.get(20, TimeUnit.SECONDS);
        assertThat(firstResult.status()).isEqualTo("PAID");
        assertThat(secondResult).isEqualTo(firstResult);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 117")).isZero();
        assertSideEffectCounts(1L);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10_000})
    void boundaryPlatformRatePersistsExpectedSettlement(int rate) {
        long userId = rate == 0 ? 111L : 112L;
        insertAccount(userId, 500L);
        ReflectionTestUtils.setField(giftOrderServiceImpl, "platformRate", rate);

        GiftOrderResult result = giftOrderService.sendGift(userId, command("gift-rate-" + rate, 1));

        long expectedPlatformFee = rate == 0 ? 0L : 100L;
        Map<String, Object> order = jdbcTemplate.queryForMap(
                "SELECT platform_rate, platform_coin_amount, anchor_income_amount FROM gift_order WHERE order_no = ?",
                result.orderNo());
        assertThat(order)
                .containsEntry("platform_rate", rate)
                .containsEntry("platform_coin_amount", expectedPlatformFee)
                .containsEntry("anchor_income_amount", 100L - expectedPlatformFee);
        assertThat(result.anchorIncomeAmount()).isEqualTo(100L - expectedPlatformFee);
        assertSideEffectCounts(1L);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10_001})
    void outOfRangePlatformRateCreatesNoFinancialSideEffect(int rate) {
        long userId = rate < 0 ? 113L : 114L;
        insertAccount(userId, 500L);
        ReflectionTestUtils.setField(giftOrderServiceImpl, "platformRate", rate);

        assertThatThrownBy(() -> giftOrderService.sendGift(userId, command("gift-invalid-rate-" + rate, 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("礼物平台抽成配置必须在 0..10000 范围内");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = ?", userId)).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void emptySettlementRuleVersionCreatesNoFinancialSideEffect(String ruleVersion) {
        insertAccount(115L, 500L);
        ReflectionTestUtils.setField(giftOrderServiceImpl, "settlementRuleVersion", ruleVersion);

        assertThatThrownBy(() -> giftOrderService.sendGift(115L, command("gift-empty-rule", 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("礼物结算规则版本不能为空");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 115")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @Test
    void overflowingGiftAmountCreatesNoFinancialSideEffect() {
        insertAccount(116L, 500L);
        jdbcTemplate.update("UPDATE gift_catalog SET coin_amount = ? WHERE id = ?", Long.MAX_VALUE, GIFT_ID);

        assertThatThrownBy(() -> giftOrderService.sendGift(116L, command("gift-overflow", 2)))
                .isInstanceOf(ArithmeticException.class);

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 116")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @Test
    void offShelfGiftCreatesNoFinancialSideEffect() {
        insertAccount(105L, 500L);
        jdbcTemplate.update("UPDATE gift_catalog SET status = 'OFF_SHELF' WHERE id = ?", GIFT_ID);

        assertThatThrownBy(() -> giftOrderService.sendGift(105L, command("gift-off-shelf", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessage("礼物不可用");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 105")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @Test
    void negativePriceGiftCreatesNoFinancialSideEffect() {
        insertAccount(108L, 500L);
        jdbcTemplate.update("UPDATE gift_catalog SET coin_amount = -100 WHERE id = ?", GIFT_ID);

        assertThatThrownBy(() -> giftOrderService.sendGift(108L, command("gift-negative-price", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessage("礼物价格必须大于 0");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 108")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @Test
    void zeroPriceGiftCreatesNoFinancialSideEffect() {
        insertAccount(109L, 500L);
        jdbcTemplate.update("UPDATE gift_catalog SET coin_amount = 0 WHERE id = ?", GIFT_ID);

        assertThatThrownBy(() -> giftOrderService.sendGift(109L, command("gift-zero-price", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessage("礼物价格必须大于 0");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 109")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @Test
    void roomRejectingGiftsCreatesNoFinancialSideEffect() {
        insertAccount(106L, 500L);
        when(liveFeignClient.validateGiftRoom(ROOM_ID)).thenReturn(Result.success(
                new GiftRoomValidationResult(ROOM_ID, ANCHOR_ID, 1, false, "直播间已关闭收礼")));

        assertThatThrownBy(() -> giftOrderService.sendGift(106L, command("gift-room-rejected", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessage("直播间已关闭收礼");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 106")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    @Test
    void mismatchedValidatedRoomCreatesNoFinancialSideEffect() {
        insertAccount(107L, 500L);
        when(liveFeignClient.validateGiftRoom(ROOM_ID)).thenReturn(Result.success(
                new GiftRoomValidationResult(9999L, ANCHOR_ID, 1, true, null)));

        assertThatThrownBy(() -> giftOrderService.sendGift(107L, command("gift-room-mismatch", 1)))
                .isInstanceOf(ApiException.class)
                .hasMessage("直播间校验结果不匹配");

        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 107")).isEqualTo(500L);
        assertSideEffectCounts(0L);
    }

    private Future<GiftOrderResult> submitConcurrentGift(Long senderId, GiftOrderCommand command,
                                                         CountDownLatch ready, CountDownLatch start) {
        return executorService.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发送礼启动超时");
            }
            return giftOrderService.sendGift(senderId, command);
        });
    }

    private GiftOrderCommand command(String requestId, int count) {
        return new GiftOrderCommand(requestId, ROOM_ID, GIFT_ID, count);
    }

    private void insertAccount(Long userId, Long availableCoin) {
        jdbcTemplate.update("INSERT INTO wallet_account (id, user_id, available_coin, version, status) "
                        + "VALUES (?, ?, ?, 0, 'ACTIVE')",
                800000000000000000L + userId, userId, availableCoin);
    }

    private void assertSideEffectCounts(long expected) {
        assertThat(queryForLong("SELECT COUNT(*) FROM gift_order")).isEqualTo(expected);
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger WHERE business_type = 'GIFT' AND direction = 'DEBIT'"))
                .isEqualTo(expected);
        assertThat(queryForLong("SELECT COUNT(*) FROM anchor_income")).isEqualTo(expected);
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event WHERE event_type = 'gift.delivered'"))
                .isEqualTo(expected);
    }

    private long queryForLong(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }
}
