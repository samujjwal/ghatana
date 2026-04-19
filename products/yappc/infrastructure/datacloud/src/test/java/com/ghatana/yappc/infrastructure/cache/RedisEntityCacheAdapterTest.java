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
    void setUp() {
        cacheAdapter = new RedisEntityCacheAdapter<>(
                new Object(),
                new ObjectMapper(),
                Duration.ofMinutes(5),
                "test-collection"
        );
    }

    @Test
    @DisplayName("Should always return empty while cache stub is disabled")
    void shouldReturnEmptyWhenCacheMiss() {
        assertThat(cacheAdapter.get("id1")).isEmpty();
    }

    @Test
    @DisplayName("Should ignore put with default TTL without throwing")
    void shouldPutValueInCacheWithDefaultTtl() {
        TestEntity entity = new TestEntity("id1", "Test Entity");

        assertThatCode(() -> cacheAdapter.put("id1", entity)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should ignore put with custom TTL without throwing")
    void shouldPutValueInCacheWithCustomTtl() {
        TestEntity entity = new TestEntity("id1", "Test Entity");
        Duration customTtl = Duration.ofMinutes(10);

        assertThatCode(() -> cacheAdapter.put("id1", entity, customTtl)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should ignore invalidation without throwing")
    void shouldInvalidateCacheEntry() {
        assertThatCode(() -> cacheAdapter.invalidate("id1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should ignore clear without throwing")
    void shouldClearAllCacheEntriesForCollection() {
        assertThatCode(cacheAdapter::clear).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should return zero statistics for cache stub")
    void shouldReturnCacheStatistics() {
        RedisEntityCacheAdapter.CacheStatistics result = cacheAdapter.getStatistics();

        assertThat(result.keyCount()).isZero();
        assertThat(result.sizeBytes()).isZero();
    }

    @Test
    @DisplayName("Should ignore repeated cache operations")
    void shouldNotThrowOnRepeatedOperations() {
        TestEntity entity = new TestEntity("id1", "Test Entity");
        assertThatCode(() -> {
            cacheAdapter.put("id1", entity);
            cacheAdapter.put("id1", entity, Duration.ofSeconds(30));
            cacheAdapter.invalidate("id1");
            cacheAdapter.clear();
        }).doesNotThrowAnyException();
    }

    record TestEntity(String id, String name) {}
}
