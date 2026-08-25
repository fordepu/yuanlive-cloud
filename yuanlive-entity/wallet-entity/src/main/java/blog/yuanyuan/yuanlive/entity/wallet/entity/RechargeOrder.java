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
@TableName("recharge_order")
public class RechargeOrder {
    @CosId
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String orderNo;
    private Long userId;
    private Long coinAmount;
    private Long paidAmountCent;
    private Long exchangeRate;
    private String channel;
    private String channelTradeNo;
    private String status;
    private LocalDateTime paidTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
