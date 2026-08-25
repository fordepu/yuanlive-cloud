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
@TableName("wallet_ledger")
public class WalletLedger {
    @CosId
    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String ledgerNo;
    private Long accountId;
    private Long userId;
    private String businessType;
    private String businessNo;
    private String direction;
    private Long amount;
    private Long balanceAfter;
    private String status;
    private LocalDateTime createTime;
}
