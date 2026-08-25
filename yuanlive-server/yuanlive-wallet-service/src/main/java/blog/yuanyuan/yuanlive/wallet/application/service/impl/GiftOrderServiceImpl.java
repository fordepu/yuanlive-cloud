package blog.yuanyuan.yuanlive.wallet.application.service.impl;

import blog.yuanyuan.yuanlive.common.exception.ApiException;
import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftCatalog;
import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import blog.yuanyuan.yuanlive.feign.live.LiveFeignClient;
import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderCommand;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderCreation;
import blog.yuanyuan.yuanlive.wallet.application.dto.GiftOrderResult;
import blog.yuanyuan.yuanlive.wallet.application.service.GiftOrderService;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftCatalogMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.GiftOrderMapper;
import blog.yuanyuan.yuanlive.wallet.mapper.WalletLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GiftOrderServiceImpl implements GiftOrderService {

    private static final String GIFT_ON_SHELF = "ON_SHELF";
    private static final long COIN_PER_YUAN = 10L;

    private final GiftCatalogMapper giftCatalogMapper;
    private final GiftOrderMapper giftOrderMapper;
    private final WalletLedgerMapper walletLedgerMapper;
    private final LiveFeignClient liveFeignClient;
    private final GiftOrderTransactionService giftOrderTransactionService;

    @Value("${wallet.gift.default-platform-rate:2000}")
    private int platformRate;

    @Value("${wallet.gift.settlement-rule-version:v1}")
    private String settlementRuleVersion;

    @Override
    public GiftOrderResult sendGift(Long senderId, GiftOrderCommand command) {
        validateRequest(senderId, command);
        GiftOrder existing = giftOrderMapper.selectBySenderAndRequest(senderId, command.requestId());
        if (existing != null) {
            return existingResult(existing);
        }

        GiftCatalog gift = giftCatalogMapper.selectById(command.giftId());
        if (gift == null || !GIFT_ON_SHELF.equals(gift.getStatus())) {
            throw new ApiException("礼物不可用");
        }
        GiftRoomValidationResult room = validateRoom(command.roomId());
        GiftOrderCreation creation = createSnapshot(command, gift, room.anchorId());
        try {
            return giftOrderTransactionService.create(senderId, creation);
        } catch (DuplicateKeyException exception) {
            // 并发相同幂等键只允许唯一键胜出的事务生效；失败事务回滚后仅返回已落库的原订单。
            GiftOrder concurrentWinner = giftOrderMapper.selectBySenderAndRequest(senderId, command.requestId());
            if (concurrentWinner != null) {
                return existingResult(concurrentWinner);
            }
            throw exception;
        }
    }

    private GiftRoomValidationResult validateRoom(Long roomId) {
        Result<GiftRoomValidationResult> response = liveFeignClient.validateGiftRoom(roomId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ApiException("直播间校验失败");
        }
        GiftRoomValidationResult room = response.getData();
        if (!roomId.equals(room.roomId())) {
            throw new ApiException("直播间校验结果不匹配");
        }
        if (!room.acceptingGifts()) {
            String reason = room.rejectionReason();
            throw new ApiException(reason == null || reason.isBlank() ? "直播间不可收礼" : reason);
        }
        if (room.anchorId() == null || room.anchorId() <= 0) {
            throw new ApiException("直播间主播信息缺失");
        }
        return room;
    }

    private GiftOrderCreation createSnapshot(GiftOrderCommand command, GiftCatalog gift, Long anchorId) {
        if (gift.getCoinAmount() == null || gift.getCoinAmount() <= 0) {
            // 目录脏数据不能进入扣款 SQL，否则负数会把扣减反转为加款并污染不可变流水。
            throw new ApiException("礼物价格必须大于 0");
        }
        if (platformRate < 0 || platformRate > 10_000) {
            throw new IllegalStateException("礼物平台抽成配置必须在 0..10000 范围内");
        }
        if (settlementRuleVersion == null || settlementRuleVersion.isBlank()) {
            throw new IllegalStateException("礼物结算规则版本不能为空");
        }
        long coinAmount = Math.multiplyExact(gift.getCoinAmount(), command.count().longValue());
        long platformCoinAmount = Math.multiplyExact(coinAmount, platformRate) / 10_000L;
        long anchorIncomeAmount = Math.addExact(coinAmount, -platformCoinAmount);
        return new GiftOrderCreation(
                command.requestId(), command.roomId(), anchorId, gift.getId(), gift.getGiftCode(), gift.getGiftName(),
                gift.getGiftIcon(), command.count(), gift.getCoinAmount(), coinAmount, COIN_PER_YUAN, platformRate,
                platformCoinAmount, anchorIncomeAmount, settlementRuleVersion);
    }

    private GiftOrderResult existingResult(GiftOrder order) {
        WalletLedger ledger = walletLedgerMapper.selectGiftDebitByBusinessNo(order.getOrderNo());
        if (ledger == null) {
            throw new IllegalStateException("礼物订单缺少扣款流水");
        }
        return GiftOrderResult.from(order, ledger.getBalanceAfter());
    }

    private void validateRequest(Long senderId, GiftOrderCommand command) {
        if (senderId == null || senderId <= 0) {
            throw new ApiException("送礼人不能为空");
        }
        if (command == null) {
            throw new ApiException("送礼请求不能为空");
        }
        if (command.requestId() == null || command.requestId().isBlank() || command.requestId().length() > 64) {
            throw new ApiException("请求幂等键格式错误");
        }
        if (command.roomId() == null || command.roomId() <= 0) {
            throw new ApiException("直播间不能为空");
        }
        if (command.giftId() == null || command.giftId() <= 0) {
            throw new ApiException("礼物不能为空");
        }
        if (command.count() == null || command.count() < 1 || command.count() > 99) {
            throw new ApiException("礼物数量必须在 1..99 范围内");
        }
    }
}
