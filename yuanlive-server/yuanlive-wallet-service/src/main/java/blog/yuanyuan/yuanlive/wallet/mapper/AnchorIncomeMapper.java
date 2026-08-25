package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.AnchorIncome;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface AnchorIncomeMapper extends BaseMapper<AnchorIncome> {

    @Select("<script>SELECT * FROM anchor_income WHERE anchor_id = #{anchorId} "
            + "<if test='beforeId != null'>AND id &lt; #{beforeId}</if> "
            + "ORDER BY id DESC LIMIT #{limit}</script>")
    List<AnchorIncome> selectByAnchorBeforeId(@Param("anchorId") Long anchorId,
                                              @Param("beforeId") Long beforeId,
                                              @Param("limit") int limit);

    @Select("SELECT * FROM anchor_income WHERE gift_order_no = #{orderNo} LIMIT 1")
    AnchorIncome selectByGiftOrderNo(@Param("orderNo") String orderNo);
}
