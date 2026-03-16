/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.feature;

import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * AEP-specific façade over the platform {@link ModelRegistryService}.
 *
 * <p>Wraps the synchronous JDBC-backed registry with ActiveJ {@link Promise}
 * dispatch so callers running on the ActiveJ event-loop thread never block.
 *
 * <h3>Model lifecycle for AEP ML components</h3>
 * <ol>
 *   <li>Register a new model version: {@link #register}</li>
 *   <li>Promote to ACTIVE production via: {@link #promoteToProduction}</li>
 *   <li>Look up active model for inference: {@link #findActiveModel}</li>
 *   <li>Deprecate old versions after rollout: {@link #deprecate}</li>
 * </ol>
 *
 * <h3>Example — registering and promoting a pattern-recommender model</h3>
 * <pre>{@code
 * ModelMetadata model = ModelMetadata.builder()
 *     .id(UUID.randomUUID())
 *     .tenantId(tenantId)
 *     .name("pattern-recommender")
 *     .version("v2.1.0")
 *     .framework("sklearn")
 *     .deploymentStatus(DeploymentStatus.STAGED)
 *     .trainingMetrics(Map.of("f1_score", 0.93, "auc_roc", 0.97))
 *     .createdAt(Instant.now())
 *     .updatedAt(Instant.now())
 *     .build();
 *
 * modelRegistryClient.register(tenantId, model)
 *     .then(__ -> modelRegistryClient.promoteToProduction(tenantId, model.getId()))
 *     .whenResult(__ -> log.info("Model promoted to production"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ-async façade over ModelRegistryService for AEP ML components
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AepModelRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(AepModelRegistryClient.class);

    private final ModelRegistryService delegate;
    private final Executor blockingExecutor;

    /**
     * Creates a new client.
     *
     * @param delegate        the platform model registry service (JDBC-backed)
     * @param blockingExecutor executor for blocking JDBC operations;
     *                        must <em>not</em> be the ActiveJ event-loop thread
     */
    public AepModelRegistryClient(ModelRegistryService delegate, Executor blockingExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a new model version in the registry.
     *
     * <p>Idempotent for the same (tenantId, modelName, version) tuple — a
     * second registration will log a warning but not fail.
     *
     * @param tenantId tenant identifier
     * @param model    model metadata to register
     * @return promise that completes when registration is persisted
     */
    public Promise<Void> register(String tenantId, ModelMetadata model) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(model, "model");
        return Promise.ofBlocking(blockingExecutor, () -> {
            delegate.register(model);
            log.info("[ModelRegistry] Registered model '{}/{}' status={} tenant='{}'",
                    model.getName(), model.getVersion(), model.getDeploymentStatus(), tenantId);
            return null;
        });
    }

    /**
     * Convenience builder-based registration for the common AEP use case where
     * a new model variant is staged after training.
     *
     * @param tenantId       tenant identifier
     * @param modelName      logical model name (e.g. {@code "pattern-recommender"})
     * @param version        semantic version string (e.g. {@code "v3.0.0"})
     * @param framework      ML framework (e.g. {@code "sklearn"}, {@code "pytorch"})
     * @param trainingMetrics key/value training quality metrics
     * @return promise that completes when registration is persisted
     */
    public Promise<Void> registerStaged(
            String tenantId,
            String modelName,
            String version,
            String framework,
            Map<String, Double> trainingMetrics) {
        Instant now = Instant.now();
        ModelMetadata model = ModelMetadata.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .name(modelName)
                .version(version)
                .framework(framework)
                .deploymentStatus(DeploymentStatus.STAGED)
                .trainingMetrics(trainingMetrics)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return register(tenantId, model);
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Finds the active (PRODUCTION) model by name and optional version.
     *
     * <p>When {@code version} is {@code null} the latest PRODUCTION version is
     * returned.
     *
     * @param tenantId tenant identifier
     * @param name     logical model name
     * @param version  exact version string, or {@code null} to get the latest
     * @return promise of an optional model metadata; empty if not found
     */
    public Promise<Optional<ModelMetadata>> findByName(
            String tenantId, String name, String version) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.findByName(tenantId, name, version));
    }

    /**
     * Returns the single ACTIVE/PRODUCTION model for the given name.
     *
     * <p>Shorthand for finding the model with {@link DeploymentStatus#ACTIVE}
     * or {@link DeploymentStatus#PRODUCTION} status that should currently be
     * serving inference traffic.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @return promise of an optional model; empty when no active model exists
     */
    public Promise<Optional<ModelMetadata>> findActiveModel(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.findByStatus(tenantId, DeploymentStatus.ACTIVE)
                        .stream()
                        .filter(m -> m.getName().equals(modelName))
                        .findFirst()
                        .or(() -> delegate.findByStatus(tenantId, DeploymentStatus.PRODUCTION)
                                .stream()
                                .filter(m -> m.getName().equals(modelName))
                                .findFirst()));
    }

    /**
     * Lists all registered versions of the given model, ordered by registration
     * time descending.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @return promise resolving to the version list (never null, may be empty)
     */
    public Promise<List<ModelMetadata>> listVersions(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.listVersions(tenantId, modelName));
    }

    // =========================================================================
    // Lifecycle management
    // =========================================================================

    /**
     * Promotes a model to {@link DeploymentStatus#PRODUCTION}.
     *
     * @param tenantId tenant identifier
     * @param modelId  model UUID (from {@link ModelMetadata#getId()})
     * @return promise that completes when the status update is persisted
     */
    public Promise<Void> promoteToProduction(String tenantId, UUID modelId) {
        return updateStatus(tenantId, modelId, DeploymentStatus.PRODUCTION,
                "Promoted to production");
    }

    /**
     * Marks a model as {@link DeploymentStatus#DEPRECATED}.
     *
     * <p>Deprecated models are no longer used for new inference but remain in
     * the registry for audit purposes.
     *
     * @param tenantId tenant identifier
     * @param modelId  model UUID
     * @return promise that completes when the status update is persisted
     */
    public Promise<Void> deprecate(String tenantId, UUID modelId) {
        return updateStatus(tenantId, modelId, DeploymentStatus.DEPRECATED,
                "Deprecated");
    }

    /**
     * Marks a model as {@link DeploymentStatus#CANARY} for partial traffic
     * rollout evaluation.
     *
     * @param tenantId tenant identifier
     * @param modelId  model UUID
     * @return promise that completes when the status update is persisted
     */
    public Promise<Void> promoteToCanary(String tenantId, UUID modelId) {
        return updateStatus(tenantId, modelId, DeploymentStatus.CANARY,
                "Promoted to canary");
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private Promise<Void> updateStatus(
            String tenantId, UUID modelId, DeploymentStatus newStatus, String logLabel) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelId, "modelId");
        return Promise.ofBlocking(blockingExecutor, () -> {
            delegate.updateStatus(tenantId, modelId, newStatus);
            log.info("[ModelRegistry] {} model '{}' tenant='{}'", logLabel, modelId, tenantId);
            return null;
        });
    }
}
