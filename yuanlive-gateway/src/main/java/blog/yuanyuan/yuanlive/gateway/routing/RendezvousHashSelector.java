package blog.yuanyuan.yuanlive.gateway.routing;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** WebSocket 新建连接使用 HRW；不保存状态，实例增减只影响一部分路由键。 */
public final class RendezvousHashSelector {
    private RendezvousHashSelector() {
    }

    public record Candidate(String instanceId, boolean draining) {
    }

    public static Optional<String> select(String routeKey, List<Candidate> candidates) {
        return candidates.stream()
                .filter(candidate -> !candidate.draining())
                .max((left, right) -> {
                    int score = Long.compareUnsigned(hash64(routeKey + "|" + left.instanceId()), hash64(routeKey + "|" + right.instanceId()));
                    return score != 0 ? score : Comparator.<String>naturalOrder().compare(left.instanceId(), right.instanceId());
                })
                .map(Candidate::instanceId);
    }

    /** MurmurHash3 x64 的稳定 64 位分数，不能使用进程相关或 String.hashCode。 */
    static long hash64(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        long hash = 0x9368e53c2f6af274L;
        for (byte b : bytes) {
            hash ^= b & 0xffL;
            hash *= 0x87c37b91114253d5L;
            hash = Long.rotateLeft(hash, 31);
        }
        hash ^= bytes.length;
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdL;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53L;
        return hash ^ hash >>> 33;
    }
}
