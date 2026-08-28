package blog.yuanyuan.yuanlive.live.server;

import blog.yuanyuan.yuanlive.live.service.LiveMessageService;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class NettyServerHandlerTest {

    private NettyServerHandler handler;
    private LiveMessageService liveMessageService;
    private SessionManager sessionManager;
    private ChannelHandlerContext context;

    @BeforeEach
    void setUp() {
        handler = new NettyServerHandler();
        liveMessageService = mock(LiveMessageService.class);
        sessionManager = mock(SessionManager.class);
        context = mock(ChannelHandlerContext.class);
        ReflectionTestUtils.setField(handler, "liveMessageService", liveMessageService);
        ReflectionTestUtils.setField(handler, "sessionManager", sessionManager);
        ReflectionTestUtils.setField(handler, "objectMapper", new ObjectMapper());
    }

    @Test
    void dispatchesLikeCommandToLikeHandler() throws Exception {
        invoke("{\"cmd\":\"LIKE\",\"msgId\":\"like-1\",\"data\":{\"count\":1}}");

        verify(liveMessageService).handleLike(org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(context);
    }

    @Test
    void keepsNormalTextChatOnExistingChatHandler() throws Exception {
        invoke("{\"cmd\":\"CHAT\",\"msgId\":\"chat-1\",\"data\":{\"type\":\"text\",\"content\":\"hello\"}}");

        verify(liveMessageService).handleChat(org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnknownCommandWithClientError() throws Exception {
        io.netty.channel.Channel channel = mock(io.netty.channel.Channel.class);
        org.mockito.Mockito.when(context.channel()).thenReturn(channel);

        invoke("{\"cmd\":\"NOT_SUPPORTED\",\"msgId\":\"unknown-1\"}");

        verify(channel).writeAndFlush(org.mockito.ArgumentMatchers.any(TextWebSocketFrame.class));
        verifyNoInteractions(liveMessageService);
    }

    @Test
    void registersOnlyApplicationConnectionAfterApplicationHandshake() throws Exception {
        io.netty.channel.Channel channel = mock(io.netty.channel.Channel.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<Long> userId = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<String> deviceId = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<ConnectionScope> scope = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<String> traceId = mock(io.netty.util.Attribute.class);
        when(context.channel()).thenReturn(channel);
        when(channel.attr(SessionManager.KEY_USER_ID)).thenReturn(userId);
        when(channel.attr(SessionManager.KEY_DEVICE_ID)).thenReturn(deviceId);
        when(channel.attr(SessionManager.KEY_CONNECTION_SCOPE)).thenReturn(scope);
        when(channel.attr(SessionManager.KEY_TRACE_ID)).thenReturn(traceId);
        when(userId.get()).thenReturn(1001L);
        when(deviceId.get()).thenReturn("device-1");
        when(scope.get()).thenReturn(ConnectionScope.APP);
        when(traceId.get()).thenReturn("trace-1");

        handler.userEventTriggered(context, WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);

        verify(sessionManager).registerAppChannel(1001L, channel);
        verifyNoMoreInteractions(liveMessageService);
    }

    @Test
    void automaticallyJoinsBoundRoomAfterRoomHandshake() throws Exception {
        io.netty.channel.Channel channel = mock(io.netty.channel.Channel.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<Long> userId = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<String> deviceId = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<ConnectionScope> scope = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<String> requestedRoom = mock(io.netty.util.Attribute.class);
        @SuppressWarnings("unchecked")
        io.netty.util.Attribute<String> traceId = mock(io.netty.util.Attribute.class);
        when(context.channel()).thenReturn(channel);
        when(channel.attr(SessionManager.KEY_USER_ID)).thenReturn(userId);
        when(channel.attr(SessionManager.KEY_DEVICE_ID)).thenReturn(deviceId);
        when(channel.attr(SessionManager.KEY_CONNECTION_SCOPE)).thenReturn(scope);
        when(channel.attr(SessionManager.KEY_REQUESTED_ROOM_ID)).thenReturn(requestedRoom);
        when(channel.attr(SessionManager.KEY_TRACE_ID)).thenReturn(traceId);
        when(userId.get()).thenReturn(1001L);
        when(deviceId.get()).thenReturn("device-1");
        when(scope.get()).thenReturn(ConnectionScope.ROOM);
        when(requestedRoom.get()).thenReturn("room-1");
        when(traceId.get()).thenReturn("trace-1");

        handler.userEventTriggered(context, WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE);

        verify(liveMessageService).handleJoinRoom(org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.argThat(join -> "room-1".equals(join.getData().getRoomId())
                        && "device-1".equals(join.getData().getDevice())));
    }

    private void invoke(String payload) throws Exception {
        ReflectionTestUtils.invokeMethod(handler, "channelRead0", context, new TextWebSocketFrame(payload));
    }
}
