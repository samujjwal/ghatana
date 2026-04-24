/**
 * @doc.type test
 * @doc.purpose Testcontainers-backed integration tests for RedisDistributedCacheAdapter against a real Redis instance
 * @doc.layer platform
 * @doc.pattern IntegrationTest
 */
package com.ghatana.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCachePort;
import com.ghatana.platform.cache.RedisDistributedCacheAdapter;
import com.ghatana.platform.testing.PlatformIntegrationTestBase;
import org.junit.jupiter.api.*;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link RedisDistributedCacheAdapter} using a real Redis container.
 *
 * <p>Verifies put/get round-trips, TTL expiry, namespace isolation, cache-miss
 * semantics, invalidation, and the {@code getOrLoad} cache-aside pattern.
 */
@DisplayName("RedisDistributedCacheAdapter – Redis container integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RedisCacheContainerTest extends PlatformIntegrationTestBase {

    @Override
    protected boolean requiresRedis() {
        return true;
    }

    private DistributedCachePort<String, String> cache;
    private JedisPool jedisPool;

    @BeforeEach
    void setUp() {
        jedisPool = new JedisPool(getRedisHost(), getRedisPort());
        cache = new RedisDistributedCacheAdapter<>(
            jedisPool,
            new ObjectMapper(),
            String.class,
            Executors.newVirtualThreadPerTaskExecutor(),
            "it-test",
            Duration.ofMinutes(5)
        );
    }

    @AfterEach
    void tearDown() {
        // Flush the test namespace to ensure isolation between test methods
        runPromise(() -> cache.invalidateAll());
        jedisPool.close();
    }

    // ── put / get round-trip ──────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("put then get returns the stored value")
    void putThenGetReturnsCachedValue() {
        runPromise(() -> cache.put("greeting", "hello"));

        Optional<String> result = runPromise(() -> cache.get("greeting"));

        assertThat(result).contains("hello");
    }

    @Test
    @Order(2)
    @DisplayName("get returns empty for a key that was never stored")
    void getMissingKeyReturnsEmpty() {
        Optional<String> result = runPromise(() -> cache.get("no-such-key"));

        assertThat(result).isEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("overwriting a key replaces the previous value")
    void putOverwritesPreviousValue() {
        runPromise(() -> cache.put("key", "first"));
        runPromise(() -> cache.put("key", "second"));

        Optional<String> result = runPromise(() -> cache.get("key"));

        assertThat(result).contains("second");
    }

    // ── TTL expiry ────────────────────────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("entry with a 1-second TTL is no longer retrievable after expiry")
    void shortTtlEntryExpiresAndBecomesInvisible() throws InterruptedException {
        runPromise(() -> cache.put("short-lived", "ephemeral", Duration.ofSeconds(1)));

        // Confirm stored
        assertThat(runPromise(() -> cache.get("short-lived"))).contains("ephemeral");

        // Wait for TTL to expire
        Thread.sleep(1_500);

        assertThat(runPromise(() -> cache.get("short-lived"))).isEmpty();
    }

    // ── Invalidation ──────────────────────────────────────────────────────

    @Test
    @Order(5)
    @DisplayName("invalidate removes the entry for the specified key")
    void invalidateSingleKeyRemovesIt() {
        runPromise(() -> cache.put("gone", "value"));
        runPromise(() -> cache.invalidate("gone"));

        assertThat(runPromise(() -> cache.get("gone"))).isEmpty();
    }

    @Test
    @Order(6)
    @DisplayName("invalidateAll removes all keys in the namespace")
    void invalidateAllClearsNamespace() {
        runPromise(() -> cache.put("a", "1"));
        runPromise(() -> cache.put("b", "2"));
        runPromise(() -> cache.put("c", "3"));

        runPromise(() -> cache.invalidateAll());

        assertThat(runPromise(() -> cache.get("a"))).isEmpty();
        assertThat(runPromise(() -> cache.get("b"))).isEmpty();
        assertThat(runPromise(() -> cache.get("c"))).isEmpty();
    }

    // ── Namespace isolation ───────────────────────────────────────────────

    @Test
    @Order(7)
    @DisplayName("two adapters with different namespaces do not share entries")
    void namespacesAreIsolated() {
        DistributedCachePort<String, String> otherCache = new RedisDistributedCacheAdapter<>(
            jedisPool,
            new ObjectMapper(),
            String.class,
            Executors.newVirtualThreadPerTaskExecutor(),
            "it-other-ns",
            Duration.ofMinutes(5)
        );

        runPromise(() -> cache.put("shared-key", "from-it-test"));
        runPromise(() -> otherCache.put("shared-key", "from-it-other-ns"));

        Optional<String> fromMain = runPromise(() -> cache.get("shared-key"));
        Optional<String> fromOther = runPromise(() -> otherCache.get("shared-key"));

        assertThat(fromMain).contains("from-it-test");
        assertThat(fromOther).contains("from-it-other-ns");

        // Cleanup
        runPromise(() -> otherCache.invalidateAll());
    }

    // ── getOrLoad (cache-aside) ───────────────────────────────────────────

    @Test
    @Order(8)
    @DisplayName("getOrLoad returns cached value without invoking the loader")
    void getOrLoadReturnsCachedValueWithoutCallingLoader() {
        runPromise(() -> cache.put("cached-key", "cached-value"));

        String[] loaderCallCount = {"not-called"};
        String result = runPromise(() -> cache.getOrLoad("cached-key", key -> {
            loaderCallCount[0] = "called";
            return io.activej.promise.Promise.of("loader-value");
        }));

        assertThat(result).isEqualTo("cached-value");
        assertThat(loaderCallCount[0]).isEqualTo("not-called");
    }

    @Test
    @Order(9)
    @DisplayName("getOrLoad invokes loader on cache-miss and stores the computed value")
    void getOrLoadCallsLoaderOnMissAndCachesResult() {
        String result = runPromise(() -> cache.getOrLoad("miss-key", key ->
            io.activej.promise.Promise.of("computed-for-" + key)
        ));

        assertThat(result).isEqualTo("computed-for-miss-key");

        // Value should now be cached
        assertThat(runPromise(() -> cache.get("miss-key")))
            .contains("computed-for-miss-key");
    }

    // ── isHealthy ────────────────────────────────────────────────────────

    @Test
    @Order(10)
    @DisplayName("isHealthy returns true when the Redis container is running")
    void isHealthyWhenContainerIsRunning() {
        assertThat(cache.isHealthy()).isTrue();
    }
}
