package com.ghatana.audio.video.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
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
 * @doc.purpose Redis integration tests for cache TTL, tenant isolation, and invalidation (AV-P1-02)
 * @doc.layer test
 * @doc.pattern IntegrationTest
 */
@Testcontainers
@DisplayName("Transcription Cache Integration Tests (AV-P1-02)")
class TranscriptionCacheIT {

    @Container
    static final RedisContainer REDIS = new RedisContainer(
            DockerImageName.parse("redis:7.2-alpine"));

    private JedisPool jedisPool;
    private ExecutorService ioExecutor;
    private TranscriptionCacheService cacheService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        String host = REDIS.getHost();
        int port = REDIS.getFirstMappedPort();

        jedisPool = new JedisPool(host, port);
        ioExecutor = Executors.newVirtualThreadPerTaskExecutor();

        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        RedisDistributedCacheAdapter<String, String> cachePort =
                new RedisDistributedCacheAdapter<>(
                        jedisPool,
                        objectMapper,
                        String.class,
                        ioExecutor,
                        "transcription",
                        Duration.ofMinutes(30));

        cacheService = new TranscriptionCacheService(cachePort, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (jedisPool != null) jedisPool.close();
        if (ioExecutor != null) ioExecutor.shutdown();
    }

    @Test
    @DisplayName("Should store and retrieve transcription within TTL")
    void shouldStoreAndRetrieveTranscription() {
        String tenantId = "tenant-cache-1";
        TranscriptionEntity entity = buildEntity(tenantId);

        cacheService.cacheTranscription(tenantId, entity).getResult();

        Optional<TranscriptionEntity> retrieved =
                cacheService.getTranscription(tenantId, entity.getId()).getResult();

        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getId()).isEqualTo(entity.getId());
        assertThat(retrieved.get().getText()).isEqualTo(entity.getText());
    }

    @Test
    @DisplayName("Should return empty for cache miss")
    void shouldReturnEmptyForCacheMiss() {
        Optional<TranscriptionEntity> result =
                cacheService.getTranscription("tenant-miss", UUID.randomUUID()).getResult();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should isolate keys between tenants (tenant isolation)")
    void shouldIsolateCacheKeysBetweenTenants() {
        String tenant1 = "tenant-iso-1";
        String tenant2 = "tenant-iso-2";
        UUID sharedId = UUID.randomUUID();

        // Store the same ID under two different tenants with different text
        TranscriptionEntity entity1 = buildEntityWithIdAndText(tenant1, sharedId, "Transcript for tenant-1");
        TranscriptionEntity entity2 = buildEntityWithIdAndText(tenant2, sharedId, "Transcript for tenant-2");

        cacheService.cacheTranscription(tenant1, entity1).getResult();
        cacheService.cacheTranscription(tenant2, entity2).getResult();

        Optional<TranscriptionEntity> t1Result = cacheService.getTranscription(tenant1, sharedId).getResult();
        Optional<TranscriptionEntity> t2Result = cacheService.getTranscription(tenant2, sharedId).getResult();

        assertThat(t1Result).isPresent();
        assertThat(t2Result).isPresent();
        assertThat(t1Result.get().getText()).isEqualTo("Transcript for tenant-1");
        assertThat(t2Result.get().getText()).isEqualTo("Transcript for tenant-2");
    }

    @Test
    @DisplayName("Should evict entry after short TTL")
    void shouldEvictAfterTtl() throws InterruptedException {
        String tenantId = "tenant-ttl";
        TranscriptionEntity entity = buildEntity(tenantId);

        // Cache with a 1-second TTL
        cacheService.cacheTranscription(tenantId, entity, Duration.ofSeconds(1)).getResult();

        // Present before TTL
        Optional<TranscriptionEntity> before = cacheService.getTranscription(tenantId, entity.getId()).getResult();
        assertThat(before).isPresent();

        // Wait for TTL to expire
        Thread.sleep(1500);

        Optional<TranscriptionEntity> after = cacheService.getTranscription(tenantId, entity.getId()).getResult();
        assertThat(after).isEmpty();
    }

    @Test
    @DisplayName("Should remove entry on explicit invalidation")
    void shouldRemoveEntryOnInvalidation() {
        String tenantId = "tenant-inval";
        TranscriptionEntity entity = buildEntity(tenantId);

        cacheService.cacheTranscription(tenantId, entity).getResult();
        assertThat(cacheService.getTranscription(tenantId, entity.getId()).getResult()).isPresent();

        cacheService.invalidateTranscription(tenantId, entity.getId()).getResult();

        Optional<TranscriptionEntity> after = cacheService.getTranscription(tenantId, entity.getId()).getResult();
        assertThat(after).isEmpty();
    }

    @Test
    @DisplayName("Should load and cache from loader on cache miss (getOrLoad)")
    void shouldLoadAndCacheOnMiss() {
        String tenantId = "tenant-load";
        UUID transcriptionId = UUID.randomUUID();
        TranscriptionEntity expected = buildEntityWithIdAndText(tenantId, transcriptionId, "Loaded from source");

        TranscriptionEntity result = cacheService.getOrLoadTranscription(
                tenantId, transcriptionId,
                id -> Promise.of(Optional.of(expected))
        ).getResult();

        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("Loaded from source");

        // Second call should hit cache (no loader call needed)
        TranscriptionEntity cachedResult = cacheService.getOrLoadTranscription(
                tenantId, transcriptionId,
                id -> Promise.ofException(new RuntimeException("loader should not be called"))
        ).getResult();

        assertThat(cachedResult).isNotNull();
        assertThat(cachedResult.getId()).isEqualTo(transcriptionId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private TranscriptionEntity buildEntity(String tenantId) {
        return buildEntityWithIdAndText(tenantId, UUID.randomUUID(), "Test transcription text");
    }

    private TranscriptionEntity buildEntityWithIdAndText(String tenantId, UUID id, String text) {
        TranscriptionEntity entity = new TranscriptionEntity(
                id, tenantId, UUID.randomUUID(), UUID.randomUUID(), text, "en");
        entity.setConfidence(0.95f);
        return entity;
    }
}



