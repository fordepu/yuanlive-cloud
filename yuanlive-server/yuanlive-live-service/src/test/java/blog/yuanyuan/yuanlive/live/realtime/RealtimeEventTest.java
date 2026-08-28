package blog.yuanyuan.yuanlive.live.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RealtimeEventTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesRoomEventWithScopeAndSequence() throws Exception {
        RealtimeEvent event = new RealtimeEvent(
                "event-1",
                42L,
                ConnectionScope.ROOM,
                "room-1",
                "CHAT",
                1_787_900_000L,
                JsonNodeFactory.instance.objectNode().put("content", "hello"));

        assertEquals("ROOM", objectMapper.readTree(objectMapper.writeValueAsString(event)).path("scope").asText());
        assertEquals(42L, objectMapper.readTree(objectMapper.writeValueAsString(event)).path("seq").asLong());
    }

    @Test
    void rejectsRoomEventWithoutRoomIdOrSequence() {
        assertThrows(IllegalArgumentException.class, () -> new RealtimeEvent(
                "event-1", null, ConnectionScope.ROOM, "room-1", "CHAT", 1L, JsonNodeFactory.instance.nullNode()));
        assertThrows(IllegalArgumentException.class, () -> new RealtimeEvent(
                "event-1", 1L, ConnectionScope.ROOM, "", "CHAT", 1L, JsonNodeFactory.instance.nullNode()));
    }

    @Test
    void allowsApplicationEventWithoutRoomBinding() {
        assertDoesNotThrow(() -> new RealtimeEvent(
                "event-1", null, ConnectionScope.APP, null, "NOTICE", 1L, JsonNodeFactory.instance.nullNode()));
    }
}
