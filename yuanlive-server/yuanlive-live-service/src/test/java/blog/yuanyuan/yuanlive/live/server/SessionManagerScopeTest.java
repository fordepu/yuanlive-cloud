package blog.yuanyuan.yuanlive.live.server;

import blog.yuanyuan.yuanlive.live.realtime.ConnectionScope;
import blog.yuanyuan.yuanlive.live.realtime.routing.ConnectionRouteRegistry;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagerScopeTest {

    @Test
    void roomChannelDoesNotReplaceApplicationChannelForSameUser() {
        SessionManager sessionManager = new SessionManager();
        org.springframework.test.util.ReflectionTestUtils.setField(sessionManager, "connectionRouteRegistry", mock(ConnectionRouteRegistry.class));
        Channel appChannel = new EmbeddedChannel();
        Channel roomChannel = new EmbeddedChannel();

        sessionManager.registerAppChannel(2001L, appChannel);
        sessionManager.registerRoomChannel("room-2", "desktop", roomChannel);

        assertSame(appChannel, sessionManager.getUserChannel(2001L));
    }

    @Test
    void registersAppRouteWithTheGeneratedConnectionId() {
        SessionManager sessionManager = new SessionManager();
        ConnectionRouteRegistry routes = mock(ConnectionRouteRegistry.class);
        org.springframework.test.util.ReflectionTestUtils.setField(sessionManager, "connectionRouteRegistry", routes);
        Channel channel = new EmbeddedChannel();

        sessionManager.registerAppChannel(3001L, channel);

        String connectionId = channel.attr(SessionManager.KEY_CONNECTION_ID).get();
        org.junit.jupiter.api.Assertions.assertNotNull(connectionId);
        verify(routes).registerApp(3001L, connectionId);
    }
}
