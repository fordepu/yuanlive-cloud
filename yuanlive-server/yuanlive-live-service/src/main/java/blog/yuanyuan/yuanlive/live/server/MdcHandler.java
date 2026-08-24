package blog.yuanyuan.yuanlive.live.server;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ChannelHandler.Sharable
public class MdcHandler extends ChannelDuplexHandler {
    private static final String TRACE_ID_KEY = "traceId";
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 1. 进站前：设置 MDC
        wrapMdc(ctx);
        try {
            // 2. 执行下一个 Handler (你的业务逻辑)
            super.channelRead(ctx, msg);
        } finally {
            // 3. 业务执行完：清理 MDC
            clearMdc();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        wrapMdc(ctx);
        try {
            super.userEventTriggered(ctx, evt);
        } finally {
            clearMdc();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        wrapMdc(ctx);
        try {
            super.exceptionCaught(ctx, cause);
        } finally {
            clearMdc();
        }
    }

    private void wrapMdc(ChannelHandlerContext ctx) {
        String traceId = ctx.channel().attr(SessionManager.KEY_TRACE_ID).get();
        if (traceId != null) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
        Long userId = ctx.channel().attr(SessionManager.KEY_USER_ID).get();
        if (userId != null) {
            MDC.put("userId", String.valueOf(userId));
        }
        String roomId = ctx.channel().attr(SessionManager.KEY_ROOM_ID).get();
        if (roomId != null) {
            MDC.put("roomId", roomId);
        }
    }

    private void clearMdc() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove("userId");
        MDC.remove("roomId");
    }
}
