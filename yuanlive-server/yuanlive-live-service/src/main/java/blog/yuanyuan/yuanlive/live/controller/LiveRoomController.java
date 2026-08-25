package blog.yuanyuan.yuanlive.live.controller;

import blog.yuanyuan.yuanlive.common.result.Result;
import blog.yuanyuan.yuanlive.common.result.ResultPage;
import blog.yuanyuan.yuanlive.entity.live.dto.SearchQueryDTO;
import blog.yuanyuan.yuanlive.entity.live.vo.SearchVO;
import blog.yuanyuan.yuanlive.entity.live.dto.LiveRoomDTO;
import blog.yuanyuan.yuanlive.live.domain.dto.LiveRoomQueryDTO;
import blog.yuanyuan.yuanlive.live.domain.dto.SrsCallBackDTO;
import blog.yuanyuan.yuanlive.live.domain.vo.LiveRoomDetailVO;
import blog.yuanyuan.yuanlive.entity.live.vo.LiveRoomVO;
import blog.yuanyuan.yuanlive.entity.live.vo.LiveRoomRankVO;
import blog.yuanyuan.yuanlive.feign.live.dto.GiftRoomValidationResult;
import blog.yuanyuan.yuanlive.live.service.LiveRoomService;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Update;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@RestController
@Tag(name = "直播间管理")
@RequestMapping("/room")
@Slf4j
public class LiveRoomController {
    @Resource
    private LiveRoomService liveRoomService;

    @PostMapping("/create")
    @Operation(summary = "创建直播间")
    @SaCheckRole("ANCHOR")
    public Result<String> createRoom(@RequestBody @Validated LiveRoomDTO roomDTO) {
        Long anchorId = StpUtil.getLoginIdAsLong();
        long roomId = liveRoomService.createRoom(roomDTO, anchorId);
        return Result.success(String.valueOf(roomId));
    }

    @PostMapping("/apply")
    @Operation(summary = "申请开播")
    @SaCheckRole("ANCHOR")
    public Result<String> apply(@RequestBody @Validated(Update.class) LiveRoomDTO roomDTO) {
        String url = liveRoomService.apply(roomDTO);
        if (StrUtil.isNotBlank(url)) {
            return Result.success(url);
        }
        return Result.failed("修改失败");
    }

    @PostMapping("/start")
    @Operation(summary = "开始直播")
    public int startLive(@RequestBody SrsCallBackDTO srsCallBackDTO) {
        try {
            boolean success = liveRoomService.startLive(srsCallBackDTO);
            // 成功返回 0，失败返回 1
            return success ? 0 : 1;
        } catch (Exception e) {
            log.error("推流回调异常: ", e);
            return 1;
        }
    }

    @PostMapping("/end")
    @Operation(summary = "结束直播")
    public int endLive(@RequestBody SrsCallBackDTO dto) {
        try {
            liveRoomService.endLive(dto);
            // SRS 收到 0 表示回调成功，即使失败通常也返回 0，因为流已经断开了
            return 0;
        } catch (Exception e) {
            log.error("停止直播回调异常: ", e);
            return 0;
        }
    }

    @PostMapping("/dvr")
    @Operation(summary = "DVR录制回调")
    public int dvr(@RequestBody SrsCallBackDTO dto) {
        try {
            liveRoomService.dvr(dto);
            return 0;
        } catch (Exception e) {
            log.error("DVR回调异常: ", e);
            return 0;
        }
    }

    @GetMapping("/detail/{roomId}")
    @Operation(summary = "获取直播间详情")
    public Result<LiveRoomDetailVO> getRoomDetail(@PathVariable("roomId") Long roomId) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        log.debug("token为: {}", attributes.getRequest().getHeader("token"));
        LiveRoomDetailVO detail = liveRoomService.getRoomDetail(roomId);
        return Result.success(detail);
    }

    @GetMapping("/gift-validation/{roomId}")
    @Operation(summary = "校验直播间是否可接收礼物")
    public Result<GiftRoomValidationResult> validateGiftRoom(@PathVariable("roomId") Long roomId) {
        return Result.success(liveRoomService.validateGiftRoom(roomId));
    }

    @GetMapping("/myRoom")
    @Operation(summary = "获取当前主播的直播间")
    @SaCheckRole("ANCHOR")
    public Result<LiveRoomVO> getMyRoom() {
        Long anchorId = StpUtil.getLoginIdAsLong();
        LiveRoomVO room = liveRoomService.getAnchorRoom(anchorId);
        return Result.success(room);
    }

    @GetMapping("/getRoom/{uid}")
    @Operation(summary = "根据主播id获取直播间")
    public Result<LiveRoomVO> getRoomByUid(@PathVariable("uid") Long uid) {
        return Result.success(liveRoomService.getAnchorRoom(uid));
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询直播间列表")
    public Result<ResultPage<LiveRoomVO>> list(@ParameterObject LiveRoomQueryDTO queryDTO) {
        ResultPage<LiveRoomVO> page = liveRoomService.pageRooms(queryDTO);
        return Result.success(page);
    }

    @GetMapping("/anchor/{anchorId}")
    @Operation(summary = "根据主播ID获取直播间")
    public Result<LiveRoomVO> getAnchorRoom(@PathVariable("anchorId") Long anchorId) {
        LiveRoomVO room = liveRoomService.getAnchorRoom(anchorId);
        return Result.success(room);
    }

    @GetMapping("/popularRooms")
    @Operation(summary = "获取人气前5的直播列表")
    public Result<List<LiveRoomRankVO>> getPopularRooms() {
        List<LiveRoomRankVO> rooms = liveRoomService.getPopularRooms();
        return Result.success(rooms);
    }

    @PostMapping("/search")
    @Operation(summary = "综合搜索视频与直播间")
    public ResultPage<SearchVO> search(@RequestBody @Validated SearchQueryDTO searchQueryDTO) {
        return liveRoomService.search(searchQueryDTO);
    }
}
