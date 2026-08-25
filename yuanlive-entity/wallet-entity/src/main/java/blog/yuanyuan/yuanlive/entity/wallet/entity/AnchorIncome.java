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
@TableName("anchor_income")
public class AnchorIncome {
    @CosId
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String incomeNo;
    private String giftOrderNo;
    private Long anchorId;
    private Long grossAmount;
    private Long platformFee;
    private Long incomeAmount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
