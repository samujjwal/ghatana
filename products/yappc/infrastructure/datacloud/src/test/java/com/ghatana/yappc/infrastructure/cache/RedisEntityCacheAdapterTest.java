package com.ghatana.yappc.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisEntityCacheAdapter}.
 *
 * Verifies that the adapter correctly delegates to {@link DistributedCacheService} with
 * collection-scoped keys, default/explicit TTLs, and pattern-based cache clearing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisEntityCacheAdapter Tests")
class RedisEntityCacheAdapterTest {

    private static final String COLLECTION = "test-collection";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    @Mock
    private DistributedCacheService cacheService;

    private RedisEntityCacheAdapter<TestEntity> cacheAdapter;
    private RedisEntityCacheAdapter<TestEntity> typedAdapter;

    @BeforeEach
    void setUp() {
        cacheAdapter = new RedisEntityCacheAdapter<>(
                cacheService,
                new ObjectMapper(),
                DEFAULT_TTL,
                COLLECTION
        );
        typedAdapter = new RedisEntityCacheAdapter<>(
                cacheService,
                new ObjectMapper(),
                DEFAULT_TTL,
                COLLECTION,
                TestEntity.class
        );
    }

    @Test
    @DisplayName("Should return empty when service returns empty (cache miss)")
    void shouldReturnEmptyOnCacheMiss() {
        when(cacheService.get(eq(COLLECTION + ":id1"), eq(TestEntity.class)))
                .thenReturn(Optional.empty());

        Optional<TestEntity> result = cacheAdapter.get("id1", TestEntity.class);

        assertThat(result).isEmpty();
        verify(cacheService).get(COLLECTION + ":id1", TestEntity.class);
    }

    @Test
    @DisplayName("Should return entity when service returns value (cache hit)")
    void shouldReturnEntityOnCacheHit() {
        TestEntity entity = new TestEntity("id1", "Test Entity");
        when(cacheService.get(eq(COLLECTION + ":id1"), eq(TestEntity.class)))
                .thenReturn(Optional.of(entity));

        Optional<TestEntity> result = cacheAdapter.get("id1", TestEntity.class);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("id1");
        assertThat(result.get().name()).isEqualTo("Test Entity");
    }

    @Test
    @DisplayName("Should delegate get to service using entity class from typed constructor")
    void shouldDelegateGetWithTypedConstructor() {
        TestEntity entity = new TestEntity("id2", "Typed Entity");
        when(cacheService.get(eq(COLLECTION + ":id2"), eq(TestEntity.class)))
                .thenReturn(Optional.of(entity));

        Optional<TestEntity> result = typedAdapter.get("id2");

        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Typed Entity");
    }

    @Test
    @DisplayName("Should throw when get() called without entity class set")
    void shouldThrowWhenGetCalledWithoutEntityClass() {
        assertThatThrownBy(() -> cacheAdapter.get("id1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Entity class not set");
    }

    @Test
    @DisplayName("Should delegate put to service with scoped key and default TTL")
    void shouldPutWithDefaultTtl() {
        TestEntity entity = new TestEntity("id1", "Entity");

        cacheAdapter.put("id1", entity);

        verify(cacheService).put(COLLECTION + ":id1", entity, DEFAULT_TTL.getSeconds());
    }

    @Test
    @DisplayName("Should delegate put to service with scoped key and explicit TTL")
    void shouldPutWithExplicitTtl() {
        TestEntity entity = new TestEntity("id1", "Entity");
        Duration customTtl = Duration.ofSeconds(30);

        cacheAdapter.put("id1", entity, customTtl);

        verify(cacheService).put(COLLECTION + ":id1", entity, 30L);
    }

    @Test
    @DisplayName("Should delegate invalidate to service with scoped key")
    void shouldInvalidateScopedKey() {
        cacheAdapter.invalidate("id1");

        verify(cacheService).invalidate(COLLECTION + ":id1");
    }

    @Test
    @DisplayName("Should delegate clear to service using collection pattern")
    void shouldClearUsingCollectionPattern() {
        cacheAdapter.clear();

        verify(cacheService).invalidatePattern(COLLECTION + ":*");
    }

    @Test
    @DisplayName("Should return statistics from service")
    void shouldReturnCacheStatisticsFromService() {
        DistributedCacheService.CacheStatistics serviceStats =
                new DistributedCacheService.CacheStatistics(3L, 1024L);
        when(cacheService.getStatistics(COLLECTION + ":*")).thenReturn(serviceStats);

        RedisEntityCacheAdapter.CacheStatistics result = cacheAdapter.getStatistics();

        assertThat(result.keyCount()).isEqualTo(3L);
        assertThat(result.sizeBytes()).isEqualTo(1024L);
    }

    record TestEntity(String id, String name) {}
}
