package com.ghatana.orchestrator.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.ghatana.orchestrator.models.OrchestratorPipelineEntity;
import com.ghatana.platform.observability.MetricsCollector;

import io.activej.promise.Promise;

/**
 * Pipeline cache with TTL and metrics support.
 * 
 * Day 24 Implementation: Simple pipeline caching with ActiveJ Promise
 * integration
 */
public class PipelineCache {

    private final Duration ttl;
    private final MetricsCollector metrics;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final AtomicInteger size = new AtomicInteger(0);

    public PipelineCache(Duration ttl, MetricsCollector metrics) {
        this.ttl = ttl;
        this.metrics = metrics;
    }

    /**
     * Get pipeline by ID.
     */
    public Promise<Optional<OrchestratorPipelineEntity>> get(String pipelineId) {
        CacheEntry entry = cache.get(pipelineId);
        if (entry == null || entry.isExpired()) {
            return Promise.of(Optional.empty());
        }
        return Promise.of(Optional.of(entry.pipeline));
    }

    /**
     * Put single pipeline.
     */
    public Promise<Void> put(String pipelineId, OrchestratorPipelineEntity pipeline) {
        cache.put(pipelineId, new CacheEntry(pipeline, Instant.now().plus(ttl)));
        size.set(cache.size());
        return Promise.of(null);
    }

    /**
     * Put multiple pipelines.
     */
    public Promise<Void> putAll(List<OrchestratorPipelineEntity> pipelines) {
        Instant expiryTime = Instant.now().plus(ttl);
        for (OrchestratorPipelineEntity pipeline : pipelines) {
            cache.put(pipeline.id, new CacheEntry(pipeline, expiryTime));
        }
        size.set(cache.size());
        return Promise.of(null);
    }

    /**
     * Get all cached pipelines.
     */
    public Promise<List<OrchestratorPipelineEntity>> getAllPipelines() {
        List<OrchestratorPipelineEntity> pipelines = cache.values().stream()
                .filter(entry -> !entry.isExpired())
                .map(entry -> entry.pipeline)
                .collect(Collectors.toList());
        return Promise.of(pipelines);
    }

    /**
     * Remove a pipeline from the cache.
     *
     * @param pipelineId The pipeline ID to remove
     * @return true if the pipeline was removed, false if it wasn't in the cache
     *
     * @doc.type method
     * @doc.purpose Remove a pipeline from the cache
     * @doc.layer core
     */
    public Promise<Boolean> remove(String pipelineId) {
        CacheEntry removed = cache.remove(pipelineId);
        if (removed != null) {
            size.set(cache.size());
            return Promise.of(true);
        }
        return Promise.of(false);
    }

    /**
     * Clear all cached pipelines.
     */
    public Promise<Void> clear() {
        cache.clear();
        size.set(0);
        return Promise.of(null);
    }

    /**
     * Get cache size.
     */
    public int size() {
        return size.get();
    }

    /**
     * Cache entry with expiry.
     */
    private static class CacheEntry {
        final OrchestratorPipelineEntity pipeline;
        final Instant expiryTime;

        CacheEntry(OrchestratorPipelineEntity pipeline, Instant expiryTime) {
            this.pipeline = pipeline;
            this.expiryTime = expiryTime;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiryTime);
        }
    }
}