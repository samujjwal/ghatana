package com.ghatana.audio.video.infrastructure.cache;

import com.ghatana.audio.video.infrastructure.persistence.entity.TranscriptionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * @doc.type class
 * @doc.purpose Cache service for transcription results.
 *              Provides tenant-scoped caching with Redis backend.
 * @doc.layer infrastructure
 * @doc.pattern Cache Service
 */
public class TranscriptionCacheService {

    private static final Logger LOG = LoggerFactory.getLogger(TranscriptionCacheService.class);

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final Duration LONG_TTL = Duration.ofHours(2);

    private final AudioVideoCache<String, String> cache;
    private final ObjectMapper objectMapper;

    public TranscriptionCacheService(
            com.ghatana.platform.cache.DistributedCachePort<String, String> cachePort,
            ObjectMapper objectMapper) {
        this.cache = new AudioVideoCache<>(cachePort, "transcription");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Get cached transcription by ID.
     */
    public Promise<Optional<TranscriptionEntity>> getTranscription(String tenantId, UUID transcriptionId) {
        String key = buildKey(tenantId, transcriptionId.toString());

        return cache.get(key)
            .then(optJson -> {
                if (optJson.isPresent()) {
                    try {
                        TranscriptionEntity entity = objectMapper.readValue(optJson.get(), TranscriptionEntity.class);
                        LOG.debug("Cache hit for transcription: tenantId={}, id={}", tenantId, transcriptionId);
                        return Promise.of(Optional.of(entity));
                    } catch (Exception e) {
                        LOG.error("Failed to deserialize cached transcription: {}", transcriptionId, e);
                        return Promise.of(Optional.<TranscriptionEntity>empty());
                    }
                }
                LOG.debug("Cache miss for transcription: tenantId={}, id={}", tenantId, transcriptionId);
                return Promise.of(Optional.<TranscriptionEntity>empty());
            });
    }

    /**
     * Cache a transcription result.
     */
    public Promise<Void> cacheTranscription(String tenantId, TranscriptionEntity entity) {
        String key = buildKey(tenantId, entity.getId().toString());

        try {
            String json = objectMapper.writeValueAsString(entity);
            return cache.put(key, json, DEFAULT_TTL)
                .whenResult(__ -> LOG.debug("Cached transcription: tenantId={}, id={}",
                    tenantId, entity.getId()));
        } catch (Exception e) {
            LOG.error("Failed to serialize transcription for caching: {}", entity.getId(), e);
            return Promise.complete();
        }
    }

    /**
     * Cache transcription with custom TTL.
     */
    public Promise<Void> cacheTranscription(String tenantId, TranscriptionEntity entity, Duration ttl) {
        String key = buildKey(tenantId, entity.getId().toString());

        try {
            String json = objectMapper.writeValueAsString(entity);
            return cache.put(key, json, ttl)
                .whenResult(__ -> LOG.debug("Cached transcription with TTL {}s: tenantId={}, id={}",
                    ttl.getSeconds(), tenantId, entity.getId()));
        } catch (Exception e) {
            LOG.error("Failed to serialize transcription for caching: {}", entity.getId(), e);
            return Promise.complete();
        }
    }

    /**
     * Get or load transcription using provided loader.
     */
    public Promise<TranscriptionEntity> getOrLoadTranscription(
            String tenantId,
            UUID transcriptionId,
            Function<UUID, Promise<Optional<TranscriptionEntity>>> loader) {

        String key = buildKey(tenantId, transcriptionId.toString());

        return cache.getOrLoad(key, k -> {
            LOG.debug("Loading transcription from source: tenantId={}, id={}", tenantId, transcriptionId);
            return loader.apply(transcriptionId)
                .then(optEntity -> {
                    if (optEntity.isPresent()) {
                        try {
                            String json = objectMapper.writeValueAsString(optEntity.get());
                            return Promise.of(json);
                        } catch (Exception e) {
                            LOG.error("Failed to serialize loaded transcription: {}", transcriptionId, e);
                            return Promise.of("");
                        }
                    }
                    return Promise.of("");
                });
        }).then(json -> {
            if (json.isEmpty()) {
                return Promise.of((TranscriptionEntity) null);
            }
            try {
                return Promise.of(objectMapper.readValue(json, TranscriptionEntity.class));
            } catch (Exception e) {
                LOG.error("Failed to deserialize loaded transcription: {}", transcriptionId, e);
                return Promise.of((TranscriptionEntity) null);
            }
        });
    }

    /**
     * Invalidate cached transcription.
     */
    public Promise<Void> invalidateTranscription(String tenantId, UUID transcriptionId) {
        String key = buildKey(tenantId, transcriptionId.toString());
        return cache.invalidate(key)
            .whenResult(__ -> LOG.debug("Invalidated transcription cache: tenantId={}, id={}",
                tenantId, transcriptionId));
    }

    /**
     * Invalidate all transcriptions for a tenant.
     */
    public Promise<Void> invalidateAllForTenant(String tenantId) {
        // Note: This would need pattern-based invalidation support in the cache
        LOG.debug("Invalidate all transcriptions for tenant: {}", tenantId);
        return cache.invalidateAll();
    }

    /**
     * Cache transcription by audio file ID (for lookup optimization).
     */
    public Promise<Void> cacheTranscriptionByAudioFileId(
            String tenantId,
            UUID audioFileId,
            TranscriptionEntity entity) {

        String key = buildKey(tenantId, "audio:" + audioFileId.toString());

        try {
            String json = objectMapper.writeValueAsString(entity);
            return cache.put(key, json, DEFAULT_TTL)
                .whenResult(__ -> LOG.debug("Cached transcription by audio file: tenantId={}, audioId={}",
                    tenantId, audioFileId));
        } catch (Exception e) {
            LOG.error("Failed to cache transcription by audio file: {}", audioFileId, e);
            return Promise.complete();
        }
    }

    /**
     * Get transcription by audio file ID.
     */
    public Promise<Optional<TranscriptionEntity>> getTranscriptionByAudioFileId(
            String tenantId,
            UUID audioFileId) {

        String key = buildKey(tenantId, "audio:" + audioFileId.toString());

        return cache.get(key)
            .then(optJson -> {
                if (optJson.isPresent()) {
                    try {
                        TranscriptionEntity entity = objectMapper.readValue(optJson.get(), TranscriptionEntity.class);
                        return Promise.of(Optional.of(entity));
                    } catch (Exception e) {
                        LOG.error("Failed to deserialize cached transcription: {}", audioFileId, e);
                        return Promise.of(Optional.<TranscriptionEntity>empty());
                    }
                }
                return Promise.of(Optional.<TranscriptionEntity>empty());
            });
    }

    private String buildKey(String tenantId, String id) {
        return cache.buildKey(tenantId, id);
    }
}
