package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.GiftOrder;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface GiftOrderMapper extends BaseMapper<GiftOrder> {

    @Select("SELECT * FROM gift_order WHERE order_no = #{orderNo} LIMIT 1")
    GiftOrder selectByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM gift_order WHERE sender_id = #{senderId} AND request_id = #{requestId} LIMIT 1")
    GiftOrder selectBySenderAndRequest(@Param("senderId") Long senderId, @Param("requestId") String requestId);

    @Select("SELECT * FROM gift_order WHERE sender_id = #{senderId} AND request_id = #{requestId} "
            + "LIMIT 1 FOR UPDATE")
    GiftOrder selectBySenderAndRequestForUpdate(@Param("senderId") Long senderId,
                                                @Param("requestId") String requestId);
}
