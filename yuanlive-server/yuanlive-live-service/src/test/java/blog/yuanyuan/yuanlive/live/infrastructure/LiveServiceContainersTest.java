package blog.yuanyuan.yuanlive.live.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveServiceContainersTest extends LiveServiceContainers {

    @Test
    void startsTheLiveServiceExternalDependencies() {
        assertTrue(MYSQL.isRunning());
        assertTrue(REDIS.isRunning());
        assertTrue(RABBIT_MQ.isRunning());
    }
}
