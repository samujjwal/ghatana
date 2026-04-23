package com.ghatana.yappc.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for RedisEntityCacheAdapter.
 *
 * Verifies the current no-op cache adapter contract while platform cache wiring is disabled.
 */
@DisplayName("RedisEntityCacheAdapter Tests")
class RedisEntityCacheAdapterTest {

    private RedisEntityCacheAdapter<TestEntity> cacheAdapter;

    @BeforeEach
    void setUp() { // GH-90000
        cacheAdapter = new RedisEntityCacheAdapter<>( // GH-90000
                new Object(), // GH-90000
                new ObjectMapper(), // GH-90000
                Duration.ofMinutes(5), // GH-90000
                "test-collection"
        );
    }

    @Test
    @DisplayName("Should always return empty while cache stub is disabled")
    void shouldReturnEmptyWhenCacheMiss() { // GH-90000
        assertThat(cacheAdapter.get("id1")).isEmpty();
    }

    @Test
    @DisplayName("Should ignore put with default TTL without throwing")
    void shouldPutValueInCacheWithDefaultTtl() { // GH-90000
        TestEntity entity = new TestEntity("id1", "Test Entity"); // GH-90000

        assertThatCode(() -> cacheAdapter.put("id1", entity)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Should ignore put with custom TTL without throwing")
    void shouldPutValueInCacheWithCustomTtl() { // GH-90000
        TestEntity entity = new TestEntity("id1", "Test Entity"); // GH-90000
        Duration customTtl = Duration.ofMinutes(10); // GH-90000

        assertThatCode(() -> cacheAdapter.put("id1", entity, customTtl)).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Should ignore invalidation without throwing")
    void shouldInvalidateCacheEntry() { // GH-90000
        assertThatCode(() -> cacheAdapter.invalidate("id1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should ignore clear without throwing")
    void shouldClearAllCacheEntriesForCollection() { // GH-90000
        assertThatCode(cacheAdapter::clear).doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("Should return zero statistics for cache stub")
    void shouldReturnCacheStatistics() { // GH-90000
        RedisEntityCacheAdapter.CacheStatistics result = cacheAdapter.getStatistics(); // GH-90000

        assertThat(result.keyCount()).isZero(); // GH-90000
        assertThat(result.sizeBytes()).isZero(); // GH-90000
    }

    @Test
    @DisplayName("Should ignore repeated cache operations")
    void shouldNotThrowOnRepeatedOperations() { // GH-90000
        TestEntity entity = new TestEntity("id1", "Test Entity"); // GH-90000
        assertThatCode(() -> { // GH-90000
            cacheAdapter.put("id1", entity); // GH-90000
            cacheAdapter.put("id1", entity, Duration.ofSeconds(30)); // GH-90000
            cacheAdapter.invalidate("id1");
            cacheAdapter.clear(); // GH-90000
        }).doesNotThrowAnyException(); // GH-90000
    }

    record TestEntity(String id, String name) {} // GH-90000
}
