package blog.yuanyuan.yuanlive.live.server;

import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class SessionManagerScopeTest {

    @Test
    void roomChannelDoesNotReplaceApplicationChannelForSameUser() {
        SessionManager sessionManager = new SessionManager();
        Channel appChannel = new EmbeddedChannel();
        Channel roomChannel = new EmbeddedChannel();

        sessionManager.registerAppChannel(2001L, appChannel);
        sessionManager.registerRoomChannel("room-2", "desktop", roomChannel);

        assertSame(appChannel, sessionManager.getUserChannel(2001L));
        appChannel.close();
        roomChannel.close();
    }
}
