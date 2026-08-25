package blog.yuanyuan.yuanlive.live.server;

import blog.yuanyuan.yuanlive.live.message.*;
import blog.yuanyuan.yuanlive.live.message.request.GroupChatRequest;
import blog.yuanyuan.yuanlive.live.message.request.JoinRequest;
import blog.yuanyuan.yuanlive.live.message.request.LeaveRequest;
import blog.yuanyuan.yuanlive.live.message.request.LikeRequest;
import blog.yuanyuan.yuanlive.live.message.request.PingMessage;
import blog.yuanyuan.yuanlive.live.message.response.AckMessage;
import blog.yuanyuan.yuanlive.live.service.LiveMessageService;
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
            if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                // 此时 AuthHandshakeHandler 已经执行完毕，Attribute 里有值了
                Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
                String deviceId = ctx.channel().attr(SessionManager.KEY_DEVICE_ID).get();

                if (userId != null) {
                    // ✅ 在这里注册才是真正有效的
                    sessionManager.register(userId, ctx.channel());
                    log.info("✅ 用户[{}] 设备[{}] 握手成功，正式上线！", userId, deviceId);
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
}
