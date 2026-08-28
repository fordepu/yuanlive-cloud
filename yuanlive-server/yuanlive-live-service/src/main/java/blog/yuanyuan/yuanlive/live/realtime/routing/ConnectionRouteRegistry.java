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

/**
 * Redis 是跨实例连接归属的最终目录。本组件只登记和查询路由，不保存 Channel 对象。
 */
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
        String value = redis.opsForValue().get(appRouteKey(userId));
        String[] parts = split(value, 3);
        if (parts == null || !hasLiveLease(parts[0])) return Optional.empty();
        return Optional.of(new AppConnectionRoute(parts[0], parts[1], parts[2]));
    }

    public void unregisterAppIfCurrent(Long userId, String connectionId) {
        String expectedValue = identity.memberValue() + "|" + connectionId;
        redis.execute(DELETE_IF_VALUE_MATCHES, List.of(appRouteKey(userId)), expectedValue);
    }

    public void registerRoom(String roomId) {
        String key = roomInstancesKey(roomId);
        double expiryAt = clock.millis() + TimeUnit.SECONDS.toMillis(ROOM_MEMBER_SECONDS);
        redis.opsForZSet().add(key, identity.memberValue(), expiryAt);
        redis.expire(key, ROOM_MEMBER_SECONDS, TimeUnit.SECONDS);
    }

    public void unregisterRoom(String roomId) {
        redis.opsForZSet().remove(roomInstancesKey(roomId), identity.memberValue());
    }

    public List<RealtimeInstanceIdentity> resolveRoomInstances(String roomId) {
        double now = clock.millis();
        var members = redis.opsForZSet().rangeByScore(roomInstancesKey(roomId), now, Double.MAX_VALUE);
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

    private static String[] split(String value, int expectedParts) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split("\\|", -1);
        if (parts.length != expectedParts || Arrays.stream(parts).anyMatch(String::isBlank)) return null;
        return parts;
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
