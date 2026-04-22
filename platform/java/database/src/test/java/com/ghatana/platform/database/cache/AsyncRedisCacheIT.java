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
@Tag("integration [GH-90000]")
@Testcontainers
@DisplayName("AsyncRedisCache Integration Tests [GH-90000]")
class AsyncRedisCacheIT extends EventloopTestBase {

    @Container
    @SuppressWarnings("resource [GH-90000]")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine [GH-90000]"))
                    .withExposedPorts(6379); // GH-90000

    private AsyncRedisCache<String> cache;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpCache() { // GH-90000
        objectMapper = new ObjectMapper(); // GH-90000
        RedisCacheConfig config = RedisCacheConfig.builder() // GH-90000
                .host(REDIS.getHost()) // GH-90000
                .port(REDIS.getFirstMappedPort()) // GH-90000
                .database(0) // GH-90000
                .ttlSeconds(60) // GH-90000
                .keyPrefix("test: [GH-90000]")
                .timeout(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000
        cache = AsyncRedisCache.create(config, objectMapper, String.class); // GH-90000
    }

    @AfterEach
    void tearDown() throws Exception { // GH-90000
        if (cache != null) { // GH-90000
            // Clear all test keys before closing
            runPromise(() -> cache.clearWithPrefix("test: [GH-90000]"));
            cache.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("put stores value and get retrieves it [GH-90000]")
    void putAndGet() { // GH-90000
        runPromise(() -> cache.put("test:key1", "hello")); // GH-90000
        Optional<String> result = runPromise(() -> cache.get("test:key1 [GH-90000]"));
        assertThat(result).isPresent().hasValue("hello [GH-90000]");
    }

    @Test
    @DisplayName("get returns empty Optional for missing key [GH-90000]")
    void getMissing() { // GH-90000
        Optional<String> result = runPromise(() -> cache.get("test:nonexistent-" + System.nanoTime())); // GH-90000
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("remove deletes a stored key [GH-90000]")
    void remove() { // GH-90000
        runPromise(() -> cache.put("test:key-remove", "value")); // GH-90000
        Boolean removed = runPromise(() -> cache.remove("test:key-remove [GH-90000]"));
        assertThat(removed).isTrue(); // GH-90000
        Optional<String> result = runPromise(() -> cache.get("test:key-remove [GH-90000]"));
        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("remove returns false for non-existent key [GH-90000]")
    void removeNonExistent() { // GH-90000
        Boolean removed = runPromise(() -> cache.remove("test:ghost-" + System.nanoTime())); // GH-90000
        assertThat(removed).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("exists returns true after put [GH-90000]")
    void existsAfterPut() { // GH-90000
        runPromise(() -> cache.put("test:exists-key", "value")); // GH-90000
        Boolean exists = runPromise(() -> cache.exists("test:exists-key [GH-90000]"));
        assertThat(exists).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("exists returns false for missing key [GH-90000]")
    void existsMissing() { // GH-90000
        Boolean exists = runPromise(() -> cache.exists("test:missing-" + System.nanoTime())); // GH-90000
        assertThat(exists).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("putAll and getAll batch operations work correctly [GH-90000]")
    void putAllAndGetAll() { // GH-90000
        Map<String, String> batch = Map.of( // GH-90000
                "test:batch1", "alpha",
                "test:batch2", "beta",
                "test:batch3", "gamma"
        );
        runPromise(() -> cache.putAll(batch)); // GH-90000

        Map<String, String> results = runPromise(() -> // GH-90000
                cache.getAll(List.of("test:batch1", "test:batch2", "test:batch3"))); // GH-90000
        assertThat(results) // GH-90000
                .containsEntry("test:batch1", "alpha") // GH-90000
                .containsEntry("test:batch2", "beta") // GH-90000
                .containsEntry("test:batch3", "gamma"); // GH-90000
    }

    @Test
    @DisplayName("getAll returns empty map when all keys missing [GH-90000]")
    void getAllMissing() { // GH-90000
        Map<String, String> results = runPromise(() -> // GH-90000
                cache.getAll(List.of("test:absent1", "test:absent2"))); // GH-90000
        assertThat(results).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("clearWithPrefix removes all matching keys [GH-90000]")
    void clearWithPrefix() { // GH-90000
        runPromise(() -> cache.put("test:prefix:a", "1")); // GH-90000
        runPromise(() -> cache.put("test:prefix:b", "2")); // GH-90000
        runPromise(() -> cache.clearWithPrefix("test:prefix: [GH-90000]"));

        Optional<String> a = runPromise(() -> cache.get("test:prefix:a [GH-90000]"));
        Optional<String> b = runPromise(() -> cache.get("test:prefix:b [GH-90000]"));
        assertThat(a).isEmpty(); // GH-90000
        assertThat(b).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("countWithPrefix returns count of matching keys [GH-90000]")
    void countWithPrefix() { // GH-90000
        String pfx = "test:counted:" + System.nanoTime() + ":"; // GH-90000
        runPromise(() -> cache.put(pfx + "x", "1")); // GH-90000
        runPromise(() -> cache.put(pfx + "y", "2")); // GH-90000
        Long count = runPromise(() -> cache.countWithPrefix(pfx)); // GH-90000
        assertThat(count).isEqualTo(2L); // GH-90000
    }

    @Test
    @DisplayName("removeAll deletes multiple keys and returns count [GH-90000]")
    void removeAll() { // GH-90000
        runPromise(() -> cache.put("test:rm1", "a")); // GH-90000
        runPromise(() -> cache.put("test:rm2", "b")); // GH-90000
        Long removed = runPromise(() -> cache.removeAll(Set.of("test:rm1", "test:rm2"))); // GH-90000
        assertThat(removed).isEqualTo(2L); // GH-90000
    }

    @Test
    @DisplayName("snapshotStats reports hits and misses [GH-90000]")
    void snapshotStats() { // GH-90000
        runPromise(() -> cache.put("test:stats-key", "value")); // GH-90000
        runPromise(() -> cache.get("test:stats-key [GH-90000]"));       // hit
        runPromise(() -> cache.get("test:missing-stats [GH-90000]"));   // miss

        AsyncRedisCache.CacheStats stats = cache.snapshotStats(); // GH-90000
        assertThat(stats.hits()).isGreaterThanOrEqualTo(1L); // GH-90000
        assertThat(stats.misses()).isGreaterThanOrEqualTo(1L); // GH-90000
    }

    @Test
    @DisplayName("overwrite replaces existing value [GH-90000]")
    void overwrite() { // GH-90000
        runPromise(() -> cache.put("test:ow-key", "first")); // GH-90000
        runPromise(() -> cache.put("test:ow-key", "second")); // GH-90000
        Optional<String> result = runPromise(() -> cache.get("test:ow-key [GH-90000]"));
        assertThat(result).isPresent().hasValue("second [GH-90000]");
    }

    @Test
    @DisplayName("getAll with empty key list returns empty map [GH-90000]")
    void getAllWithEmptyKeys() { // GH-90000
        Map<String, String> results = runPromise(() -> cache.getAll(List.of())); // GH-90000
        assertThat(results).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("putAll with empty map is a no-op [GH-90000]")
    void putAllEmpty() { // GH-90000
        runPromise(() -> cache.putAll(Map.of())); // should not throw // GH-90000
    }
}
