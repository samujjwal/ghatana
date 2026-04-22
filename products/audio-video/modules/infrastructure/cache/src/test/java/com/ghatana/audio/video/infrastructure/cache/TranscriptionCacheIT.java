package com.ghatana.audio.video.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.cache.RedisDistributedCacheAdapter;
import com.redis.testcontainers.RedisContainer;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TranscriptionCacheService against a real Redis instance.
 *
 * <p>Covers:
 * <ul>
 *   <li>TTL expiry: cached entries are evicted after TTL</li>
 *   <li>Tenant isolation: keys in different tenant namespaces do not collide</li>
 *   <li>Delete-triggered invalidation: invalidate removes the entry</li>
 *   <li>Cache hit-rate: repeated get after put returns the stored value</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Redis integration tests for cache TTL, tenant isolation, and invalidation (AV-P1-02) // GH-90000
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@Testcontainers
@DisplayName("Transcription Cache Integration Tests (AV-P1-02) [GH-90000]")
class TranscriptionCacheIT extends EventloopTestBase {

    @Container
    static final RedisContainer REDIS = new RedisContainer( // GH-90000
            DockerImageName.parse("redis:7.2-alpine [GH-90000]"));

    private JedisPool jedisPool;
    private ExecutorService ioExecutor;
    private TranscriptionCacheService cacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() { // GH-90000
        String host = REDIS.getHost(); // GH-90000
        int port = REDIS.getFirstMappedPort(); // GH-90000

        jedisPool = new JedisPool(host, port); // GH-90000
        ioExecutor = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()); // GH-90000

        RedisDistributedCacheAdapter<String, String> cachePort =
                new RedisDistributedCacheAdapter<>( // GH-90000
                        jedisPool,
                        objectMapper,
                        String.class,
                        ioExecutor,
                        "transcription",
                        Duration.ofMinutes(30)); // GH-90000

