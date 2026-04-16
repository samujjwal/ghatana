package com.ghatana.yappc.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.platform.governance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisEntityCacheAdapter.
 *
 * Tests cache operations, tenant isolation, TTL, and error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisEntityCacheAdapter Tests")
class RedisEntityCacheAdapterTest {

    @Mock
    private DistributedCacheService distributedCacheService;

    @Mock
    private ObjectMapper objectMapper;

    private RedisEntityCacheAdapter<TestEntity> cacheAdapter;

    @BeforeEach
    void setUp() {
        lenient().when(TenantContext.getCurrentTenantId()).thenReturn("test-tenant");
        cacheAdapter = new RedisEntityCacheAdapter<>(
                distributedCacheService,
                objectMapper,
                Duration.ofMinutes(5),
                "test-collection"
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should return cached value when present")
    void shouldReturnCachedValueWhenPresent() {
        TestEntity entity = new TestEntity("id1", "Test Entity");
        when(distributedCacheService.get(anyString(), eq(Object.class)))
                .thenReturn(Optional.of(entity));

        Optional<TestEntity> result = cacheAdapter.get("id1");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("id1");
        verify(distributedCacheService).get(anyString(), eq(Object.class));
    }

    @Test
    @DisplayName("Should return empty when cache miss")
    void shouldReturnEmptyWhenCacheMiss() {
        when(distributedCacheService.get(anyString(), eq(Object.class)))
                .thenReturn(Optional.empty());

        Optional<TestEntity> result = cacheAdapter.get("id1");

        assertThat(result).isEmpty();
        verify(distributedCacheService).get(anyString(), eq(Object.class));
    }

    @Test
    @DisplayName("Should put value in cache with default TTL")
    void shouldPutValueInCacheWithDefaultTtl() {
        TestEntity entity = new TestEntity("id1", "Test Entity");

        cacheAdapter.put("id1", entity);

        verify(distributedCacheService).put(anyString(), eq(entity), eq(300L));
    }

    @Test
    @DisplayName("Should put value in cache with custom TTL")
    void shouldPutValueInCacheWithCustomTtl() {
        TestEntity entity = new TestEntity("id1", "Test Entity");
        Duration customTtl = Duration.ofMinutes(10);

        cacheAdapter.put("id1", entity, customTtl);

        verify(distributedCacheService).put(anyString(), eq(entity), eq(600L));
    }

    @Test
    @DisplayName("Should invalidate cache entry")
    void shouldInvalidateCacheEntry() {
        cacheAdapter.invalidate("id1");

        verify(distributedCacheService).invalidate(anyString());
    }

    @Test
    @DisplayName("Should clear all cache entries for collection")
    void shouldClearAllCacheEntriesForCollection() {
        cacheAdapter.clear();

        verify(distributedCacheService).invalidatePattern(anyString());
    }

    @Test
    @DisplayName("Should return cache statistics")
    void shouldReturnCacheStatistics() {
        DistributedCacheService.CacheStatistics stats = new DistributedCacheService.CacheStatistics(10, 1000);
        when(distributedCacheService.getStatistics(anyString())).thenReturn(stats);

        DistributedCacheService.CacheStatistics result = cacheAdapter.getStatistics();

        assertThat(result.totalKeys).isEqualTo(10);
        assertThat(result.totalSize).isEqualTo(1000);
        verify(distributedCacheService).getStatistics(anyString());
    }

    @Test
    @DisplayName("Should return empty on cache get error")
    void shouldReturnEmptyOnCacheGetError() {
        when(distributedCacheService.get(anyString(), eq(Object.class)))
                .thenThrow(new RuntimeException("Cache error"));

        Optional<TestEntity> result = cacheAdapter.get("id1");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should not throw on cache put error")
    void shouldNotThrowOnCachePutError() {
        TestEntity entity = new TestEntity("id1", "Test Entity");
        doThrow(new RuntimeException("Cache error")).when(distributedCacheService)
                .put(anyString(), any(), anyLong());

        cacheAdapter.put("id1", entity); // Should not throw
    }

    @Test
    @DisplayName("Should not throw on cache invalidate error")
    void shouldNotThrowOnCacheInvalidateError() {
        doThrow(new RuntimeException("Cache error")).when(distributedCacheService)
                .invalidate(anyString());

        cacheAdapter.invalidate("id1"); // Should not throw
    }

    @Test
    @DisplayName("Should throw when tenant context is missing")
    void shouldThrowWhenTenantContextIsMissing() {
        TenantContext.clear();

        assertThatThrownBy(() -> cacheAdapter.get("id1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant context required");
    }

    @Test
    @DisplayName("Should build tenant-scoped cache key")
    void shouldBuildTenantScopedCacheKey() {
        when(distributedCacheService.get(anyString(), eq(Object.class)))
                .thenReturn(Optional.empty());

        cacheAdapter.get("id1");

        verify(distributedCacheService).get(contains("test-tenant:test-collection:id1"), eq(Object.class));
    }

    record TestEntity(String id, String name) {}
}
