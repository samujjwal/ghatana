package com.ghatana.platform.database.cache.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link RedisPubSubManager} using a real Redis instance via Testcontainers.
 *
 * @doc.type class
 * @doc.purpose Integration tests for Redis pub/sub cache invalidation manager
 * @doc.layer platform
 * @doc.pattern Integration Test
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers
@DisplayName("RedisPubSubManager Integration Tests")
class RedisPubSubManagerIT extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379); // GH-90000

    private JedisPool jedisPool;
    private ObjectMapper objectMapper;
    private RedisPubSubManager publisherManager;
    private RedisPubSubManager subscriberManager;

    @BeforeEach
    void setUp() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        objectMapper.findAndRegisterModules(); // for Instant serialization // GH-90000

        JedisPoolConfig config = new JedisPoolConfig(); // GH-90000
        config.setMaxTotal(16); // GH-90000
        jedisPool = new JedisPool(config, REDIS.getHost(), REDIS.getFirstMappedPort(), 5000); // GH-90000

        publisherManager = new RedisPubSubManager( // GH-90000
                jedisPool, objectMapper, "test-channel", "publisher-1", new NoopMetricsCollector()); // GH-90000
        subscriberManager = new RedisPubSubManager( // GH-90000
                jedisPool, objectMapper, "test-channel", "subscriber-2", new NoopMetricsCollector()); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (subscriberManager != null) { // GH-90000
            runPromise(() -> subscriberManager.stop()); // GH-90000
        }
        if (publisherManager != null) { // GH-90000
            runPromise(() -> publisherManager.stop()); // GH-90000
        }
        if (jedisPool != null) { // GH-90000
            jedisPool.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("start succeeds and isRunning reflects running state via stats")
    void startAndStop() { // GH-90000
        runPromise(() -> subscriberManager.start()); // GH-90000
        RedisPubSubManager.PubSubStats stats = subscriberManager.getStats(); // GH-90000
        assertThat(stats.isRunning()).isTrue(); // GH-90000

        runPromise(() -> subscriberManager.stop()); // GH-90000
        RedisPubSubManager.PubSubStats stoppedStats = subscriberManager.getStats(); // GH-90000
        assertThat(stoppedStats.isRunning()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("publish increments publish count in stats")
    void publishIncrementsCount() { // GH-90000
        runPromise(() -> subscriberManager.start()); // GH-90000
        CacheInvalidationMessage msg = CacheInvalidationMessage.invalidateKeys( // GH-90000
                Set.of("user:123"), "tenant-a", "publisher-1");

        runPromise(() -> publisherManager.publish(msg)); // GH-90000

        RedisPubSubManager.PubSubStats stats = publisherManager.getStats(); // GH-90000
        assertThat(stats.getPublishCount()).isEqualTo(1L); // GH-90000
    }

    @Test
    @DisplayName("subscriber receives published message from a different instance")
    void subscriberReceivesMessage() throws InterruptedException { // GH-90000
        CountDownLatch latch = new CountDownLatch(1); // GH-90000
        List<CacheInvalidationMessage> received = new ArrayList<>(); // GH-90000

        runPromise(() -> subscriberManager.start()); // GH-90000
        // Allow subscriber thread to initialize and subscribe
        Thread.sleep(500); // GH-90000

        subscriberManager.subscribe(msg -> { // GH-90000
            received.add(msg); // GH-90000
            latch.countDown(); // GH-90000
        });

        CacheInvalidationMessage outbound = CacheInvalidationMessage.invalidateKeys( // GH-90000
                Set.of("key:abc"), "tenant-b", "publisher-1");
        runPromise(() -> publisherManager.publish(outbound)); // GH-90000

        boolean delivered = latch.await(5, TimeUnit.SECONDS); // GH-90000
        assertThat(delivered).isTrue(); // GH-90000
        assertThat(received).hasSize(1); // GH-90000
        assertThat(received.get(0).getOperation()) // GH-90000
                .isEqualTo(CacheInvalidationMessage.Operation.INVALIDATE_KEYS); // GH-90000
    }

    @Test
    @DisplayName("messages from same instance are skipped by subscriber")
    void sameInstanceMessagesSkipped() throws InterruptedException { // GH-90000
        // Use same instanceId for both publisher and subscriber
        RedisPubSubManager sameInstance = new RedisPubSubManager( // GH-90000
                jedisPool, objectMapper, "test-channel-self", "same-id", new NoopMetricsCollector()); // GH-90000

        CountDownLatch latch = new CountDownLatch(1); // GH-90000
        List<CacheInvalidationMessage> received = new ArrayList<>(); // GH-90000

        try {
            runPromise(() -> sameInstance.start()); // GH-90000
            Thread.sleep(300); // GH-90000

            sameInstance.subscribe(msg -> { // GH-90000
                received.add(msg); // GH-90000
                latch.countDown(); // GH-90000
            });

            CacheInvalidationMessage selfMsg = CacheInvalidationMessage.invalidateKeys( // GH-90000
                    Set.of("own-key"), "tenant-x", "same-id");
            runPromise(() -> sameInstance.publish(selfMsg)); // GH-90000

            // Should NOT deliver because sourceInstance == instanceId
            boolean delivered = latch.await(2, TimeUnit.SECONDS); // GH-90000
            assertThat(delivered).isFalse(); // GH-90000
            assertThat(received).isEmpty(); // GH-90000
        } finally {
            runPromise(() -> sameInstance.stop()); // GH-90000
        }
    }

    @Test
    @DisplayName("subscribe adds listener, unsubscribe removes it")
    void subscribeAndUnsubscribe() { // GH-90000
        List<CacheInvalidationMessage> received = new ArrayList<>(); // GH-90000
        CacheInvalidationListener listener = received::add;

        subscriberManager.subscribe(listener); // GH-90000
        assertThat(subscriberManager.getStats().getListenerCount()).isEqualTo(1); // GH-90000

        subscriberManager.unsubscribe(listener); // GH-90000
        assertThat(subscriberManager.getStats().getListenerCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("clearNamespace message is published successfully")
    void publishClearNamespace() { // GH-90000
        runPromise(() -> subscriberManager.start()); // GH-90000
        CacheInvalidationMessage msg = CacheInvalidationMessage.clearNamespace( // GH-90000
                "tenant-clear", "publisher-1");
        runPromise(() -> publisherManager.publish(msg)); // GH-90000

        assertThat(publisherManager.getStats().getPublishCount()).isEqualTo(1L); // GH-90000
    }

    @Test
    @DisplayName("double start is idempotent — does not throw")
    void doubleStart() { // GH-90000
        runPromise(() -> subscriberManager.start()); // GH-90000
        runPromise(() -> subscriberManager.start()); // idempotent // GH-90000
        assertThat(subscriberManager.getStats().isRunning()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("double stop is idempotent — does not throw")
    void doubleStop() { // GH-90000
        runPromise(() -> subscriberManager.start()); // GH-90000
        runPromise(() -> subscriberManager.stop()); // GH-90000
        runPromise(() -> subscriberManager.stop()); // idempotent // GH-90000
        assertThat(subscriberManager.getStats().isRunning()).isFalse(); // GH-90000
    }
}
