package blog.yuanyuan.yuanlive.wallet.mapper;

import blog.yuanyuan.yuanlive.entity.wallet.entity.OutboxEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {

    @Select("SELECT COUNT(*) FROM outbox_event WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(create_time), NOW()), 0) FROM outbox_event "
            + "WHERE status IN ('NEW','RETRY_WAIT','PUBLISHING')")
    long oldestPendingAgeSeconds();

    @Select("SELECT * FROM outbox_event WHERE business_id = #{businessId} LIMIT 1")
    OutboxEvent selectByBusinessId(@Param("businessId") String businessId);

    @Select("""
            SELECT *
            FROM outbox_event
            WHERE status = 'NEW'
               OR (status = 'RETRY_WAIT' AND (next_retry_at IS NULL OR next_retry_at <= NOW(3)))
               OR (status = 'PUBLISHING' AND lease_until <= NOW(3))
            ORDER BY id
            LIMIT #{batchSize}
            FOR UPDATE SKIP LOCKED
            """)
    List<OutboxEvent> selectClaimableForUpdate(@Param("batchSize") int batchSize);

    @Update("""
            UPDATE outbox_event
            SET status = 'PUBLISHING',
                lease_owner = #{leaseOwner},
                lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, NOW(3)),
                update_time = NOW(3)
            WHERE id = #{id}
            """)
    int claim(@Param("id") Long id,
              @Param("leaseOwner") String leaseOwner,
              @Param("leaseSeconds") long leaseSeconds);

    @Update("""
            UPDATE outbox_event
            SET status = 'PUBLISHED',
                published_at = NOW(3),
                next_retry_at = NULL,
                lease_owner = NULL,
                lease_until = NULL,
                failure_reason = NULL,
                update_time = NOW(3)
            WHERE event_id = #{eventId}
              AND lease_owner = #{leaseOwner}
              AND status = 'PUBLISHING'
            """)
    int markPublished(@Param("eventId") String eventId,
                      @Param("leaseOwner") String leaseOwner);

    @Select("""
            SELECT *
            FROM outbox_event
            WHERE event_id = #{eventId}
              AND lease_owner = #{leaseOwner}
              AND status = 'PUBLISHING'
            FOR UPDATE
            """)
    OutboxEvent selectPublishingForUpdate(@Param("eventId") String eventId,
                                          @Param("leaseOwner") String leaseOwner);

    @Update("""
            UPDATE outbox_event
            SET status = #{status},
                retry_count = #{retryCount},
                next_retry_at = #{nextRetryAt},
                lease_owner = NULL,
                lease_until = NULL,
                failure_reason = LEFT(#{failureReason}, 500),
                update_time = NOW(3)
            WHERE event_id = #{eventId}
              AND lease_owner = #{leaseOwner}
              AND status = 'PUBLISHING'
            """)
    int markFailed(@Param("eventId") String eventId,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("status") String status,
                   @Param("retryCount") int retryCount,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("failureReason") String failureReason);
}
