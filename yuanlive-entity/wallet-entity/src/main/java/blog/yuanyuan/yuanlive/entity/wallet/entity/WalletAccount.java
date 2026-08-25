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
@TableName("wallet_account")
public class WalletAccount {
    // CosId 插件在 MyBatis INSERT 前填充主键，避免依赖单库自增序列。
    @CosId
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long userId;
    private Long availableCoin;
    private Long version;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
