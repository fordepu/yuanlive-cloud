package blog.yuanyuan.yuanlive.live.server;

import blog.yuanyuan.yuanlive.live.service.LiveMessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NettyServerHandlerTest {

    private NettyServerHandler handler;
    private LiveMessageService liveMessageService;
    private ChannelHandlerContext context;

    @BeforeEach
    void setUp() {
        handler = new NettyServerHandler();
        liveMessageService = mock(LiveMessageService.class);
        context = mock(ChannelHandlerContext.class);
        ReflectionTestUtils.setField(handler, "liveMessageService", liveMessageService);
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

    private void invoke(String payload) throws Exception {
        ReflectionTestUtils.invokeMethod(handler, "channelRead0", context, new TextWebSocketFrame(payload));
    }
}
