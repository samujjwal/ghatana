/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.ai.platform;

import com.ghatana.aiplatform.featurestore.Feature;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * YAPPC-specific ActiveJ async façade over the platform {@link FeatureStoreService}.
 *
 * <p>Provides non-blocking access to the two-tier feature store (Redis hot path +
 * PostgreSQL cold path) for use in YAPPC's event-loop-based async pipeline.
 * All blocking JDBC/Redis calls are dispatched to the configured {@link Executor}
 * via {@link Promise#ofBlocking}.
 *
 * <h3>YAPPC use cases</h3>
 * <ul>
 *   <li>Agent feature ingestion — write agent action embeddings, complexity
 *       scores, and progress indicators as features per workspace entity.</li>
 *   <li>LLM prompt enrichment — retrieve historical feature vectors
 *       ({@code getFeatures}) before calling the LLM gateway so the prompt
 *       contains relevant context signals.</li>
 *   <li>Requirement analysis — store and retrieve semantic similarity scores
 *       computed during AI requirement generation.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * featureStoreClient
 *     .ingest(tenantId, workspaceId, "complexity_score", 0.72)
 *     .then(ignored -> featureStoreClient.getFeatures(tenantId, workspaceId,
 *             List.of("complexity_score", "sprint_velocity_90d")))
 *     .whenResult(feats -> log.info("Features: {}", feats));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ async façade over FeatureStoreService for YAPPC agents and services
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class YappcFeatureStoreClient {

    private static final Logger log = LoggerFactory.getLogger(YappcFeatureStoreClient.class);

    private final FeatureStoreService delegate;
    private final Executor blockingExecutor;

    /**
     * Creates the client.
     *
     * @param delegate         platform feature store service (two-tier: Redis + PostgreSQL)
     * @param blockingExecutor executor for blocking JDBC/Redis calls; must not be
     *                         the ActiveJ event-loop thread
     */
    public YappcFeatureStoreClient(FeatureStoreService delegate, Executor blockingExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    // =========================================================================
    // Ingestion
    // =========================================================================

    /**
     * Ingests a numeric feature for the given workspace entity.
     *
     * <p>Writes to both the Redis hot-tier and PostgreSQL cold-tier.
     *
     * @param tenantId    tenant identifier (multi-tenancy boundary)
     * @param entityId    entity identifier — typically a workspace ID, requirement ID,
     *                    or agent run ID
     * @param featureName symbolic feature name (e.g. {@code "complexity_score"})
     * @param value       feature value
     * @return promise completing when both storage tiers have acknowledged the write
     */
    public Promise<Void> ingest(String tenantId, String entityId, String featureName, double value) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(featureName, "featureName");
        Feature feature = Feature.builder()
                .name(featureName)
                .entityId(entityId)
                .value(value)
                .timestamp(Instant.now())
                .build();
        return ingest(tenantId, feature);
    }

    /**
     * Ingests a pre-built {@link Feature} for the given tenant.
     *
     * @param tenantId tenant identifier
     * @param feature  feature value object
     * @return promise completing when both storage tiers have acknowledged the write
     */
    public Promise<Void> ingest(String tenantId, Feature feature) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(feature, "feature");
        return Promise.ofBlocking(blockingExecutor, () -> {
            delegate.ingest(tenantId, feature);
            log.debug("[FeatureStore] Ingested '{}' for entity '{}' tenant='{}'",
                    feature.getName(), feature.getEntityId(), tenantId);
            return null;
        });
    }

    /**
     * Batch-ingests a list of features, tolerating partial failures.
     *
     * <p>Features that fail individually are logged at WARN level and the
     * remaining features continue to be processed. This prevents a single
     * bad feature from blocking an entire agent snapshot.
     *
     * @param tenantId tenant identifier
     * @param features list of features to ingest
     * @return promise completing when all features have been attempted
     */
    public Promise<Void> ingestAll(String tenantId, List<Feature> features) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(features, "features");
        if (features.isEmpty()) {
            return Promise.complete();
        }
        return Promise.ofBlocking(blockingExecutor, () -> {
            int ok = 0;
            int err = 0;
            for (Feature f : features) {
                try {
                    delegate.ingest(tenantId, f);
                    ok++;
                } catch (Exception ex) {
                    log.warn("[FeatureStore] Failed to ingest '{}' tenant='{}': {}",
                            f.getName(), tenantId, ex.getMessage());
                    err++;
                }
            }
            log.debug("[FeatureStore] Batch ingest — ok={} errors={} tenant='{}'",
                    ok, err, tenantId);
            return null;
        });
    }

    // =========================================================================
    // Retrieval
    // =========================================================================

    /**
     * Retrieves the latest values for the given feature names for a workspace entity.
     *
     * <p>Probes Redis first (cache hit ≈ 1 ms P99); loads from PostgreSQL on misses
     * and backfills Redis automatically.
     *
     * @param tenantId     tenant identifier
     * @param entityId     entity identifier
     * @param featureNames ordered list of feature names to fetch
     * @return promise resolving to a map of featureName → value; absent features
     *         are omitted from the map (never null values)
     */
    public Promise<Map<String, Double>> getFeatures(
            String tenantId, String entityId, List<String> featureNames) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(featureNames, "featureNames");
        if (featureNames.isEmpty()) {
            return Promise.of(Map.of());
        }
        return Promise.ofBlocking(blockingExecutor, () -> {
            Map<String, Double> result = delegate.getFeatures(tenantId, entityId, featureNames);
            log.debug("[FeatureStore] Retrieved {}/{} features for entity '{}' tenant='{}'",
                    result.size(), featureNames.size(), entityId, tenantId);
            return result;
        });
    }

    /**
     * Convenience single-feature retrieval; returns {@code null} when the feature
     * is absent.
     *
     * @param tenantId    tenant identifier
     * @param entityId    entity identifier
     * @param featureName feature name to fetch
     * @return promise resolving to the feature value, or {@code null} if absent
     */
    public Promise<Double> getFeature(String tenantId, String entityId, String featureName) {
        return getFeatures(tenantId, entityId, List.of(featureName))
                .map(m -> m.get(featureName));
    }

    // =========================================================================
    // Diagnostics (useful for health probes)
    // =========================================================================

    /**
     * Returns the number of live entries currently in the in-process cache.
     *
     * @return promise resolving to the cache size
     */
    public Promise<Integer> cacheSize() {
        return Promise.ofBlocking(blockingExecutor, delegate::getCacheSize);
    }
}
