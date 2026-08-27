package blog.yuanyuan.yuanlive.wallet.query;

import blog.yuanyuan.yuanlive.wallet.application.dto.WalletAccountResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletApiSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesExternalIdentifiersAndCursorAsStrings() throws Exception {
        JsonNode account = objectMapper.readTree(objectMapper.writeValueAsString(
                new WalletAccountResult(900000000000000001L, 100L, "ACTIVE")));
        JsonNode catalog = objectMapper.readTree(objectMapper.writeValueAsString(
                new GiftCatalogQueryResult(900000000000000002L, "ROSE", "玫瑰", "rose.png", 10L, "ON_SHELF")));
        JsonNode order = objectMapper.readTree(objectMapper.writeValueAsString(
                new GiftOrderQueryResult("G-1", 900000000000000003L, 900000000000000004L,
                        900000000000000005L, 900000000000000006L, 1, 10L, 1000, 1L, 9L, "PAID")));
        JsonNode page = objectMapper.readTree(objectMapper.writeValueAsString(
                new CursorPage<>(java.util.List.of(), 900000000000000007L)));

        assertTrue(account.path("userId").isTextual());
        assertTrue(catalog.path("id").isTextual());
        assertTrue(order.path("senderId").isTextual());
        assertTrue(order.path("roomId").isTextual());
        assertTrue(order.path("anchorId").isTextual());
        assertTrue(order.path("giftId").isTextual());
        assertTrue(page.path("nextCursor").isTextual());
    }
}
