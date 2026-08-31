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
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionRouteRegistryTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ZSetOperations<String, String> zSets = mock(ZSetOperations.class);
    private final RealtimeInstanceIdentity identity = new RealtimeInstanceIdentity("live-a:8080", "epoch-a");
    private ConnectionRouteRegistry registry;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForZSet()).thenReturn(zSets);
        registry = new ConnectionRouteRegistry(redis, identity,
                Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC));
    }

    @Test
    void storesAndResolvesAppRouteWithInstanceEpochAndConnectionId() {
        when(values.get("ws:user:1001:app")).thenReturn("live-a:8080|epoch-a|connection-a");
        when(redis.hasKey("ws:instance:live-a:8080:lease")).thenReturn(true);

        Optional<AppConnectionRoute> route = registry.resolveApp(1001L);

        assertEquals(new AppConnectionRoute("live-a:8080", "epoch-a", "connection-a"), route.orElseThrow());
    }

    @Test
    void doesNotRemoveReplacementAppRouteWhenOldConnectionDisconnects() {
        registry.unregisterAppIfCurrent(1001L, "old-connection");

        verify(redis).execute(any(), eq(List.of("ws:user:1001:app")),
                eq("live-a:8080|epoch-a|old-connection"));
    }

    @Test
    void resolvesOnlyRoomInstancesWhoseLeasesAreLive() {
        when(zSets.rangeByScore("ws:room:room-1:instances", 1_000D, Double.MAX_VALUE))
                .thenReturn(Set.of("live-a:8080|epoch-a", "live-b:8080|epoch-b"));
        when(redis.hasKey("ws:instance:live-a:8080:lease")).thenReturn(true);
        when(redis.hasKey("ws:instance:live-b:8080:lease")).thenReturn(false);

        assertEquals(List.of(identity), registry.resolveRoomInstances("room-1"));
    }

    @Test
    void refreshesLeaseUsingCurrentEpoch() {
        registry.refreshInstanceLease();

        verify(values).set(eq("ws:instance:live-a:8080:lease"), eq("epoch-a"), eq(30L), any());
    }
}
