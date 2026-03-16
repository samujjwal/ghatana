/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

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
 * AEP-specific façade over the platform {@link FeatureStoreService}.
 *
 * <p>Bridges the ActiveJ async world (Promises) and the synchronous
 * {@link FeatureStoreService} (JDBC + Redis cache).  Every call is
 * dispatched to the injected blocking {@link Executor} via
 * {@link Promise#ofBlocking} so the ActiveJ event-loop is never blocked.
 *
 * <h3>Typical usage inside an AEP operator</h3>
 * <pre>{@code
 * featureStoreClient
 *     .getFeatures(tenantId, entityId, List.of("score_7d", "txn_count_24h"))
 *     .map(features -> enrichEvent(event, features));
 * }</pre>
 *
 * <h3>Feature ingestion (from AEP events)</h3>
 * <pre>{@code
 * Feature f = Feature.builder()
 *     .name("event_payload_size")
 *     .entityId(event.getId())
 *     .value(event.getPayloadBytes())
 *     .timestamp(Instant.now())
 *     .build();
 * featureStoreClient.ingest(tenantId, f);
 * }</pre>
 *
 * <p><b>Data lineage</b>: Every feature ingested through this client is
 * automatically tracked by the platform {@link com.ghatana.aiplatform.featurestore.FeatureLineageTracker}
 * baked into the underlying {@link FeatureStoreService}.
 *
 * @doc.type class
 * @doc.purpose ActiveJ-async façade over FeatureStoreService for AEP operators
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AepFeatureStoreClient {

    private static final Logger log = LoggerFactory.getLogger(AepFeatureStoreClient.class);

    private final FeatureStoreService delegate;
    private final Executor blockingExecutor;

    /**
     * Creates a new client.
     *
     * @param delegate        the platform feature store service (JDBC + Redis)
     * @param blockingExecutor executor used for blocking JDBC / Redis calls;
     *                        must <em>not</em> be the ActiveJ event-loop thread
     */
    public AepFeatureStoreClient(FeatureStoreService delegate, Executor blockingExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    // =========================================================================
    // Ingestion
    // =========================================================================

    /**
     * Ingests a single feature for the given tenant.
     *
     * <p>The call is dispatched asynchronously; the returned promise completes
     * when the feature has been written to both the Redis hot tier and the
     * PostgreSQL cold tier.
     *
     * @param tenantId tenant identifier
     * @param feature  feature value object
     * @return promise that completes when ingestion is done
     */
    public Promise<Void> ingest(String tenantId, Feature feature) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(feature, "feature");
        return Promise.ofBlocking(blockingExecutor, () -> {
            delegate.ingest(tenantId, feature);
            log.debug("[FeatureStore] Ingested feature '{}' for entity '{}' tenant '{}'",
                    feature.getName(), feature.getEntityId(), tenantId);
            return null;
        });
    }

    /**
     * Convenience method that builds and ingests a feature from its component parts.
     *
     * @param tenantId    tenant identifier
     * @param entityId    entity identifier (e.g. event ID, agent ID)
     * @param featureName feature name (e.g. {@code "payload_size_bytes"})
     * @param value       numeric feature value
     * @return promise that completes when ingestion is done
     */
    public Promise<Void> ingest(String tenantId, String entityId, String featureName, double value) {
        Feature f = Feature.builder()
                .name(featureName)
                .entityId(entityId)
                .value(value)
                .timestamp(Instant.now())
                .build();
        return ingest(tenantId, f);
    }

    /**
     * Batch-ingests a list of features for the given tenant.
     *
     * <p>Each feature is ingested independently; partial failures are logged but
     * do not abort remaining ingestions.
     *
     * @param tenantId tenant identifier
     * @param features features to ingest (must not contain null elements)
     * @return promise that completes when all features have been processed
     */
    public Promise<Void> ingestAll(String tenantId, List<Feature> features) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(features, "features");
        if (features.isEmpty()) {
            return Promise.complete();
        }
        return Promise.ofBlocking(blockingExecutor, () -> {
            int successCount = 0;
            int errorCount = 0;
            for (Feature f : features) {
                try {
                    delegate.ingest(tenantId, f);
                    successCount++;
                } catch (Exception ex) {
                    log.warn("[FeatureStore] Failed to ingest feature '{}' for tenant '{}': {}",
                            f.getName(), tenantId, ex.getMessage());
                    errorCount++;
                }
            }
            log.debug("[FeatureStore] Batch ingest complete — success={} errors={} tenant='{}'",
                    successCount, errorCount, tenantId);
            return null;
        });
    }

    // =========================================================================
    // Retrieval
    // =========================================================================

    /**
     * Retrieves the latest values of the requested features for an entity.
     *
     * <p>Checks the Redis hot-tier first (sub-millisecond for cache hits); falls
     * back to PostgreSQL on misses and re-populates the cache.
     *
     * @param tenantId     tenant identifier
     * @param entityId     entity identifier
     * @param featureNames names of features to retrieve
     * @return promise resolving to a map from feature name → value;
     *         missing features are absent from the map (not null)
     */
    public Promise<Map<String, Double>> getFeatures(
            String tenantId, String entityId, List<String> featureNames) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(featureNames, "featureNames");
        if (featureNames.isEmpty()) {
            return Promise.of(Map.of());
        }
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.getFeatures(tenantId, entityId, featureNames));
    }

    // =========================================================================
    // Cache management (test / ops hooks)
    // =========================================================================

    /**
     * Evicts all entries from the in-process feature cache.
     *
     * <p>Intended for use in tests and operational cache-reset tooling.
     * Does <em>not</em> affect the Redis or PostgreSQL stores.
     */
    public void clearLocalCache() {
        delegate.clearCache();
        log.info("[FeatureStore] Local feature cache cleared.");
    }
}
