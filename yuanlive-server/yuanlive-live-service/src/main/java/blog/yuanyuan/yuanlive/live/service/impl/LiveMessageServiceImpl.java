package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.entity.live.entity.LiveRoom;
import blog.yuanyuan.yuanlive.live.message.Message;
import blog.yuanyuan.yuanlive.live.message.WsResult;
import blog.yuanyuan.yuanlive.live.message.notification.EventMessage;
import blog.yuanyuan.yuanlive.live.message.notification.GroupChatNotification;
import blog.yuanyuan.yuanlive.live.message.notification.SystemNotification;
import blog.yuanyuan.yuanlive.live.message.request.*;
import blog.yuanyuan.yuanlive.live.message.response.AckMessage;
import blog.yuanyuan.yuanlive.live.message.response.JoinResponse;
import blog.yuanyuan.yuanlive.live.message.response.PongMessage;
import blog.yuanyuan.yuanlive.live.properties.AiDetectProperties;
import blog.yuanyuan.yuanlive.live.properties.LiveRoomProperties;
import blog.yuanyuan.yuanlive.live.properties.LiveWeightsProperties;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.service.LiveMessageService;
import blog.yuanyuan.yuanlive.live.service.LiveRoomService;
import blog.yuanyuan.yuanlive.live.util.PopularityUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class LiveMessageServiceImpl implements LiveMessageService {
    @Resource
    private SessionManager sessionManager;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LiveWeightsProperties liveWeightsProperties;
    @Resource
    private LiveRoomProperties liveRoomProperties;
    @Resource
    private AiDetectProperties aiDetectProperties;
    @Resource
    private PopularityUtil popularityUtil;
    @Resource
    private LiveRoomService roomService;

    @Resource(name = "joinRoomScript")
    private DefaultRedisScript<Long> joinRoomScript;
    @SuppressWarnings("rawtypes")
    @Resource(name = "harvestChatsScript")
    private DefaultRedisScript<List> harvestChatsScript;

    @Value("${live.mq.chat.exchange}")
    private String exchangeName;
    @Value(("${live.mq.ai-detect.exchange}"))
    private String aiDetectExchangeName;
    @Value("${live.mq.ai-detect.routing-key}")
    private String aiDetectRoutingKey;

    private static final String LIKE_STATS_EXCHANGE = "live.stats.exchange";

    @Override
    public void handlePing(ChannelHandlerContext ctx, PingMessage ping) {
        PongMessage pong = PongMessage.builder()
                .msgId(ping.getMsgId())
                .build();
        sendMsg(ctx, pong);
    }

    @Override
    public void handleJoinRoom(ChannelHandlerContext ctx, JoinRequest join) {
        if (join.getData() == null || StrUtil.isBlank(join.getData().getRoomId())) {
            JoinResponse response = JoinResponse.builder()
                    .msgId(join.getMsgId())
                    .code(400)
                    .message("未携带所有参数")
                    .success(false)
                    .build();
            sendMsg(ctx, response);
            return;
        }
        String roomId = join.getData().getRoomId();
        ConnectionScope scope = ctx.channel().attr(SessionManager.KEY_CONNECTION_SCOPE).get();
        if (scope == ConnectionScope.APP) {
            JoinResponse response = JoinResponse.builder()
                    .msgId(join.getMsgId())
                    .code(400)
                    .message("APP连接不能加入直播间")
                    .success(false)
                    .build();
            sendMsg(ctx, response);
            return;
        }
        String requestedRoomId = ctx.channel().attr(SessionManager.KEY_REQUESTED_ROOM_ID).get();
        if (scope == ConnectionScope.ROOM && !roomId.equals(requestedRoomId)) {
            JoinResponse response = JoinResponse.builder()
                    .msgId(join.getMsgId())
                    .code(400)
                    .message("JOIN_ROOM房间与握手房间不一致")
                    .success(false)
                    .build();
            sendMsg(ctx, response);
            return;
        }
        join.setRoomId(roomId);
        Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();// 判断直播间是否存在
        boolean exists = checkRoom(roomId);
        if (!exists) {
            log.warn("直播间[{}] 不存在", roomId);
            JoinResponse response = JoinResponse.builder()
                    .msgId(join.getMsgId())
                    .code(404)
                    .message("直播间未开播")
                    .success(false)
                    .build();
            sendMsg(ctx, response);
            return;
        }
        if (StrUtil.isNotBlank(roomId) && userId != null) {
            // 如果用户已经加入过该房间，不允许重复加入
            String currentRoomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
            if (roomId.equals(currentRoomId)) {
                JoinResponse response = JoinResponse.builder()
                        .msgId(join.getMsgId())
                        .code(400)
                        .message("已加入过该房间")
                        .success(false)
                        .build();
                sendMsg(ctx, response);
                return;
            }
            // ROOM 连接一生只服务一个房间；LEGACY 才保留旧客户端的切房兼容。
            if (currentRoomId != null) {
                if (scope == ConnectionScope.ROOM) {
                    JoinResponse response = JoinResponse.builder()
                            .msgId(join.getMsgId())
                            .code(400)
                            .message("ROOM连接不能切换直播间")
                            .success(false)
                            .build();
                    sendMsg(ctx, response);
                    return;
                }
                leaveRoom(ctx);
            }

            // 1. 本地连接管理
            sessionManager.joinRoom(roomId, join.getData().getDevice(), ctx.channel());
            // 2. 准备 Redis Keys
            String currentKey = liveRoomProperties.getCurrentPrefix() + roomId;
            String totalKey = liveRoomProperties.getTotalPrefix() + roomId;
            String sessionKey = liveRoomProperties.getSessionPrefix() + roomId;

            // 3. 执行原子脚本：一气呵成完成 SADD, PFADD, SCARD, Peak Check
            Long currentCount = stringRedisTemplate.execute(
                    joinRoomScript,
                    Arrays.asList(currentKey, sessionKey, totalKey),
                    userId.toString()
            );

            // 4. 增加人气权重
            popularityUtil.updatePopularity(roomId, liveWeightsProperties.getView());
            String anchorName = (String) stringRedisTemplate.opsForHash()
                    .get(sessionKey, "anchor");
            String roomTitle = (String) stringRedisTemplate.opsForHash()
                    .get(sessionKey, "roomTitle");
            JoinResponse.JoinData data = new JoinResponse.JoinData();
            data.setRoomId(roomId);
            data.setRoomTitle(roomTitle);
            data.setAnchorName(anchorName);
            data.setOnlineCount(currentCount.intValue());
            JoinResponse response = JoinResponse.builder()
                    .msgId(join.getMsgId())
                    .code(200)
                    .data(data)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .success(true)
                    .build();
            sendMsg(ctx, response);
            // 构建直播间欢迎通知(私发)
            Optional<LiveRoom> opt = roomService.lambdaQuery()
                    .select(LiveRoom::getNotification)
                    .eq(LiveRoom::getId, roomId)
                    .oneOpt();
            String message = opt.get().getNotification();
            String username = ctx.channel().attr(SessionManager.KEY_USER_NAME).get();
            SystemNotification.SystemData systemData = new SystemNotification.SystemData();
            systemData.setType("announcement");
            systemData.setUser(username);
            systemData.setUserid(userId);
            systemData.setContent(message);
            systemData.setIsVip(false);
            systemData.setLevel(0);
            
            SystemNotification notification = SystemNotification.builder()
                    .data(systemData)
                    .userId(userId)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .build();
            sendMsg(ctx, notification);

            // 广播用户加入房间事件
            EventMessage.EventData eventData = new EventMessage.EventData();
            eventData.setType("enter");
            eventData.setRoomId(roomId);
            eventData.setUser(username);
            eventData.setUserid(userId);
            eventData.setIsVip(false);
            eventData.setLevel(0);
            
            EventMessage enterEvent = EventMessage.builder()
                    .data(eventData)
                    .timestamp(System.currentTimeMillis() / 1000)
                    .roomId(roomId)
                    .msgId(join.getMsgId())
                    .build();
            broadcastMessage(enterEvent);
            log.info("用户[{}] 加入房间[{}] 成功", username, roomId);
        }
    }

    @Override
    public void handleChat(ChannelHandlerContext ctx, GroupChatRequest chat) {
        AckMessage ack = AckMessage.builder()
                .msgId(chat.getMsgId())
                .timestamp(System.currentTimeMillis() / 1000)
                .code(200)
                .build();
        if (chat.getData() == null) {
            ack.setSuccess(false);
            ack.setCode(400);
            ack.setMessage("消息内容不能为空");
            sendMsg(ctx, ack);
            return;
        }
        if ("gift".equals(chat.getData().getType())) {
            // 资金订单必须经过 wallet-service 的幂等事务，Netty 只接收普通文本聊天。
            ack.setSuccess(false);
            ack.setCode(400);
            ack.setMessage("请使用送礼订单接口");
            sendMsg(ctx, ack);
            return;
        }
        String roomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
        boolean exists = checkRoom(roomId);
        if (roomId == null || !exists) {
            ack.setSuccess(false);
            ack.setMessage("直播间未开播\n或用户未加入房间");
            sendMsg(ctx, ack);
            return;
        }
        if (StrUtil.isBlank(chat.getData().getContent())) {
            ack.setSuccess(false);
            ack.setMessage("发送内容不能为空");
            sendMsg(ctx, ack);
            return;
        }
        ack.setSuccess(true);
        sendMsg(ctx, ack);

        // 构造广播通知 (Notification)，补全发送者信息
        Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
        String username = ctx.channel().attr(SessionManager.KEY_USER_NAME).get();


        popularityUtil.updatePopularity(roomId, liveWeightsProperties.getChat());

        // 构建聊天通知
        GroupChatNotification.ChatData chatData = new GroupChatNotification.ChatData();
        chatData.setType(chat.getData().getType() != null ? chat.getData().getType() : "text");
        chatData.setRoomId(roomId);
        chatData.setUser(username);
        chatData.setUserid(userId);
        chatData.setContent(chat.getData().getContent());
        chatData.setIsVip(false);  // TODO: 从用户信息中获取
        chatData.setLevel(0);      // TODO: 从用户信息中获取

        GroupChatNotification notification = GroupChatNotification.builder()
                .data(chatData)
                .roomId(roomId)
                .timestamp(System.currentTimeMillis() / 1000)
                .build();
        log.info("发送聊天消息: {}", notification);
        broadcastMessage(notification);

        // 判断是否需要进行 AI 检测
        Long rank = stringRedisTemplate.opsForZSet()
                .reverseRank(liveRoomProperties.getMainRank(), roomId);
        if (rank != null) {
            long realRank = rank + 1L;
            if (realRank > aiDetectProperties.getAiDetectThreshold()) {
                return;
            }
            String chatBufferKey = liveRoomProperties.getChatBufferPrefix() + roomId;
            // 只对普通消息进行AI检测
            if ("text".equals(chat.getData().getType()) && StrUtil.isNotBlank(chat.getData().getContent())) {
                stringRedisTemplate.opsForList().rightPush(chatBufferKey, chat.getData().getContent());
            }
//            stringRedisTemplate
//                    .expire(chatBufferKey, aiDetectProperties.getTtl());
            List<String> messages = stringRedisTemplate.execute(
                    harvestChatsScript,
                    Collections.singletonList(chatBufferKey),
                    aiDetectProperties.getCacheThreshold().toString()
            );
            // 超出阈值，说明达到水位线，发送给 MQ
            if (CollUtil.isNotEmpty(messages)) {
                log.info("送审消息: {}", messages);
                Map<String, Object> payload = Map.of(
                        "roomId", roomId,
                        "history", String.join("\n", messages),
                        "trigger", "WATERMARK_REACHED"
                );
                rabbitTemplate.convertAndSend(
                        aiDetectExchangeName,
                        aiDetectRoutingKey,
                        JSONUtil.toJsonStr(payload));
                log.info("房间 {} 达到水位线， {} 条记录送审", roomId, messages.size());
            }

        }
    }

    @Override
    public void handleLeaveRoom(ChannelHandlerContext ctx, LeaveRequest message) {
        String roomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
        Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
        AckMessage ack = AckMessage.builder()
                .timestamp(System.currentTimeMillis() / 1000)
                .msgId(message.getMsgId())
                .build();
        if (StrUtil.isBlank(roomId) || userId == null) {
            log.warn("用户未加入任何房间，无需处理退出请求");
            ack.setSuccess(false);
            ack.setMessage("用户未加入任何房间，无需处理退出请求");
            ack.setCode(400);
            sendMsg(ctx, ack);
            return;
        }
        // 执行离场
        leaveRoom(ctx);
        ack.setSuccess(true);
        sendMsg(ctx, ack);
    }

    @Override
    public void handleDisconnect(ChannelHandlerContext ctx) {
        leaveRoom(ctx);
        sessionManager.remove(ctx.channel());
    }

    @Override
    public void handleLike(ChannelHandlerContext ctx, LikeRequest like) {
        // 点赞直播间
        String roomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
        String username = ctx.channel().attr(SessionManager.KEY_USER_NAME).get();
        Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
        if (!checkRoom(roomId)) {
            return;
        }
        // 点赞属于可丢弃统计消息，独立于资金事实队列；队列高峰时允许 TTL/长度淘汰。
        Map<String, Object> likeEvent = Map.of(
                "roomId", roomId,
                "count", like.getData().getCount(),
                "weight", liveWeightsProperties.getLike());
        rabbitTemplate.convertAndSend(LIKE_STATS_EXCHANGE, "like", likeEvent);
        // 构造点赞通知
        EventMessage.EventData eventData = new EventMessage.EventData();
        eventData.setType("like");
        eventData.setRoomId(roomId);
        eventData.setUser(username);
        eventData.setUserid(userId);
        eventData.setIsVip(false);
        eventData.setLevel(0);
        EventMessage eventMessage = EventMessage.builder()
                .data(eventData)
                .timestamp(System.currentTimeMillis() / 1000)
                .roomId(roomId)
                .msgId(like.getMsgId())
                .build();
        // 点赞展示属于房间级实时事件，和点赞热度统计使用不同交换机，避免统计丢弃影响展示。
        broadcastMessage(eventMessage);
    }

    @Override
    public void handleGenericMessage(ChannelHandlerContext ctx, Message message) {
        // 对于其他类型的消息，直接转发到消息队列
        String currentRoomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
        if (currentRoomId != null) {
            message.setRoomId(currentRoomId);
            message.setUserId(ctx.channel().attr(SessionManager.KEY_USER_ID).get());
            broadcastMessage(message);
        }
    }

    // 广播消息
    private void broadcastMessage(Message message) {
        rabbitTemplate.convertAndSend(exchangeName, "", JSONUtil.toJsonStr(message));
    }

    // 向请求者发送消息
    private void sendMsg(ChannelHandlerContext ctx, Message message) {
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(message)));
    }

    private void leaveRoom(ChannelHandlerContext ctx) {
        Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
        String roomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
        log.info("用户[{}] 退出房间[{}]", userId, roomId);
        ctx.channel().attr(SessionManager.KEY_ROOM_ID).set(null);
        String currentKey = liveRoomProperties.getCurrentPrefix() + roomId;
        stringRedisTemplate.opsForSet().remove(currentKey, userId.toString());
        popularityUtil.updatePopularity(roomId, -liveWeightsProperties.getView());
    }

    // 判断直播间是否存在
    private boolean checkRoom(String roomId) {
        return stringRedisTemplate.hasKey(liveRoomProperties.getSessionPrefix() + roomId);
    }

    // TODO 获取用户会员信息
    private void getUserVipInfo(String userId) {
    }
}
