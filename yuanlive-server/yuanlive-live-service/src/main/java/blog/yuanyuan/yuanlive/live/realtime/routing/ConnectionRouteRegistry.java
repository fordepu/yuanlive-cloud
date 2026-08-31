package blog.yuanyuan.yuanlive.live.realtime.routing;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Redis 是跨实例连接归属的最终目录，本组件不保存任何 Netty Channel。 */
@Component
public class ConnectionRouteRegistry {
    static final long INSTANCE_LEASE_SECONDS = 30;
    static final long APP_ROUTE_SECONDS = 45;
    static final long ROOM_MEMBER_SECONDS = 60;

    private static final DefaultRedisScript<Long> DELETE_IF_VALUE_MATCHES = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end return 0",
            Long.class);

    private final StringRedisTemplate redis;
    private final RealtimeInstanceIdentity identity;
    private final Clock clock;

    public ConnectionRouteRegistry(StringRedisTemplate redis, RealtimeInstanceIdentity identity) {
        this(redis, identity, Clock.systemUTC());
    }

    ConnectionRouteRegistry(StringRedisTemplate redis, RealtimeInstanceIdentity identity, Clock clock) {
        this.redis = redis;
        this.identity = identity;
        this.clock = clock;
    }

    public void refreshInstanceLease() {
        redis.opsForValue().set(instanceLeaseKey(identity.instanceId()), identity.epoch(), INSTANCE_LEASE_SECONDS, TimeUnit.SECONDS);
    }

    @Scheduled(fixedDelay = 10_000)
    public void refreshLeaseOnSchedule() {
        refreshInstanceLease();
    }

    public void registerApp(Long userId, String connectionId) {
        redis.opsForValue().set(appRouteKey(userId), identity.memberValue() + "|" + connectionId,
                APP_ROUTE_SECONDS, TimeUnit.SECONDS);
    }

    public Optional<AppConnectionRoute> resolveApp(Long userId) {
        String[] parts = split(redis.opsForValue().get(appRouteKey(userId)), 3);
        if (parts == null || !hasLiveLease(parts[0])) return Optional.empty();
        return Optional.of(new AppConnectionRoute(parts[0], parts[1], parts[2]));
    }

    public void unregisterAppIfCurrent(Long userId, String connectionId) {
        redis.execute(DELETE_IF_VALUE_MATCHES, List.of(appRouteKey(userId)), identity.memberValue() + "|" + connectionId);
    }

    public void registerRoom(String roomId) {
        redis.opsForZSet().add(roomInstancesKey(roomId), identity.memberValue(),
                clock.millis() + TimeUnit.SECONDS.toMillis(ROOM_MEMBER_SECONDS));
        redis.expire(roomInstancesKey(roomId), ROOM_MEMBER_SECONDS, TimeUnit.SECONDS);
    }

    public void unregisterRoom(String roomId) {
        redis.opsForZSet().remove(roomInstancesKey(roomId), identity.memberValue());
    }

    public List<RealtimeInstanceIdentity> resolveRoomInstances(String roomId) {
        var members = redis.opsForZSet().rangeByScore(roomInstancesKey(roomId), clock.millis(), Double.MAX_VALUE);
        if (members == null || members.isEmpty()) return List.of();
        return members.stream()
                .map(member -> split(member, 2))
                .filter(parts -> parts != null && hasLiveLease(parts[0]))
                .map(parts -> new RealtimeInstanceIdentity(parts[0], parts[1]))
                .toList();
    }

    private boolean hasLiveLease(String instanceId) {
        return Boolean.TRUE.equals(redis.hasKey(instanceLeaseKey(instanceId)));
    }

    private static String[] split(String value, int size) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split("\\|", -1);
        return parts.length == size && Arrays.stream(parts).noneMatch(String::isBlank) ? parts : null;
    }

    private static String instanceLeaseKey(String instanceId) {
        return "ws:instance:" + instanceId + ":lease";
    }

    private static String appRouteKey(Long userId) {
        return "ws:user:" + userId + ":app";
    }

    private static String roomInstancesKey(String roomId) {
        return "ws:room:" + roomId + ":instances";
    }
}
