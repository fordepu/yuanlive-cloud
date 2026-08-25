package blog.yuanyuan.yuanlive.wallet.application.service.impl;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.entity.wallet.entity.AnchorIncome;
import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletAccount;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderCreation;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.service.support.WalletAccountLocker;
import blog.yuanyuan.yuanlive.wallet.mapper.AnchorIncomeMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftOrderMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletAccountMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletLedgerMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GiftOrderTransactionService {

    private static final String ORDER_PAID = "PAID";
    private static final String RECORD_CONFIRMED = "CONFIRMED";
    private static final String OUTBOX_NEW = "NEW";

    private final WalletAccountLocker walletAccountLocker;
    private final WalletAccountMapper walletAccountMapper;
    private final GiftOrderMapper giftOrderMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final AnchorIncomeMapper anchorIncomeMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public GiftOrderResult create(Long senderId, GiftOrderCreation creation) {
        WalletAccount account = walletAccountLocker.lockOrOpen(senderId);
        // 固定按账户、幂等订单的顺序加锁；等待者使用当前读才能看到胜出事务刚提交的订单。
        GiftOrder existing = giftOrderMapper.selectBySenderAndRequestForUpdate(senderId, creation.requestId());
        if (existing != null) {
            WalletLedger ledger = walletLedgerMapper.selectGiftDebitByBusinessNo(existing.getOrderNo());
            if (ledger == null) {
                throw new IllegalStateException("礼物订单缺少扣款流水");
            }
            return GiftOrderResult.from(existing, ledger.getBalanceAfter());
        }
        if (account.getAvailableCoin() < creation.coinAmount()) {
            throw new ApiException("钱包余额不足");
        }

        long balanceAfter = Math.addExact(account.getAvailableCoin(), -creation.coinAmount());
        GiftOrder order = buildOrder(senderId, creation);
        giftOrderMapper.insert(order);
        if (walletAccountMapper.debitIfEnough(account.getId(), creation.coinAmount()) != 1) {
            // 条件扣减是账户行锁之外的第二道保护；失败时抛异常令订单及全部后续记录一并回滚。
            throw new ApiException("钱包余额不足");
        }
        walletLedgerMapper.insert(buildLedger(senderId, account.getId(), order, creation, balanceAfter));
        anchorIncomeMapper.insert(buildIncome(order, creation));
        outboxEventMapper.insert(buildOutbox(senderId, order, creation));
        return GiftOrderResult.from(order, balanceAfter);
    }

    private GiftOrder buildOrder(Long senderId, GiftOrderCreation creation) {
        GiftOrder order = new GiftOrder();
        order.setOrderNo(nextBusinessNo("G"));
        order.setSenderId(senderId);
        order.setRequestId(creation.requestId());
        order.setRoomId(creation.roomId());
        order.setAnchorId(creation.anchorId());
        order.setGiftId(creation.giftId());
        order.setGiftCount(creation.giftCount());
        order.setCoinAmount(creation.coinAmount());
        order.setExchangeRate(creation.exchangeRate());
        order.setPlatformRate(creation.platformRate());
        order.setPlatformCoinAmount(creation.platformCoinAmount());
        order.setAnchorIncomeAmount(creation.anchorIncomeAmount());
        order.setSettlementRuleVersion(creation.settlementRuleVersion());
        order.setStatus(ORDER_PAID);
        return order;
    }

    private WalletLedger buildLedger(Long senderId, Long accountId, GiftOrder order,
                                     GiftOrderCreation creation, long balanceAfter) {
        WalletLedger ledger = new WalletLedger();
        ledger.setLedgerNo(nextBusinessNo("L"));
        ledger.setAccountId(accountId);
        ledger.setUserId(senderId);
        ledger.setBusinessType("GIFT");
        ledger.setBusinessNo(order.getOrderNo());
        ledger.setDirection("DEBIT");
        ledger.setAmount(creation.coinAmount());
        ledger.setBalanceAfter(balanceAfter);
        ledger.setStatus(RECORD_CONFIRMED);
        return ledger;
    }

    private AnchorIncome buildIncome(GiftOrder order, GiftOrderCreation creation) {
        AnchorIncome income = new AnchorIncome();
        income.setIncomeNo(nextBusinessNo("I"));
        income.setGiftOrderNo(order.getOrderNo());
        income.setAnchorId(creation.anchorId());
        income.setGrossAmount(creation.coinAmount());
        income.setPlatformFee(creation.platformCoinAmount());
        income.setIncomeAmount(creation.anchorIncomeAmount());
        income.setStatus(RECORD_CONFIRMED);
        return income;
    }

    private OutboxEvent buildOutbox(Long senderId, GiftOrder order, GiftOrderCreation creation) {
        String eventId = nextBusinessNo("E");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("orderNo", order.getOrderNo());
        payload.put("roomId", creation.roomId());
        payload.put("anchorId", creation.anchorId());
        payload.put("senderId", senderId);
        payload.put("giftId", creation.giftId());
        payload.put("giftCode", creation.giftCode());
        payload.put("giftName", creation.giftName());
        payload.put("giftIcon", creation.giftIcon());
        payload.put("giftCount", creation.giftCount());
        payload.put("unitCoinAmount", creation.unitCoinAmount());
        payload.put("coinAmount", creation.coinAmount());
        payload.put("exchangeRate", creation.exchangeRate());
        payload.put("platformRate", creation.platformRate());
        payload.put("platformCoinAmount", creation.platformCoinAmount());
        payload.put("anchorIncomeAmount", creation.anchorIncomeAmount());
        payload.put("settlementRuleVersion", creation.settlementRuleVersion());

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setEventType("gift.delivered");
        event.setBusinessId(order.getOrderNo());
        event.setPayload(toJson(payload));
        event.setStatus(OUTBOX_NEW);
        event.setRetryCount(0);
        return event;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("送礼事件序列化失败", exception);
        }
    }

    private String nextBusinessNo(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
