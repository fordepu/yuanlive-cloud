package blog.yuanyuan.yuanlive.live.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.entity.live.entity.LiveInboxEvent;
import blog.yuanyuan.yuanlive.feign.live.dto.LiveInboxQueryResult;
import blog.yuanyuan.yuanlive.live.mapper.LiveInboxEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供给钱包对账的只读收件箱状态，不允许通过该接口修改消费状态。 */
@RestController
@RequestMapping("/internal/live/inbox")
@RequiredArgsConstructor
public class InternalLiveInboxController {

    private final LiveInboxEventMapper liveInboxEventMapper;

    @GetMapping("/{eventId}")
    public Result<LiveInboxQueryResult> getInbox(@PathVariable String eventId) {
        LiveInboxEvent event = liveInboxEventMapper.selectByEventId(eventId);
        return Result.success(event == null ? null :
                new LiveInboxQueryResult(event.getEventId(), event.getBusinessId(), event.getStatus()));
    }
}
