package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.entity.wallet.entity.AnchorIncome;
import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletAccount;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import blog.yuanyuan.yuanlive.entity.wallet.entity.RechargeOrder;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletBalanceQueryResult;
import blog.yuanyuan.yuanlive.feign.wallet.dto.WalletOrderQueryResult;
import blog.yuanyuan.yuanlive.feign.live.LiveFeignClient;
import blog.yuanyuan.yuanlive.feign.live.dto.LiveInboxQueryResult;
import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.wallet.mapper.AnchorIncomeMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftOrderMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletAccountMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletLedgerMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.RechargeOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class WalletQueryServiceImpl implements WalletQueryService {
    private final GiftOrderMapper giftOrderMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final AnchorIncomeMapper anchorIncomeMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final WalletAccountMapper walletAccountMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final LiveFeignClient liveFeignClient;

    @Autowired
    public WalletQueryServiceImpl(GiftOrderMapper giftOrderMapper, WalletLedgerMapper walletLedgerMapper,
                                  AnchorIncomeMapper anchorIncomeMapper, OutboxEventMapper outboxEventMapper,
                                  WalletAccountMapper walletAccountMapper, RechargeOrderMapper rechargeOrderMapper,
                                  LiveFeignClient liveFeignClient) {
        this.giftOrderMapper = giftOrderMapper;
        this.walletLedgerMapper = walletLedgerMapper;
        this.anchorIncomeMapper = anchorIncomeMapper;
        this.outboxEventMapper = outboxEventMapper;
        this.walletAccountMapper = walletAccountMapper;
        this.rechargeOrderMapper = rechargeOrderMapper;
        this.liveFeignClient = liveFeignClient;
    }

    public WalletQueryServiceImpl(GiftOrderMapper giftOrderMapper, WalletLedgerMapper walletLedgerMapper,
                                  AnchorIncomeMapper anchorIncomeMapper, OutboxEventMapper outboxEventMapper) {
        this(giftOrderMapper, walletLedgerMapper, anchorIncomeMapper, outboxEventMapper, null, null, null);
    }

    @Override
    public GiftOrderQueryResult getGiftOrder(String orderNo, Long requesterId) {
        GiftOrder order = giftOrderMapper.selectByOrderNo(orderNo);
        if (order == null) throw new ApiException("礼物订单不存在");
        if (requesterId == null || !requesterId.equals(order.getSenderId())) throw new WalletQueryForbiddenException();
        return GiftOrderQueryResult.from(order);
    }

    @Override
    public WalletBalanceQueryResult getBalanceForInternal(Long userId) {
        if (walletAccountMapper == null) throw new IllegalStateException("钱包账户查询未配置");
        WalletAccount account = walletAccountMapper.selectByUserId(userId);
        return account == null ? new WalletBalanceQueryResult(userId, 0L) :
                new WalletBalanceQueryResult(userId, account.getAvailableCoin());
    }

    @Override
    public WalletOrderQueryResult getOrderForInternal(String orderNo) {
        GiftOrder order = giftOrderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            return new WalletOrderQueryResult(order.getOrderNo(), order.getSenderId(), order.getStatus());
        }
        if (rechargeOrderMapper == null) return null;
        RechargeOrder recharge = rechargeOrderMapper.selectByOrderNo(orderNo);
        return recharge == null ? null : new WalletOrderQueryResult(recharge.getOrderNo(), recharge.getUserId(), recharge.getStatus());
    }

    @Override
    public CursorPage<LedgerQueryResult> listLedgers(Long userId, Long beforeId, int limit) {
        int pageSize = normalizeLimit(limit);
        List<WalletLedger> rows = walletLedgerMapper.selectByUserBeforeId(userId, beforeId, pageSize);
        List<LedgerQueryResult> items = rows.stream().map(LedgerQueryResult::from).toList();
        return new CursorPage<>(items, rows.size() == pageSize ? rows.get(rows.size() - 1).getId() : null);
    }

    @Override
    public CursorPage<IncomeQueryResult> listIncomes(Long anchorId, Long beforeId, int limit) {
        int pageSize = normalizeLimit(limit);
        List<AnchorIncome> rows = anchorIncomeMapper.selectByAnchorBeforeId(anchorId, beforeId, pageSize);
        List<IncomeQueryResult> items = rows.stream().map(IncomeQueryResult::from).toList();
        return new CursorPage<>(items, rows.size() == pageSize ? rows.get(rows.size() - 1).getId() : null);
    }

    private int normalizeLimit(int limit) {
        if (limit < 1) return 20;
        return Math.min(limit, 100);
    }

    @Override
    public ReconciliationReport reconcileGiftOrder(String orderNo) {
        GiftOrder order = giftOrderMapper.selectByOrderNo(orderNo);
        ArrayList<String> anomalies = new ArrayList<>();
        if (order == null) {
            anomalies.add("缺少礼物订单");
            return new ReconciliationReport(orderNo, false, false, false, false, null, false, null, anomalies);
        }
        WalletLedger ledger = walletLedgerMapper.selectGiftDebitByBusinessNo(orderNo);
        AnchorIncome income = anchorIncomeMapper.selectByGiftOrderNo(orderNo);
        OutboxEvent outbox = outboxEventMapper.selectByBusinessId(orderNo);
        if (ledger == null) anomalies.add("缺少礼物扣款流水");
        if (income == null) anomalies.add("缺少主播收益记录");
        if (outbox == null) anomalies.add("缺少 Outbox 事件");
        boolean inboxExists = false;
        String inboxStatus = null;
        if (outbox != null && liveFeignClient != null) {
            try {
                Result<LiveInboxQueryResult> response = liveFeignClient.getInbox(outbox.getEventId());
                LiveInboxQueryResult inbox = response == null ? null : response.getData();
                inboxExists = response != null && response.isSuccess() && inbox != null;
                inboxStatus = inbox == null ? null : inbox.status();
                if (!inboxExists) anomalies.add("缺少直播 Inbox 事件");
                else if (!"SUCCEEDED".equals(inbox.status())) anomalies.add("直播 Inbox 未成功");
            } catch (RuntimeException exception) {
                anomalies.add("直播 Inbox 查询失败");
            }
        }
        return new ReconciliationReport(orderNo, true, ledger != null, income != null,
                outbox != null, outbox == null ? null : outbox.getStatus(), inboxExists, inboxStatus, anomalies);
    }
}
