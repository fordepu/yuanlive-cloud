package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.WalletLedger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface WalletLedgerMapper extends BaseMapper<WalletLedger> {

    @Select("<script>SELECT * FROM wallet_ledger WHERE user_id = #{userId} "
            + "<if test='beforeId != null'>AND id &lt; #{beforeId}</if> "
            + "ORDER BY id DESC LIMIT #{limit}</script>")
    List<WalletLedger> selectByUserBeforeId(@Param("userId") Long userId,
                                            @Param("beforeId") Long beforeId,
                                            @Param("limit") int limit);

    @Select("SELECT * FROM wallet_ledger "
            + "WHERE business_type = 'GIFT' AND business_no = #{businessNo} AND direction = 'DEBIT' LIMIT 1")
    WalletLedger selectGiftDebitByBusinessNo(String businessNo);
}
