package blog.yuanyuan.yuanlive.wallet.application;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.wallet.WalletApplication;
import blog.yuanyuan.yuanlive.wallet.application.dto.RechargeOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.service.RechargeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
        classes = WalletApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        args = "--spring.config.location=optional:classpath:/wallet-test.yml",
        properties = {
                "spring.main.banner-mode=off"
        })
@Testcontainers
class RechargeServiceIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.44-debian")
            .withDatabaseName("yuanlive_wallet")
            .withUsername("yuanlive")
            .withPassword("yuanlive")
            .withInitScript("db/wallet-schema.sql")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private RechargeService rechargeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        jdbcTemplate.update("DELETE FROM wallet_ledger");
        jdbcTemplate.update("DELETE FROM recharge_order");
        jdbcTemplate.update("DELETE FROM wallet_account");
    }

    @AfterEach
    void shutdownExecutor() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void firstSuccessfulSimulationCreditsBalanceLedgerAndOutboxOnce() {
        RechargeOrderResult created = rechargeService.createRechargeOrder(101L, 200L);

        RechargeOrderResult paid = rechargeService.simulatePaid(101L, created.orderNo());

        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.availableCoin()).isEqualTo(200L);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 101")).isEqualTo(200L);
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger WHERE business_no = ?", created.orderNo())).isEqualTo(1L);
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event WHERE business_id = ?", created.orderNo())).isEqualTo(1L);
        assertThat(queryForLong("SELECT COUNT(*) FROM recharge_order WHERE id IS NOT NULL")).isEqualTo(1L);
    }

    @Test
    void repeatedPaidCallbackReturnsOriginalResultWithoutSecondCredit() {
        RechargeOrderResult created = rechargeService.createRechargeOrder(102L, 120L);
        RechargeOrderResult first = rechargeService.simulatePaid(102L, created.orderNo());

        RechargeOrderResult repeated = rechargeService.simulatePaid(102L, created.orderNo());

        assertThat(repeated).isEqualTo(first);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 102")).isEqualTo(120L);
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger WHERE business_no = ?", created.orderNo())).isEqualTo(1L);
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event WHERE business_id = ?", created.orderNo())).isEqualTo(1L);
    }

    @Test
    void callbackFromAnotherUserDoesNotCreditTheOrder() {
        RechargeOrderResult created = rechargeService.createRechargeOrder(103L, 80L);

        assertThatThrownBy(() -> rechargeService.simulatePaid(104L, created.orderNo()))
                .isInstanceOf(ApiException.class)
                .hasMessage("无权操作该充值订单");

        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_account")).isZero();
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger")).isZero();
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event")).isZero();
    }

    @Test
    void callbackForClosedOrderDoesNotCreditTheOrder() {
        RechargeOrderResult created = rechargeService.createRechargeOrder(105L, 80L);
        jdbcTemplate.update("UPDATE recharge_order SET status = 'CLOSED' WHERE order_no = ?", created.orderNo());

        assertThatThrownBy(() -> rechargeService.simulatePaid(105L, created.orderNo()))
                .isInstanceOf(ApiException.class)
                .hasMessage("充值订单状态不允许支付");

        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_account")).isZero();
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger")).isZero();
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event")).isZero();
    }

    @Test
    void concurrentFirstAccountRechargeCreditsBothOrdersWithoutDeadlock() throws Exception {
        RechargeOrderResult firstOrder = rechargeService.createRechargeOrder(106L, 80L);
        RechargeOrderResult secondOrder = rechargeService.createRechargeOrder(106L, 120L);
        executorService = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<RechargeOrderResult> first = submitConcurrentPayment(106L, firstOrder.orderNo(), ready, start);
        Future<RechargeOrderResult> second = submitConcurrentPayment(106L, secondOrder.orderNo(), ready, start);

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(first.get(20, TimeUnit.SECONDS).status()).isEqualTo("PAID");
        assertThat(second.get(20, TimeUnit.SECONDS).status()).isEqualTo("PAID");
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_account WHERE user_id = 106")).isEqualTo(1L);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 106")).isEqualTo(200L);
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger WHERE user_id = 106")).isEqualTo(2L);
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event")).isEqualTo(2L);
    }

    @Test
    void threeConcurrentFirstAccountRechargesAllSucceedWithoutDeadlock() throws Exception {
        RechargeOrderResult firstOrder = rechargeService.createRechargeOrder(107L, 70L);
        RechargeOrderResult secondOrder = rechargeService.createRechargeOrder(107L, 110L);
        RechargeOrderResult thirdOrder = rechargeService.createRechargeOrder(107L, 130L);
        executorService = Executors.newFixedThreadPool(3);
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        Future<RechargeOrderResult> first = submitConcurrentPayment(107L, firstOrder.orderNo(), ready, start);
        Future<RechargeOrderResult> second = submitConcurrentPayment(107L, secondOrder.orderNo(), ready, start);
        Future<RechargeOrderResult> third = submitConcurrentPayment(107L, thirdOrder.orderNo(), ready, start);

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(first.get(20, TimeUnit.SECONDS).status()).isEqualTo("PAID");
        assertThat(second.get(20, TimeUnit.SECONDS).status()).isEqualTo("PAID");
        assertThat(third.get(20, TimeUnit.SECONDS).status()).isEqualTo("PAID");
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_account WHERE user_id = 107")).isEqualTo(1L);
        assertThat(queryForLong("SELECT available_coin FROM wallet_account WHERE user_id = 107")).isEqualTo(310L);
        assertThat(queryForLong("SELECT COUNT(*) FROM wallet_ledger WHERE user_id = 107")).isEqualTo(3L);
        assertThat(queryForLong("SELECT COUNT(*) FROM outbox_event")).isEqualTo(3L);
    }

    private Future<RechargeOrderResult> submitConcurrentPayment(Long userId, String orderNo,
                                                                 CountDownLatch ready, CountDownLatch start) {
        return executorService.submit(() -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("并发充值启动超时");
            }
            return rechargeService.simulatePaid(userId, orderNo);
        });
    }

    private long queryForLong(String sql, Object... arguments) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return value == null ? 0L : value;
    }
}
