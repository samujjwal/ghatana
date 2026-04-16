package com.ghatana.yappc.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.cache.backend.RedisDistributedCacheBackend;
import com.ghatana.platform.cache.DistributedCacheService;
import com.ghatana.platform.governance.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisEntityCacheAdapter with real Redis using Testcontainers.
 *
 * Tests Redis-backed caching with tenant isolation, TTL, and cache invalidation.
 */
@Testcontainers
@DisplayName("RedisEntityCacheAdapter Integration Tests")
class RedisEntityCacheAdapterIntegrationTest {

    @Container
    private static final GenericContainer<?> redisContainer = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static ObjectMapper objectMapper;
    private static DistributedCacheService distributedCacheService;

    private RedisEntityCacheAdapter<TestEntity> cacheAdapter;

    @BeforeAll
    static void setUpRedis() {
        objectMapper = new ObjectMapper();

        String redisHost = redisContainer.getHost();
        Integer redisPort = redisContainer.getFirstMappedPort();

        RedisDistributedCacheBackend backend = new RedisDistributedCacheBackend(
                redisHost,
                redisPort,
                null, // no password
                0,    // database 0
                5000, // timeout 5s
                3,    // max retries
                objectMapper
        );

        distributedCacheService = new DistributedCacheService(backend, "test-tenant");
    }

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenantId("test-tenant");
        cacheAdapter = new RedisEntityCacheAdapter<>(
                distributedCacheService,
                objectMapper,
                Duration.ofMinutes(5),
                "test-collection"
        );
    }

    @AfterEach
    void tearDown() {
        cacheAdapter.clear();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Should cache and retrieve entity")
    void shouldCacheAndRetrieveEntity() {
        TestEntity entity = new TestEntity("id1", "Test Entity");

        cacheAdapter.put("id1", entity);
        Optional<TestEntity> retrieved = cacheAdapter.get("id1");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().id()).isEqualTo("id1");
        assertThat(retrieved.get().name()).isEqualTo("Test Entity");
    }

    @Test
    @DisplayName("Should return empty for non-existent key")
    void shouldReturnEmptyForNonExistentKey() {
        Optional<TestEntity> retrieved = cacheAdapter.get("nonexistent");

        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Should update cached value")
    void shouldUpdateCachedValue() {
        TestEntity entity1 = new TestEntity("id1", "Entity 1");
        TestEntity entity2 = new TestEntity("id1", "Entity 2");

        cacheAdapter.put("id1", entity1);
        cacheAdapter.put("id1", entity2);
        Optional<TestEntity> retrieved = cacheAdapter.get("id1");

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().name()).isEqualTo("Entity 2");
    }

    @Test
    @DisplayName("Should invalidate cache entry")
    void shouldInvalidateCacheEntry() {
        TestEntity entity = new TestEntity("id1", "Test Entity");

        cacheAdapter.put("id1", entity);
        cacheAdapter.invalidate("id1");
        Optional<TestEntity> retrieved = cacheAdapter.get("id1");

        assertThat(retrieved).isEmpty();
    }

    @Test
    @DisplayName("Should clear all cache entries")
    void shouldClearAllCacheEntries() {
        cacheAdapter.put("id1", new TestEntity("id1", "Entity 1"));
        cacheAdapter.put("id2", new TestEntity("id2", "Entity 2"));
        cacheAdapter.put("id3", new TestEntity("id3", "Entity 3"));

        cacheAdapter.clear();

        assertThat(cacheAdapter.get("id1")).isEmpty();
        assertThat(cacheAdapter.get("id2")).isEmpty();
        assertThat(cacheAdapter.get("id3")).isEmpty();
    }

    @Test
    @DisplayName("Should respect TTL")
    void shouldRespectTtl() throws InterruptedException {
        TestEntity entity = new TestEntity("id1", "Test Entity");

        // Cache with 1 second TTL
        RedisEntityCacheAdapter<TestEntity> shortLivedCache = new RedisEntityCacheAdapter<>(
                distributedCacheService,
                objectMapper,
                Duration.ofSeconds(1),
                "test-collection"
        );

        shortLivedCache.put("id1", entity);
        Optional<TestEntity> retrieved = shortLivedCache.get("id1");
        assertThat(retrieved).isPresent();

        // Wait for TTL to expire
        Thread.sleep(1500);

        Optional<TestEntity> afterTtl = shortLivedCache.get("id1");
        assertThat(afterTtl).isEmpty();
    }

    @Test
    @DisplayName("Should return cache statistics")
    void shouldReturnCacheStatistics() {
        cacheAdapter.put("id1", new TestEntity("id1", "Entity 1"));
        cacheAdapter.put("id2", new TestEntity("id2", "Entity 2"));

        DistributedCacheService.CacheStatistics stats = cacheAdapter.getStatistics();

        assertThat(stats.totalKeys).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should isolate cache by tenant")
    void shouldIsolateCacheByTenant() {
        TestEntity tenant1Entity = new TestEntity("id1", "Tenant 1 Entity");

        // Cache as tenant1
        TenantContext.setCurrentTenantId("tenant1");
        RedisEntityCacheAdapter<TestEntity> tenant1Cache = new RedisEntityCacheAdapter<>(
                distributedCacheService,
                objectMapper,
                Duration.ofMinutes(5),
                "test-collection"
        );
        tenant1Cache.put("id1", tenant1Entity);

        // Try to retrieve as tenant2
        TenantContext.setCurrentTenantId("tenant2");
        RedisEntityCacheAdapter<TestEntity> tenant2Cache = new RedisEntityCacheAdapter<>(
                distributedCacheService,
                objectMapper,
                Duration.ofMinutes(5),
                "test-collection"
        );
        Optional<TestEntity> retrieved = tenant2Cache.get("id1");

        assertThat(retrieved).isEmpty();
    }

    record TestEntity(String id, String name) {}
}
