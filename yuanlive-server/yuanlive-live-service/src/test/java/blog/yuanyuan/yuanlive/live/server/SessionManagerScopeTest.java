package blog.yuanyuan.yuanlive.live.server;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import blog.yuanyuan.yuanlive.live.realtime.routing.ConnectionRouteRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SessionManagerScopeTest {

    @Test
    void roomChannelDoesNotReplaceApplicationChannelForSameUser() {
        SessionManager sessionManager = new SessionManager();
        ConnectionRouteRegistry routeRegistry = mock(ConnectionRouteRegistry.class);
        ReflectionTestUtils.setField(sessionManager, "connectionRouteRegistry", routeRegistry);
        Channel appChannel = new EmbeddedChannel();
        Channel roomChannel = new EmbeddedChannel();

        sessionManager.registerAppChannel(2001L, appChannel);
        sessionManager.registerRoomChannel("room-2", "desktop", roomChannel);

        assertSame(appChannel, sessionManager.getUserChannel(2001L));
        verify(routeRegistry).registerApp(eq(2001L), anyString());
        verify(routeRegistry).registerRoom("room-2");
        appChannel.close();
        roomChannel.close();
    }
}
