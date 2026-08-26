package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.InboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InboxEventMapper extends BaseMapper<InboxEvent> {

    @Select("SELECT * FROM inbox_event WHERE event_id = #{eventId} LIMIT 1")
    InboxEvent selectByEventId(String eventId);
}
