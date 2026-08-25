package blog.yuanyuan.yuanlive.feign.live.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 钱包送礼前的只读房间校验结果，不包含账户、订单或任何资金字段。
 */
public record GiftRoomValidationResult(
        @JsonSerialize(using = ToStringSerializer.class) Long roomId,
        @JsonSerialize(using = ToStringSerializer.class) Long anchorId,
        Integer liveStatus,
        boolean acceptingGifts,
        String rejectionReason) {
}
