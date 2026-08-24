package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.live.message.request.GroupChatRequest;
import blog.yuanyuan.yuanlive.live.message.response.AckMessage;
import blog.yuanyuan.yuanlive.live.properties.LiveRoomProperties;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveMessageServiceImplTest {

    @Test
    void rejectsGroupChatWhenTheChannelHasNotJoinedAnActiveRoom() {
        LiveMessageServiceImpl service = new LiveMessageServiceImpl();
        LiveRoomProperties properties = new LiveRoomProperties();
        properties.setSessionPrefix("test:live:session:");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "liveRoomProperties", properties);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);

        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        Attribute<String> roomAttribute = mock(Attribute.class);
        when(context.channel()).thenReturn(channel);
        when(channel.attr(SessionManager.KEY_ROOM_ID)).thenReturn(roomAttribute);
        when(roomAttribute.get()).thenReturn(null);
        when(redis.hasKey("test:live:session:null")).thenReturn(false);

        GroupChatRequest request = new GroupChatRequest();
        request.setMsgId("message-1");
        GroupChatRequest.ChatData data = new GroupChatRequest.ChatData();
        data.setType("text");
        data.setContent("hello");
        request.setData(data);

        service.handleChat(context, request);

        ArgumentCaptor<TextWebSocketFrame> frameCaptor = ArgumentCaptor.forClass(TextWebSocketFrame.class);
        verify(channel).writeAndFlush(frameCaptor.capture());
        String response = frameCaptor.getValue().text();
        assertTrue(response.contains("ACK"));
        assertTrue(response.contains("直播间未开播"));
    }
}
