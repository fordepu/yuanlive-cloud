package blog.yuanyuan.yuanlive.feign.live;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.common.result.ResultPage;
import blog.yuanyuan.yuanlive.entity.live.dto.LiveRoomDTO;
import blog.yuanyuan.yuanlive.entity.live.dto.SearchQueryDTO;
import blog.yuanyuan.yuanlive.entity.live.vo.LiveRoomRankVO;
import blog.yuanyuan.yuanlive.entity.live.vo.LiveRoomVO;
import blog.yuanyuan.yuanlive.entity.live.vo.SearchVO;
import blog.yuanyuan.yuanlive.entity.live.vo.UnseenVO;
import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import blog.yuanyuan.yuanlive.feign.live.dto.LiveInboxQueryResult;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(value = "live-service")
public interface LiveFeignClient {
    @GetMapping("/room/getRoom/{uid}")
    public Result<LiveRoomVO> getRoomByUid(@PathVariable("uid") Long uid);

    @GetMapping("/record/getUnseenCount")
    public Result<List<UnseenVO>> getUnseenCount(@RequestParam("followingIds") List<Long> followingIds,
                                                 @RequestParam("lastReadVideoIds") List<Long> lastReadVideoIds);

    @PostMapping("/room/search")
    public ResultPage<SearchVO> search(@RequestBody @Validated SearchQueryDTO searchQueryDTO);

    @GetMapping("/category/getCategoryIdBySearch")
    public Result<Integer> getCategoryIdBySearch(@RequestParam("keyword") String keyword);

    @PostMapping("/room/create")
    public Result<String> createRoom(@RequestBody @Validated LiveRoomDTO roomDTO);

    @GetMapping("/room/gift-validation/{roomId}")
    Result<GiftRoomValidationResult> validateGiftRoom(@PathVariable("roomId") Long roomId);

    @GetMapping("/internal/live/inbox/{eventId}")
    Result<LiveInboxQueryResult> getInbox(@PathVariable("eventId") String eventId);
}
