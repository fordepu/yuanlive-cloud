package blog.yuanyuan.yuanlive.live.realtime.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionRouteRegistryTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ZSetOperations<String, String> zSet = mock(ZSetOperations.class);
    private final RealtimeInstanceIdentity identity = new RealtimeInstanceIdentity("live-a", "epoch-a");
    private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_000_000L), ZoneOffset.UTC);
    private ConnectionRouteRegistry registry;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zSet);
        registry = new ConnectionRouteRegistry(redis, identity, clock);
    }

    @Test
    void registersApplicationRouteWithConnectionIdAndLease() {
        registry.refreshInstanceLease();
        registry.registerApp(1001L, "connection-new");

        verify(values).set("ws:instance:live-a:lease", "epoch-a", 30, TimeUnit.SECONDS);
        verify(values).set("ws:user:1001:app", "live-a|epoch-a|connection-new", 45, TimeUnit.SECONDS);
    }

    @Test
    void resolvesOnlyRoomInstancesWhoseLeaseIsStillValid() {
        when(zSet.rangeByScore("ws:room:room-1:instances", 1_000_000D, Double.MAX_VALUE))
                .thenReturn(Set.of("live-a|epoch-a", "live-gone|epoch-z"));
        when(redis.hasKey("ws:instance:live-a:lease")).thenReturn(true);
        when(redis.hasKey("ws:instance:live-gone:lease")).thenReturn(false);

        List<RealtimeInstanceIdentity> routes = registry.resolveRoomInstances("room-1");

        assertEquals(List.of(identity), routes);
    }

    @Test
    void registersRoomInstanceWithExpiringMembership() {
        registry.registerRoom("room-1");

        verify(zSet).add("ws:room:room-1:instances", "live-a|epoch-a", 1_060_000D);
        verify(redis).expire("ws:room:room-1:instances", 60, TimeUnit.SECONDS);
    }

    @Test
    void parsesApplicationRouteOnlyWhenTargetLeaseExists() {
        when(values.get("ws:user:1001:app")).thenReturn("live-a|epoch-a|connection-1");
        when(redis.hasKey("ws:instance:live-a:lease")).thenReturn(true);

        assertTrue(registry.resolveApp(1001L).isPresent());
        assertEquals("connection-1", registry.resolveApp(1001L).orElseThrow().connectionId());
    }
}
