package blog.yuanyuan.yuanlive.live.server;

import blog.yuanyuan.yuanlive.live.message.*;
import blog.yuanyuan.yuanlive.live.message.request.GroupChatRequest;
import blog.yuanyuan.yuanlive.live.message.request.JoinRequest;
import blog.yuanyuan.yuanlive.live.message.request.LeaveRequest;
import blog.yuanyuan.yuanlive.live.message.request.LikeRequest;
import blog.yuanyuan.yuanlive.live.message.request.PingMessage;
import blog.yuanyuan.yuanlive.live.message.response.AckMessage;
import blog.yuanyuan.yuanlive.live.service.LiveMessageService;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomEventBuffer;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomReplayResult;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomReplayStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@ChannelHandler.Sharable
@Component
@Slf4j
public class NettyServerHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
    @Resource
    private LiveMessageService liveMessageService;
    @Resource
    private SessionManager sessionManager;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private RoomEventBuffer roomEventBuffer;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        liveMessageService.handleDisconnect(ctx);
        super.channelInactive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        // 1. 解析消息
//        log.info("收到消息: {}", frame.text());
        Message message;
        try {
            message = objectMapper.readValue(frame.text(), Message.class);
        } catch (JsonProcessingException exception) {
            // Jackson 多态反序列化会在未知 cmd 时失败；这里转换成协议 ACK，避免异常直接关闭 WebSocket。
            sendProtocolError(ctx, "消息格式错误或不支持的 cmd");
            return;
        }
//        log.info("收到消息: {}", message);
        if (message == null) return;

        // 根据具体消息类型进行处理
        switch (message.getCmd()) {
            case PING -> {
                log.info("收到心跳包");
                liveMessageService.handlePing(ctx, (PingMessage) message);
            }
            case JOIN_ROOM -> liveMessageService.handleJoinRoom(ctx, (JoinRequest) message);
            case CHAT -> liveMessageService.handleChat(ctx, (GroupChatRequest) message);
            case LEAVE_ROOM -> liveMessageService.handleLeaveRoom(ctx, (LeaveRequest) message);
            case LIKE -> liveMessageService.handleLike(ctx, (LikeRequest) message);
            default -> sendProtocolError(ctx, "不支持的 cmd: " + message.getCmd());
        }
    }

    private void sendProtocolError(ChannelHandlerContext ctx, String message) {
        AckMessage response = AckMessage.builder()
                .code(400)
                .success(false)
                .message(message)
                .timestamp(System.currentTimeMillis() / 1000)
                .build();
        ctx.channel().writeAndFlush(new TextWebSocketFrame(objectMapper.valueToTree(response).toString()));
    }

    // 处理心跳超时 (IdleStateHandler 触发)
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        String traceId = ctx.channel().attr(SessionManager.KEY_TRACE_ID).get();
        MDC.put("traceId", traceId);
        try {
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete
                    || evt == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                // 此时 AuthHandshakeHandler 已经执行完毕，Attribute 里有值了
                Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
                String deviceId = ctx.channel().attr(SessionManager.KEY_DEVICE_ID).get();
                ConnectionScope scope = ctx.channel().attr(SessionManager.KEY_CONNECTION_SCOPE).get();

                if (userId != null) {
                    if (scope == ConnectionScope.APP) {
                        sessionManager.registerAppChannel(userId, ctx.channel());
                    } else if (scope == ConnectionScope.ROOM) {
                        joinRequestedRoom(ctx, deviceId);
                    }
                    log.info("用户[{}] 设备[{}] scope=[{}] 握手成功", userId, deviceId, scope);
                }
            } else if (evt instanceof IdleStateEvent event) {
                if (event.state() == IdleState.READER_IDLE) {
                    log.warn("心跳超时，关闭连接: {}", ctx.channel().id());
                    ctx.close();
                }
            } else {
                super.userEventTriggered(ctx, evt);
            }
        } finally {
            MDC.remove("traceId");
        }
    }

    private void joinRequestedRoom(ChannelHandlerContext ctx, String deviceId) {
        String roomId = ctx.channel().attr(SessionManager.KEY_REQUESTED_ROOM_ID).get();
        if (roomId == null || roomId.isBlank()) {
            sendProtocolError(ctx, "ROOM连接缺少roomId");
            ctx.close();
            return;
        }
        JoinRequest.JoinData data = new JoinRequest.JoinData();
        data.setRoomId(roomId);
        data.setDevice(deviceId);
        JoinRequest join = new JoinRequest();
        join.setData(data);
        liveMessageService.handleJoinRoom(ctx, join);
        io.netty.util.Attribute<Long> lastSeqAttribute = ctx.channel().attr(SessionManager.KEY_LAST_SEQ);
        Long lastSeq = lastSeqAttribute == null ? 0L : lastSeqAttribute.get();
        RoomReplayResult replay = roomEventBuffer.replayAfter(roomId, lastSeq == null ? 0L : lastSeq);
        if (replay.status() == RoomReplayStatus.RESYNC_REQUIRED) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"type\":\"RESYNC_REQUIRED\",\"roomId\":\"" + roomId + "\"}"));
            return;
        }
        replay.events().forEach(event -> ctx.channel().writeAndFlush(
                new TextWebSocketFrame(objectMapper.valueToTree(event).toString())));
    }
}
