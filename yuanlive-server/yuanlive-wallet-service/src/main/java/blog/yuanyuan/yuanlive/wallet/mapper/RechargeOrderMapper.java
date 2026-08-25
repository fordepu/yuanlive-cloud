package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.RechargeOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

    @Select("SELECT * FROM recharge_order WHERE order_no = #{orderNo} LIMIT 1")
    RechargeOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM recharge_order WHERE order_no = #{orderNo} FOR UPDATE")
    RechargeOrder selectByOrderNoForUpdate(String orderNo);
}