        cacheService = new TranscriptionCacheService(cachePort, objectMapper); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (jedisPool != null) jedisPool.close(); // GH-90000
        if (ioExecutor != null) ioExecutor.shutdown(); // GH-90000
    }

    @Test
    @DisplayName("Should store and retrieve transcription within TTL [GH-90000]")
    void shouldStoreAndRetrieveTranscription() { // GH-90000
        String tenantId = "tenant-cache-1";
        TranscriptionEntity entity = buildEntity(tenantId); // GH-90000

        runPromise(() -> cacheService.cacheTranscription(tenantId, entity)); // GH-90000

        Optional<TranscriptionEntity> retrieved =
                runPromise(() -> cacheService.getTranscription(tenantId, entity.getId())); // GH-90000

        assertThat(retrieved).isPresent(); // GH-90000
        assertThat(retrieved.get().getId()).isEqualTo(entity.getId()); // GH-90000
        assertThat(retrieved.get().getText()).isEqualTo(entity.getText()); // GH-90000
    }

    @Test
    @DisplayName("Should return empty for cache miss [GH-90000]")
    void shouldReturnEmptyForCacheMiss() { // GH-90000
        Optional<TranscriptionEntity> result =
                runPromise(() -> cacheService.getTranscription("tenant-miss", UUID.randomUUID())); // GH-90000

        assertThat(result).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should isolate keys between tenants (tenant isolation) [GH-90000]")
    void shouldIsolateCacheKeysBetweenTenants() { // GH-90000
        String tenant1 = "tenant-iso-1";
        String tenant2 = "tenant-iso-2";
        UUID sharedId = UUID.randomUUID(); // GH-90000

        // Store the same ID under two different tenants with different text
        TranscriptionEntity entity1 = buildEntityWithIdAndText(tenant1, sharedId, "Transcript for tenant-1"); // GH-90000
        TranscriptionEntity entity2 = buildEntityWithIdAndText(tenant2, sharedId, "Transcript for tenant-2"); // GH-90000

        runPromise(() -> cacheService.cacheTranscription(tenant1, entity1)); // GH-90000
        runPromise(() -> cacheService.cacheTranscription(tenant2, entity2)); // GH-90000

        Optional<TranscriptionEntity> t1Result = runPromise(() -> cacheService.getTranscription(tenant1, sharedId)); // GH-90000
        Optional<TranscriptionEntity> t2Result = runPromise(() -> cacheService.getTranscription(tenant2, sharedId)); // GH-90000

        assertThat(t1Result).isPresent(); // GH-90000
        assertThat(t2Result).isPresent(); // GH-90000
        assertThat(t1Result.get().getText()).isEqualTo("Transcript for tenant-1 [GH-90000]");
        assertThat(t2Result.get().getText()).isEqualTo("Transcript for tenant-2 [GH-90000]");
    }

    @Test
    @DisplayName("Should evict entry after short TTL [GH-90000]")
    void shouldEvictAfterTtl() throws InterruptedException { // GH-90000
        String tenantId = "tenant-ttl";
        TranscriptionEntity entity = buildEntity(tenantId); // GH-90000

        // Cache with a 1-second TTL
        runPromise(() -> cacheService.cacheTranscription(tenantId, entity, Duration.ofSeconds(1))); // GH-90000

        // Present before TTL
        Optional<TranscriptionEntity> before = runPromise(() -> cacheService.getTranscription(tenantId, entity.getId())); // GH-90000
        assertThat(before).isPresent(); // GH-90000

        // Wait for TTL to expire
        Thread.sleep(1500); // GH-90000

        Optional<TranscriptionEntity> after = runPromise(() -> cacheService.getTranscription(tenantId, entity.getId())); // GH-90000
        assertThat(after).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should remove entry on explicit invalidation [GH-90000]")
    void shouldRemoveEntryOnInvalidation() { // GH-90000
        String tenantId = "tenant-inval";
        TranscriptionEntity entity = buildEntity(tenantId); // GH-90000

        runPromise(() -> cacheService.cacheTranscription(tenantId, entity)); // GH-90000
        assertThat(runPromise(() -> cacheService.getTranscription(tenantId, entity.getId()))).isPresent(); // GH-90000

        runPromise(() -> cacheService.invalidateTranscription(tenantId, entity.getId())); // GH-90000

        Optional<TranscriptionEntity> after = runPromise(() -> cacheService.getTranscription(tenantId, entity.getId())); // GH-90000
        assertThat(after).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should load and cache from loader on cache miss (getOrLoad) [GH-90000]")
    void shouldLoadAndCacheOnMiss() { // GH-90000
        String tenantId = "tenant-load";
        UUID transcriptionId = UUID.randomUUID(); // GH-90000
        TranscriptionEntity expected = buildEntityWithIdAndText(tenantId, transcriptionId, "Loaded from source"); // GH-90000

        TranscriptionEntity result = runPromise(() -> cacheService.getOrLoadTranscription( // GH-90000
                tenantId, transcriptionId,
                id -> Promise.of(Optional.of(expected)) // GH-90000
        ));

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.getText()).isEqualTo("Loaded from source [GH-90000]");

        // Second call should hit cache (no loader call needed) // GH-90000
        TranscriptionEntity cachedResult = runPromise(() -> cacheService.getOrLoadTranscription( // GH-90000
                tenantId, transcriptionId,
                id -> Promise.ofException(new RuntimeException("loader should not be called [GH-90000]"))
        ));

        assertThat(cachedResult).isNotNull(); // GH-90000
        assertThat(cachedResult.getId()).isEqualTo(transcriptionId); // GH-90000
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TranscriptionEntity buildEntity(String tenantId) { // GH-90000
        return buildEntityWithIdAndText(tenantId, UUID.randomUUID(), "Test transcription text"); // GH-90000
    }

    private TranscriptionEntity buildEntityWithIdAndText(String tenantId, UUID id, String text) { // GH-90000
        TranscriptionEntity entity = new TranscriptionEntity( // GH-90000
                id, tenantId, UUID.randomUUID(), UUID.randomUUID(), text, "en"); // GH-90000
        entity.setConfidence(0.95f); // GH-90000
        return entity;
    }
}



