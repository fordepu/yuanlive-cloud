package blog.yuanyuan.yuanlive.ai.infrastructure;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class AiServiceMongoContainerTest {

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer("mongo:8.0");

    @Test
    void startsMongoForAiConversationIntegrationTests() {
        assertTrue(MONGODB.isRunning());
        assertTrue(MONGODB.getReplicaSetUrl().contains("mongodb://"));
    }
}
