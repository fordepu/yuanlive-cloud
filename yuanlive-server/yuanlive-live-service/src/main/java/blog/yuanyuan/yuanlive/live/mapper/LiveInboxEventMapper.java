package blog.yuanyuan.yuanlive.live.mapper;

import blog.yuanyuan.yuanlive.entity.live.entity.LiveInboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface LiveInboxEventMapper extends BaseMapper<LiveInboxEvent> {

    @Select("SELECT * FROM live_inbox_event WHERE event_id = #{eventId} LIMIT 1")
    LiveInboxEvent selectByEventId(@Param("eventId") String eventId);
}
