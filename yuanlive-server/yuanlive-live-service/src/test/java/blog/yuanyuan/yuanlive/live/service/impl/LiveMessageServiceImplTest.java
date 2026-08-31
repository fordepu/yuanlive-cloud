package blog.yuanyuan.yuanlive.live.service.impl;

import blog.yuanyuan.yuanlive.live.message.request.GroupChatRequest;
import blog.yuanyuan.yuanlive.live.message.request.JoinRequest;
import blog.yuanyuan.yuanlive.live.message.request.LikeRequest;
import blog.yuanyuan.yuanlive.live.message.response.AckMessage;
import blog.yuanyuan.yuanlive.live.properties.LiveRoomProperties;
import blog.yuanyuan.yuanlive.live.properties.LiveWeightsProperties;
import blog.yuanyuan.yuanlive.live.server.SessionManager;
import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.replay.RoomRealtimeEventPublisher;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import java.util.Map;

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

    @Test
    void rejectsGiftChatAndRequiresGiftOrderApi() {
        LiveMessageServiceImpl service = new LiveMessageServiceImpl();
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);

        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        when(context.channel()).thenReturn(channel);

        GroupChatRequest request = new GroupChatRequest();
        request.setMsgId("gift-chat-1");
        GroupChatRequest.ChatData data = new GroupChatRequest.ChatData();
        data.setType("gift");
        data.setGiftCount(1);
        request.setData(data);

        service.handleChat(context, request);

        ArgumentCaptor<TextWebSocketFrame> frameCaptor = ArgumentCaptor.forClass(TextWebSocketFrame.class);
        verify(channel).writeAndFlush(frameCaptor.capture());
        String response = frameCaptor.getValue().text();
        assertTrue(response.contains("请使用送礼订单接口"));
    }

    @Test
    void rejectsRoomJoinFromApplicationConnection() {
        LiveMessageServiceImpl service = new LiveMessageServiceImpl();
        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        Attribute<ConnectionScope> scopeAttribute = mock(Attribute.class);
        when(context.channel()).thenReturn(channel);
        when(channel.attr(SessionManager.KEY_CONNECTION_SCOPE)).thenReturn(scopeAttribute);
        when(scopeAttribute.get()).thenReturn(ConnectionScope.APP);

        JoinRequest request = new JoinRequest();
        request.setMsgId("join-1");
        JoinRequest.JoinData data = new JoinRequest.JoinData();
        data.setRoomId("room-1");
        request.setData(data);

        service.handleJoinRoom(context, request);

        ArgumentCaptor<TextWebSocketFrame> frameCaptor = ArgumentCaptor.forClass(TextWebSocketFrame.class);
        verify(channel).writeAndFlush(frameCaptor.capture());
        assertTrue(frameCaptor.getValue().text().contains("APP连接不能加入直播间"));
    }

    @Test
    void broadcastsLikeEventToRoomWithEventData() {
        LiveMessageServiceImpl service = new LiveMessageServiceImpl();
        LiveRoomProperties properties = new LiveRoomProperties();
        properties.setSessionPrefix("test:live:session:");
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        LiveWeightsProperties weights = new LiveWeightsProperties();
        ReflectionTestUtils.setField(service, "liveRoomProperties", properties);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(service, "liveWeightsProperties", weights);
        RoomRealtimeEventPublisher roomPublisher = mock(RoomRealtimeEventPublisher.class);
        ReflectionTestUtils.setField(service, "roomRealtimeEventPublisher", roomPublisher);

        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        @SuppressWarnings("unchecked")
        Attribute<String> roomAttribute = mock(Attribute.class);
        @SuppressWarnings("unchecked")
        Attribute<String> usernameAttribute = mock(Attribute.class);
        @SuppressWarnings("unchecked")
        Attribute<Long> userIdAttribute = mock(Attribute.class);
        when(context.channel()).thenReturn(channel);
        when(channel.attr(SessionManager.KEY_ROOM_ID)).thenReturn(roomAttribute);
        when(channel.attr(SessionManager.KEY_USER_NAME)).thenReturn(usernameAttribute);
        when(channel.attr(SessionManager.KEY_USER_ID)).thenReturn(userIdAttribute);
        when(roomAttribute.get()).thenReturn("room-1");
        when(usernameAttribute.get()).thenReturn("alice");
        when(userIdAttribute.get()).thenReturn(42L);
        when(redis.hasKey("test:live:session:room-1")).thenReturn(true);

        LikeRequest request = new LikeRequest();
        request.setMsgId("like-1");
        LikeRequest.LikeData data = new LikeRequest.LikeData();
        data.setCount(3);
        request.setData(data);

        service.handleLike(context, request);

        verify(rabbit).convertAndSend(eq("live.stats.exchange"), eq("like"), any(Map.class));
        verify(roomPublisher).publish(org.mockito.ArgumentMatchers.argThat(message ->
                "room-1".equals(message.getRoomId()) && "EVENT".equals(message.getCmd().name())));
    }
}
