package blog.yuanyuan.yuanlive.live.server;

import blog.yuanyuan.yuanlive.live.properties.LiveRoomProperties;
import blog.yuanyuan.yuanlive.live.properties.LiveWeightsProperties;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.util.PopularityUtil;
import cn.hutool.core.util.StrUtil;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SessionManager {
    // 1. 全局在线用户字典: UserId -> Channel
    private static final Map<Long, Channel> USER_MAP = new ConcurrentHashMap<>();

    // 2. 房间广播组: RoomId -> ChannelGroup
    private static final Map<String, ChannelGroup> ROOM_MAP = new ConcurrentHashMap<>();

    public static final AttributeKey<Long> KEY_USER_ID = AttributeKey.valueOf("userId");
    public static final AttributeKey<String> KEY_USER_NAME = AttributeKey.valueOf("name");
    public static final AttributeKey<String> KEY_ROOM_ID = AttributeKey.valueOf("roomId");
    public static final AttributeKey<String> KEY_REQUESTED_ROOM_ID = AttributeKey.valueOf("requestedRoomId");
    public static final AttributeKey<ConnectionScope> KEY_CONNECTION_SCOPE = AttributeKey.valueOf("connectionScope");
    public static final AttributeKey<String> KEY_DEVICE_ID = AttributeKey.valueOf("deviceId");
    public static final AttributeKey<String> KEY_DEVICE_TYPE = AttributeKey.valueOf("deviceType");
    public static final AttributeKey<String> KEY_TOKEN = AttributeKey.valueOf("token");
    public static final AttributeKey<String> KEY_TRACE_ID = AttributeKey.valueOf("traceId");

    @Resource
    private LiveRoomProperties liveRoomProperties;
    @Resource
    private LiveWeightsProperties liveWeightsProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private PopularityUtil popularityUtil;
    /**
     * 1. 用户上线 (连接建立时调用)
     * 仅保存连接引用，不加入房间
     */
    public void register(Long userId, Channel channel) {
        USER_MAP.put(userId, channel);
        channel.attr(KEY_USER_ID).set(userId);
        log.info("用户[{}] 建立连接，当前在线人数: {}", userId, USER_MAP.size());
    }

    /** 注册应用级主连接；只有该连接可作为用户定向通知的本机目标。 */
    public void registerAppChannel(Long userId, Channel channel) {
        channel.attr(KEY_CONNECTION_SCOPE).set(ConnectionScope.APP);
        register(userId, channel);
    }

    /**
     * 注册房间连接时绝不写 USER_MAP，避免观看页覆盖用户主页面连接。
     */
    public void registerRoomChannel(String roomId, String device, Channel channel) {
        channel.attr(KEY_CONNECTION_SCOPE).set(ConnectionScope.ROOM);
        joinRoom(roomId, device, channel);
    }

    /**
     * 2. 用户加入房间 (收到 LOGIN/JOIN 消息时调用)
     */
    public void joinRoom(String roomId, String device,Channel channel) {
        // 将 roomId 绑定到 Channel，方便退出时识别
        channel.attr(KEY_ROOM_ID).set(roomId);
        channel.attr(KEY_DEVICE_TYPE).set(device);

        ChannelGroup group = ROOM_MAP.computeIfAbsent(roomId, k ->
                new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
        );
        group.add(channel);
        log.info("连接[{}] 加入房间[{}]", channel.id(), roomId);
    }

    /**
     * 3. 用户下线/断开
     */
    public void remove(Channel channel) {
        String traceId = channel.attr(KEY_TRACE_ID).get();
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        try {
            Long userId = channel.attr(KEY_USER_ID).get();
            String roomId = channel.attr(KEY_ROOM_ID).get();
            if (userId != null) {
                MDC.put("userId", String.valueOf(userId));
            }
            if (roomId != null) {
                MDC.put("roomId", roomId);
            }
            ConnectionScope scope = channel.attr(KEY_CONNECTION_SCOPE).get();
            if (userId != null && (scope == ConnectionScope.APP || scope == ConnectionScope.LEGACY || scope == null)) {
                // 仅当断开的是当前映射的 Channel 才删除，旧连接不能清掉新主连接。
                USER_MAP.remove(userId, channel);
                log.info("用户[{}] 断开连接", userId);
            }
            if (userId != null) {
                // 执行离场 Lua 脚本
                // 如果 roomId 不为空
                if (StrUtil.isNotBlank(roomId)) {
                    String currentKey = liveRoomProperties.getCurrentPrefix() + roomId;
                    stringRedisTemplate.opsForSet().remove(currentKey, userId.toString());
                    popularityUtil.updatePopularity(roomId, -liveWeightsProperties.getView());
                }
            }
            // Netty 的 ChannelGroup 会自动移除断开的 Channel，不需要手动操作 ROOM_MAP
        } finally {
            MDC.remove("traceId");
            MDC.remove("userId");
            MDC.remove("roomId");
        }
    }

    // 获取房间组（用于广播）
    public ChannelGroup getRoomChannels(String roomId) {
        return ROOM_MAP.get(roomId);
    }

    public Channel getUserChannel(Long userId) {
        return USER_MAP.get(userId);
    }
}
