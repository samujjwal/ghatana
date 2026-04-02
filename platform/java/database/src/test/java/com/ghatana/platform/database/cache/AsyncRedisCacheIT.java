package com.ghatana.platform.database.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link AsyncRedisCache} using a real Redis instance via Testcontainers.
 *
 * @doc.type class
 * @doc.purpose Integration tests for async Redis cache with JSON serialization
 * @doc.layer platform
 * @doc.pattern Integration Test
 */
@Tag("integration")
@Testcontainers
@DisplayName("AsyncRedisCache Integration Tests")
class AsyncRedisCacheIT extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    private AsyncRedisCache<String> cache;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpCache() {
        objectMapper = new ObjectMapper();
        RedisCacheConfig config = RedisCacheConfig.builder()
                .host(REDIS.getHost())
                .port(REDIS.getFirstMappedPort())
                .database(0)
                .ttlSeconds(60)
                .keyPrefix("test:")
                .timeout(Duration.ofSeconds(5))
                .build();
        cache = AsyncRedisCache.create(config, objectMapper, String.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (cache != null) {
            // Clear all test keys before closing
            runPromise(() -> cache.clearWithPrefix("test:"));
            cache.close();
        }
    }

    @Test
    @DisplayName("put stores value and get retrieves it")
    void putAndGet() {
        runPromise(() -> cache.put("test:key1", "hello"));
        Optional<String> result = runPromise(() -> cache.get("test:key1"));
        assertThat(result).isPresent().hasValue("hello");
    }

    @Test
    @DisplayName("get returns empty Optional for missing key")
    void getMissing() {
        Optional<String> result = runPromise(() -> cache.get("test:nonexistent-" + System.nanoTime()));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("remove deletes a stored key")
    void remove() {
        runPromise(() -> cache.put("test:key-remove", "value"));
        Boolean removed = runPromise(() -> cache.remove("test:key-remove"));
        assertThat(removed).isTrue();
        Optional<String> result = runPromise(() -> cache.get("test:key-remove"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("remove returns false for non-existent key")
    void removeNonExistent() {
        Boolean removed = runPromise(() -> cache.remove("test:ghost-" + System.nanoTime()));
        assertThat(removed).isFalse();
    }

    @Test
    @DisplayName("exists returns true after put")
    void existsAfterPut() {
        runPromise(() -> cache.put("test:exists-key", "value"));
        Boolean exists = runPromise(() -> cache.exists("test:exists-key"));
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("exists returns false for missing key")
    void existsMissing() {
        Boolean exists = runPromise(() -> cache.exists("test:missing-" + System.nanoTime()));
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("putAll and getAll batch operations work correctly")
    void putAllAndGetAll() {
        Map<String, String> batch = Map.of(
                "test:batch1", "alpha",
                "test:batch2", "beta",
                "test:batch3", "gamma"
        );
        runPromise(() -> cache.putAll(batch));

        Map<String, String> results = runPromise(() ->
                cache.getAll(List.of("test:batch1", "test:batch2", "test:batch3")));
        assertThat(results)
                .containsEntry("test:batch1", "alpha")
                .containsEntry("test:batch2", "beta")
                .containsEntry("test:batch3", "gamma");
    }

    @Test
    @DisplayName("getAll returns empty map when all keys missing")
    void getAllMissing() {
        Map<String, String> results = runPromise(() ->
                cache.getAll(List.of("test:absent1", "test:absent2")));
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("clearWithPrefix removes all matching keys")
    void clearWithPrefix() {
        runPromise(() -> cache.put("test:prefix:a", "1"));
        runPromise(() -> cache.put("test:prefix:b", "2"));
        runPromise(() -> cache.clearWithPrefix("test:prefix:"));

        Optional<String> a = runPromise(() -> cache.get("test:prefix:a"));
        Optional<String> b = runPromise(() -> cache.get("test:prefix:b"));
        assertThat(a).isEmpty();
        assertThat(b).isEmpty();
    }

    @Test
    @DisplayName("countWithPrefix returns count of matching keys")
    void countWithPrefix() {
        String pfx = "test:counted:" + System.nanoTime() + ":";
        runPromise(() -> cache.put(pfx + "x", "1"));
        runPromise(() -> cache.put(pfx + "y", "2"));
        Long count = runPromise(() -> cache.countWithPrefix(pfx));
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("removeAll deletes multiple keys and returns count")
    void removeAll() {
        runPromise(() -> cache.put("test:rm1", "a"));
        runPromise(() -> cache.put("test:rm2", "b"));
        Long removed = runPromise(() -> cache.removeAll(Set.of("test:rm1", "test:rm2")));
        assertThat(removed).isEqualTo(2L);
    }

    @Test
    @DisplayName("snapshotStats reports hits and misses")
    void snapshotStats() {
        runPromise(() -> cache.put("test:stats-key", "value"));
        runPromise(() -> cache.get("test:stats-key"));       // hit
        runPromise(() -> cache.get("test:missing-stats"));   // miss

        AsyncRedisCache.CacheStats stats = cache.snapshotStats();
        assertThat(stats.hits()).isGreaterThanOrEqualTo(1L);
        assertThat(stats.misses()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("overwrite replaces existing value")
    void overwrite() {
        runPromise(() -> cache.put("test:ow-key", "first"));
        runPromise(() -> cache.put("test:ow-key", "second"));
        Optional<String> result = runPromise(() -> cache.get("test:ow-key"));
        assertThat(result).isPresent().hasValue("second");
    }

    @Test
    @DisplayName("getAll with empty key list returns empty map")
    void getAllWithEmptyKeys() {
        Map<String, String> results = runPromise(() -> cache.getAll(List.of()));
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("putAll with empty map is a no-op")
    void putAllEmpty() {
        runPromise(() -> cache.putAll(Map.of())); // should not throw
    }
}
