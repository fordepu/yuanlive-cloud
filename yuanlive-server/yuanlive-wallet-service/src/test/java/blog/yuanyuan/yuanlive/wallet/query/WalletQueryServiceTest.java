package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftCatalog;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import blog.yuanyuan.yuanlive.wallet.mapper.AnchorIncomeMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftOrderMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.OutboxEventMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletLedgerMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftCatalogMapper;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;

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

    @Test
    void listsOnlyPublishedGiftCatalogEntries() {
        GiftCatalogMapper catalogMapper = mock(GiftCatalogMapper.class);
        GiftCatalog published = new GiftCatalog();
        published.setId(1L);
        published.setGiftCode("rose");
        published.setGiftName("玫瑰");
        published.setGiftIcon("rose.svg");
        published.setCoinAmount(10L);
        published.setStatus("ON_SHELF");
        when(catalogMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(published));

        WalletQueryService service = new WalletQueryServiceImpl(
                mock(GiftOrderMapper.class), mock(WalletLedgerMapper.class), mock(AnchorIncomeMapper.class),
                mock(OutboxEventMapper.class), mock(blog.yuanyuan.yuanlive.wallet.mapper.WalletAccountMapper.class),
                mock(blog.yuanyuan.yuanlive.wallet.mapper.RechargeOrderMapper.class), null, catalogMapper);

        org.junit.jupiter.api.Assertions.assertEquals(1, service.listGiftCatalog().size());
        org.junit.jupiter.api.Assertions.assertEquals("rose", service.listGiftCatalog().get(0).code());
    }

    @Test
    void administratorCanReadGiftOrderWithoutSenderOwnershipCheck() {
        GiftOrderMapper giftOrderMapper = mock(GiftOrderMapper.class);
        GiftOrder order = new GiftOrder();
        order.setId(8L);
        order.setOrderNo("G-ADMIN-1");
        order.setSenderId(10L);
        order.setAnchorId(20L);
        order.setStatus("PAID");
        when(giftOrderMapper.selectByOrderNo("G-ADMIN-1")).thenReturn(order);

        WalletQueryService service = new WalletQueryServiceImpl(
                giftOrderMapper, mock(WalletLedgerMapper.class), mock(AnchorIncomeMapper.class),
                mock(OutboxEventMapper.class));

        AdminGiftOrderQueryResult result = service.getGiftOrderForAdmin("G-ADMIN-1");

        org.junit.jupiter.api.Assertions.assertEquals("G-ADMIN-1", result.orderNo());
        org.junit.jupiter.api.Assertions.assertEquals(10L, result.senderId());
    }

    @Test
    void administratorGiftOrderPageUsesTheMaximumAllowedPageSizeAndReturnsCursor() {
        GiftOrderMapper giftOrderMapper = mock(GiftOrderMapper.class);
        GiftOrder first = new GiftOrder();
        first.setId(9L);
        first.setOrderNo("G-ADMIN-9");
        GiftOrder second = new GiftOrder();
        second.setId(8L);
        second.setOrderNo("G-ADMIN-8");
        when(giftOrderMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(first, second));

        WalletQueryService service = new WalletQueryServiceImpl(
                giftOrderMapper, mock(WalletLedgerMapper.class), mock(AnchorIncomeMapper.class),
                mock(OutboxEventMapper.class));

        CursorPage<AdminGiftOrderQueryResult> page = service.listGiftOrdersForAdmin(
                "", 10L, 20L, "PAID", 10L, 2);

        assertEquals(2, page.items().size());
        assertEquals(8L, page.nextCursor());
    }

    @Test
    void administratorEndpointsRequireAdminRole() throws NoSuchMethodException {
        Method listMethod = blog.yuanyuan.yuanlive.wallet.controller.WalletQueryController.class.getMethod(
                "listGiftOrdersForAdmin", String.class, Long.class, Long.class, String.class, Long.class, int.class);
        Method detailMethod = blog.yuanyuan.yuanlive.wallet.controller.WalletQueryController.class.getMethod(
                "getGiftOrderForAdmin", String.class);

        assertTrue(List.of(listMethod.getAnnotation(SaCheckRole.class).value()).contains("ADMIN"));
        assertTrue(List.of(detailMethod.getAnnotation(SaCheckRole.class).value()).contains("ADMIN"));
    }
}
