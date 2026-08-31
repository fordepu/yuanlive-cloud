package blog.yuanyuan.yuanlive.gateway.routing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RendezvousHashSelectorTest {
    @Test
    void 应稳定选择同一个候选实例并排除draining实例() {
        var candidates = List.of(
                new RendezvousHashSelector.Candidate("live-service@10.0.0.1:9000", false),
                new RendezvousHashSelector.Candidate("live-service@10.0.0.2:9000", false),
                new RendezvousHashSelector.Candidate("live-service@10.0.0.3:9000", true));

        String first = RendezvousHashSelector.select("room-42", candidates).orElseThrow();
        assertEquals(first, RendezvousHashSelector.select("room-42", candidates).orElseThrow());
        assertEquals(false, first.contains("10.0.0.3"));
    }

    @Test
    void 新增实例只在候选集内重算且原候选仍可被选择() {
        var before = List.of(
                new RendezvousHashSelector.Candidate("live-service@10.0.0.1:9000", false),
                new RendezvousHashSelector.Candidate("live-service@10.0.0.2:9000", false));
        var after = List.of(
                before.get(0), before.get(1),
                new RendezvousHashSelector.Candidate("live-service@10.0.0.3:9000", false));

        for (int index = 0; index < 100; index++) {
            String selected = RendezvousHashSelector.select("room-" + index, after).orElseThrow();
            assertEquals(true, after.stream().anyMatch(candidate -> candidate.instanceId().equals(selected)));
        }
    }
}
