package blog.yuanyuan.yuanlive.entity.wallet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;
import me.ahoo.cosid.annotation.CosId;

import java.time.LocalDateTime;

@Data
@TableName("gift_order")
public class GiftOrder {
    @CosId
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String orderNo;
    private Long senderId;
    private String requestId;
    private Long roomId;
    private Long anchorId;
    private Long giftId;
    private Integer giftCount;
    private Long coinAmount;
    private Long exchangeRate;
    private Integer platformRate;
    private Long platformCoinAmount;
    private Long anchorIncomeAmount;
    private String settlementRuleVersion;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
