package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import blog.yuanyuan.yuanlive.wallet.mapper.AnchorIncomeMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftOrderMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletLedgerMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalletQueryServiceTest {

    @Test
    void refusesToExposeAnotherUsersGiftOrder() {
        GiftOrderMapper giftOrderMapper = mock(GiftOrderMapper.class);
        GiftOrder order = new GiftOrder();
        order.setOrderNo("G-1");
        order.setSenderId(10L);
        when(giftOrderMapper.selectByOrderNo("G-1")).thenReturn(order);

        WalletQueryService service = new WalletQueryServiceImpl(
                giftOrderMapper, mock(WalletLedgerMapper.class), mock(AnchorIncomeMapper.class),
                mock(OutboxEventMapper.class));

        assertThrows(WalletQueryForbiddenException.class, () -> service.getGiftOrder("G-1", 11L));
    }

    @Test
    void reportsMissingLedgerIncomeAndOutboxWithoutChangingData() {
        GiftOrderMapper giftOrderMapper = mock(GiftOrderMapper.class);
        WalletLedgerMapper ledgerMapper = mock(WalletLedgerMapper.class);
        AnchorIncomeMapper incomeMapper = mock(AnchorIncomeMapper.class);
        OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
        GiftOrder order = new GiftOrder();
        order.setOrderNo("G-2");
        order.setSenderId(10L);
        order.setAnchorId(20L);
        when(giftOrderMapper.selectByOrderNo("G-2")).thenReturn(order);
        when(ledgerMapper.selectGiftDebitByBusinessNo("G-2")).thenReturn(null);
        when(incomeMapper.selectByGiftOrderNo("G-2")).thenReturn(null);
        when(outboxMapper.selectByBusinessId("G-2")).thenReturn(null);

        WalletQueryService service = new WalletQueryServiceImpl(
                giftOrderMapper, ledgerMapper, incomeMapper, outboxMapper);

        ReconciliationReport report = service.reconcileGiftOrder("G-2");

        org.junit.jupiter.api.Assertions.assertEquals(3, report.anomalies().size());
        org.junit.jupiter.api.Assertions.assertTrue(report.anomalies().contains("缺少礼物扣款流水"));
        org.junit.jupiter.api.Assertions.assertTrue(report.anomalies().contains("缺少主播收益记录"));
        org.junit.jupiter.api.Assertions.assertTrue(report.anomalies().contains("缺少 Outbox 事件"));
    }
}
