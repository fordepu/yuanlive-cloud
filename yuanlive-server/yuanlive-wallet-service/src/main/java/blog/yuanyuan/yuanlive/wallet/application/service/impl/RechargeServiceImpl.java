package blog.yuanyuan.yuanlive.wallet.application.service.impl;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent;
import blog.yuanyuan.yuanlive.entity.wallet.entity.RechargeOrder;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletAccount;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import blog.yuanyuan.yuanlive.entity.wallet.enums.RechargeOrderStatus;
import blog.yuanyuan.yuanlive.wallet.application.dto.RechargeOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.dto.WalletAccountResult;
import blog.yuanyuan.yuanlive.wallet.application.service.RechargeService;
import blog.yuanyuan.yuanlive.wallet.application.service.support.WalletAccountLocker;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.RechargeOrderMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletAccountMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletLedgerMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RechargeServiceImpl implements RechargeService {

    private static final long COIN_PER_YUAN = 10L;
    private static final long CENT_PER_YUAN = 100L;
    private static final String SIMULATED_CHANNEL = "SIMULATED";
    private static final String LEDGER_CREDIT = "CREDIT";
    private static final String LEDGER_CONFIRMED = "CONFIRMED";
    private static final String OUTBOX_NEW = "NEW";

    private final RechargeOrderMapper rechargeOrderMapper;
    private final WalletAccountMapper walletAccountMapper;
    private final WalletAccountLocker walletAccountLocker;
    private final WalletLedgerMapper walletLedgerMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderResult createRechargeOrder(Long userId, Long coinAmount) {
        requirePositive(userId, "用户不能为空");
        requirePositive(coinAmount, "充值金币必须大于 0");

        RechargeOrder order = new RechargeOrder();
        order.setOrderNo(nextBusinessNo("R"));
        order.setUserId(userId);
        order.setCoinAmount(coinAmount);
        order.setPaidAmountCent(toPaidAmountCent(coinAmount));
        order.setExchangeRate(COIN_PER_YUAN);
        order.setChannel(SIMULATED_CHANNEL);
        order.setStatus(RechargeOrderStatus.PENDING_PAYMENT.name());
        rechargeOrderMapper.insert(order);
        return new RechargeOrderResult(order.getOrderNo(), order.getStatus(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RechargeOrderResult simulatePaid(Long userId, String orderNo) {
        RechargeOrder order = rechargeOrderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new ApiException("充值订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new ApiException("无权操作该充值订单");
        }

        WalletAccount account = walletAccountLocker.lockOrOpen(userId);
        if (RechargeOrderStatus.PAID.name().equals(order.getStatus())) {
            return new RechargeOrderResult(order.getOrderNo(), order.getStatus(), account.getAvailableCoin());
        }
        if (!RechargeOrderStatus.PENDING_PAYMENT.name().equals(order.getStatus())) {
            throw new ApiException("充值订单状态不允许支付");
        }

        long balanceAfter = Math.addExact(account.getAvailableCoin(), order.getCoinAmount());
        if (walletAccountMapper.credit(account.getId(), order.getCoinAmount()) != 1) {
            throw new IllegalStateException("钱包余额更新失败");
        }
        account.setAvailableCoin(balanceAfter);

        order.setStatus(RechargeOrderStatus.PAID.name());
        order.setChannelTradeNo("SIM-" + order.getOrderNo());
        order.setPaidTime(LocalDateTime.now());
        if (rechargeOrderMapper.updateById(order) != 1) {
            throw new IllegalStateException("充值订单状态更新失败");
        }

        walletLedgerMapper.insert(buildRechargeLedger(order, account, balanceAfter));
        outboxEventMapper.insert(buildRechargeOutbox(order));
        return new RechargeOrderResult(order.getOrderNo(), order.getStatus(), balanceAfter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletAccountResult getAccount(Long userId) {
        WalletAccount account = walletAccountLocker.lockOrOpen(userId);
        return new WalletAccountResult(account.getUserId(), account.getAvailableCoin(), account.getStatus());
    }

    private WalletLedger buildRechargeLedger(RechargeOrder order, WalletAccount account, long balanceAfter) {
        WalletLedger ledger = new WalletLedger();
        ledger.setLedgerNo(nextBusinessNo("L"));
        ledger.setAccountId(account.getId());
        ledger.setUserId(order.getUserId());
        ledger.setBusinessType("RECHARGE");
        ledger.setBusinessNo(order.getOrderNo());
        ledger.setDirection(LEDGER_CREDIT);
        ledger.setAmount(order.getCoinAmount());
        ledger.setBalanceAfter(balanceAfter);
        ledger.setStatus(LEDGER_CONFIRMED);
        return ledger;
    }

    private OutboxEvent buildRechargeOutbox(RechargeOrder order) {
        String eventId = nextBusinessNo("E");
        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType("wallet.recharged");
        event.setBusinessId(order.getOrderNo());
        event.setPayload(toJson(Map.of(
                "eventId", eventId,
                "orderNo", order.getOrderNo(),
                "userId", order.getUserId(),
                "coinAmount", order.getCoinAmount())));
        event.setStatus(OUTBOX_NEW);
        event.setRetryCount(0);
        return event;
    }

    private long toPaidAmountCent(long coinAmount) {
        long centPerCoin = CENT_PER_YUAN / COIN_PER_YUAN;
        return Math.multiplyExact(coinAmount, centPerCoin);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("充值事件序列化失败", exception);
        }
    }

    private String nextBusinessNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new ApiException(message);
        }
    }
}
