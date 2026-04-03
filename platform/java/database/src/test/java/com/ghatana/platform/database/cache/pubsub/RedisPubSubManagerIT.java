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
@Testcontainers
@DisplayName("RedisPubSubManager Integration Tests")
class RedisPubSubManagerIT extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private JedisPool jedisPool;
    private ObjectMapper objectMapper;
    private RedisPubSubManager publisherManager;
    private RedisPubSubManager subscriberManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // for Instant serialization

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        jedisPool = new JedisPool(config, REDIS.getHost(), REDIS.getFirstMappedPort(), 5000);

        publisherManager = new RedisPubSubManager(
                jedisPool, objectMapper, "test-channel", "publisher-1", new NoopMetricsCollector());
        subscriberManager = new RedisPubSubManager(
                jedisPool, objectMapper, "test-channel", "subscriber-2", new NoopMetricsCollector());
    }

    @AfterEach
    void tearDown() {
        if (subscriberManager != null) {
            runPromise(() -> subscriberManager.stop());
        }
        if (publisherManager != null) {
            runPromise(() -> publisherManager.stop());
        }
        if (jedisPool != null) {
            jedisPool.close();
        }
    }

    @Test
    @DisplayName("start succeeds and isRunning reflects running state via stats")
    void startAndStop() {
        runPromise(() -> subscriberManager.start());
        RedisPubSubManager.PubSubStats stats = subscriberManager.getStats();
        assertThat(stats.isRunning()).isTrue();

        runPromise(() -> subscriberManager.stop());
        RedisPubSubManager.PubSubStats stoppedStats = subscriberManager.getStats();
        assertThat(stoppedStats.isRunning()).isFalse();
    }

    @Test
    @DisplayName("publish increments publish count in stats")
    void publishIncrementsCount() {
        runPromise(() -> subscriberManager.start());
        CacheInvalidationMessage msg = CacheInvalidationMessage.invalidateKeys(
                Set.of("user:123"), "tenant-a", "publisher-1");

        runPromise(() -> publisherManager.publish(msg));

        RedisPubSubManager.PubSubStats stats = publisherManager.getStats();
        assertThat(stats.getPublishCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("subscriber receives published message from a different instance")
    void subscriberReceivesMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<CacheInvalidationMessage> received = new ArrayList<>();

        runPromise(() -> subscriberManager.start());
        // Allow subscriber thread to initialize and subscribe
        Thread.sleep(500);

        subscriberManager.subscribe(msg -> {
            received.add(msg);
            latch.countDown();
        });

        CacheInvalidationMessage outbound = CacheInvalidationMessage.invalidateKeys(
                Set.of("key:abc"), "tenant-b", "publisher-1");
        runPromise(() -> publisherManager.publish(outbound));

        boolean delivered = latch.await(5, TimeUnit.SECONDS);
        assertThat(delivered).isTrue();
        assertThat(received).hasSize(1);
        assertThat(received.get(0).getOperation())
                .isEqualTo(CacheInvalidationMessage.Operation.INVALIDATE_KEYS);
    }

    @Test
    @DisplayName("messages from same instance are skipped by subscriber")
    void sameInstanceMessagesSkipped() throws InterruptedException {
        // Use same instanceId for both publisher and subscriber
        RedisPubSubManager sameInstance = new RedisPubSubManager(
                jedisPool, objectMapper, "test-channel-self", "same-id", new NoopMetricsCollector());

        CountDownLatch latch = new CountDownLatch(1);
        List<CacheInvalidationMessage> received = new ArrayList<>();

        try {
            runPromise(() -> sameInstance.start());
            Thread.sleep(300);

            sameInstance.subscribe(msg -> {
                received.add(msg);
                latch.countDown();
            });

            CacheInvalidationMessage selfMsg = CacheInvalidationMessage.invalidateKeys(
                    Set.of("own-key"), "tenant-x", "same-id");
            runPromise(() -> sameInstance.publish(selfMsg));

            // Should NOT deliver because sourceInstance == instanceId
            boolean delivered = latch.await(2, TimeUnit.SECONDS);
            assertThat(delivered).isFalse();
            assertThat(received).isEmpty();
        } finally {
            runPromise(() -> sameInstance.stop());
        }
    }

    @Test
    @DisplayName("subscribe adds listener, unsubscribe removes it")
    void subscribeAndUnsubscribe() {
        List<CacheInvalidationMessage> received = new ArrayList<>();
        CacheInvalidationListener listener = received::add;

        subscriberManager.subscribe(listener);
        assertThat(subscriberManager.getStats().getListenerCount()).isEqualTo(1);

        subscriberManager.unsubscribe(listener);
        assertThat(subscriberManager.getStats().getListenerCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("clearNamespace message is published successfully")
    void publishClearNamespace() {
        runPromise(() -> subscriberManager.start());
        CacheInvalidationMessage msg = CacheInvalidationMessage.clearNamespace(
                "tenant-clear", "publisher-1");
        runPromise(() -> publisherManager.publish(msg));

        assertThat(publisherManager.getStats().getPublishCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("double start is idempotent — does not throw")
    void doubleStart() {
        runPromise(() -> subscriberManager.start());
        runPromise(() -> subscriberManager.start()); // idempotent
        assertThat(subscriberManager.getStats().isRunning()).isTrue();
    }

    @Test
    @DisplayName("double stop is idempotent — does not throw")
    void doubleStop() {
        runPromise(() -> subscriberManager.start());
        runPromise(() -> subscriberManager.stop());
        runPromise(() -> subscriberManager.stop()); // idempotent
        assertThat(subscriberManager.getStats().isRunning()).isFalse();
    }
}
