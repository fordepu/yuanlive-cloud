package blog.yuanyuan.yuanlive.live.tools;

import blog.yuanyuan.yuanlive.live.domain.dto.SrsCallBackDTO;
import blog.yuanyuan.yuanlive.live.message.notification.SystemNotification;
import blog.yuanyuan.yuanlive.live.properties.LiveRoomProperties;
import blog.yuanyuan.yuanlive.live.realtime.dispatch.AppRealtimeEventPublisher;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomRealtimeEventPublisher;
import blog.yuanyuan.yuanlive.live.service.LiveRoomService;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Service
@Slf4j
public class LiveMcpTools {
    @Resource
    private RoomRealtimeEventPublisher roomRealtimeEventPublisher;
    @Resource
    private AppRealtimeEventPublisher appRealtimeEventPublisher;
    @Resource
    private LiveRoomProperties liveRoomProperties;
    @Resource
    private RestTemplate restTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Tool(description = "在直播间公屏发布系统公告，警告全体观众")
    public String broadcast(@ToolParam(description = "直播间房间ID") String roomId) {
        log.warn("broadcast roomId: {}", roomId);
        if (!checkRoom(roomId)) {
            return "直播间不存在";
        }
        String notice = "【系统公告】文明发言，共建和谐直播间。恶意违规者将被永久封禁。";
        SystemNotification.SystemData systemData = new SystemNotification.SystemData();
        systemData.setType("warning");
        systemData.setUser("系统");
        systemData.setUserid(0L);
        systemData.setContent(notice);
        systemData.setIsVip(false);
        systemData.setLevel(0);
        
        SystemNotification notification = SystemNotification.builder()
                .data(systemData)
                .roomId(roomId)
                .build();
        roomRealtimeEventPublisher.publish(notification);
        return "全体广播已发送至房间: " + roomId;
    }

    @Tool(description = "向指定直播间的主播发送私密警告消息")
    public String warnAnchor(@ToolParam(description = "直播间房间id") String roomId) {
        log.warn("warnAnchor roomId: {}", roomId);
        if (!checkRoom(roomId)) {
            return "直播间不存在";
        }
        // 根据房间ID获取主播uid
        String sessionKey = liveRoomProperties.getSessionPrefix() + roomId;
        String anchorId = (String) stringRedisTemplate.opsForHash().get(sessionKey, "anchorId");
        if (anchorId == null) {
            return "直播间不存在";
        }
        long uid = Long.parseLong(anchorId);
        String warningMsg = "【系统警告】检测到您的直播间弹幕存在违规倾向，请加强管理，否则将面临封禁。";
        SystemNotification.SystemData systemData = new SystemNotification.SystemData();
        systemData.setType("warning");
        systemData.setUser("系统");
        systemData.setUserid(uid);
        systemData.setContent(warningMsg);
        systemData.setIsVip(false);
        systemData.setLevel(0);
        
        SystemNotification notification = SystemNotification.builder()
                .data(systemData)
                .userId(uid)
                .build();
        appRealtimeEventPublisher.publish(uid, notification);
        return "警告已发送至主播: " + roomId;
    }

    @Tool(description = "封禁指定直播间")
    public String banRoom(@ToolParam(description = "直播间房间ID") String roomId) {
        log.warn("banRoom roomId: {}", roomId);
        if (!checkRoom(roomId)) {
            return "直播间不存在";
        }
        String sessionKey = liveRoomProperties.getSessionPrefix() + roomId;
        String client = (String) stringRedisTemplate.opsForHash().get(sessionKey, "client");
        // 部署后 ip 地址应更换为srs所在地址
        String url = "http://localhost:1985/api/v1/clients/" + client;
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, null, Map.class);
        Map<String, Object> body = response.getBody();
        log.info("直播间封禁响应: {}", body);
        Integer code = null;
        if (body != null) {
            code = (Integer) body.get("code");
            if (code == 0) {
                log.info("直播间封禁成功: {}", roomId);
                return "直播间封禁成功: " + roomId;
            }
        }
        log.warn("直播间封禁失败: {}", roomId);
        return "直播间封禁失败: " + roomId;
    }

    // 判断直播间是否存在
    private boolean checkRoom(String roomId) {
        return stringRedisTemplate.hasKey(liveRoomProperties.getSessionPrefix() + roomId);
    }
}
