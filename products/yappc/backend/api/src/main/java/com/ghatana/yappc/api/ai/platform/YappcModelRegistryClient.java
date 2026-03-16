/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.ai.platform;

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
 * YAPPC-specific ActiveJ async façade over the platform {@link ModelRegistryService}.
 *
 * <p>Exposes the model lifecycle capabilities needed by YAPPC agents and AI
 * services — registering new model versions, querying active models, and
 * transitioning between deployment stages — without blocking the ActiveJ
 * event-loop thread. All JDBC-backed operations are dispatched to the provided
 * {@link Executor} via {@link Promise#ofBlocking}.
 *
 * <h3>YAPPC use cases</h3>
 * <ul>
 *   <li>{@link com.ghatana.products.yappc.domain.agent.PredictionAgent} — fetch
 *       the current {@code PRODUCTION} model for phase prediction.</li>
 *   <li>AI suggestion pipeline — register fine-tuned code suggestion models and
 *       promote them after offline evaluation.</li>
 *   <li>Canary evaluation — query {@code CANARY} models for shadow-mode
 *       inference before full promotion.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * modelRegistryClient
 *     .findActiveModel(tenantId, "phase-prediction-v3")
 *     .whenResult(opt -> opt.ifPresent(m ->
 *         log.info("Using model {} v{}", m.getName(), m.getVersion())));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ async façade over ModelRegistryService for YAPPC AI services
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class YappcModelRegistryClient {

    private static final Logger log = LoggerFactory.getLogger(YappcModelRegistryClient.class);

    private final ModelRegistryService delegate;
    private final Executor blockingExecutor;

    /**
     * Creates the client.
     *
     * @param delegate          platform model registry service (JDBC-backed)
     * @param blockingExecutor  executor for blocking JDBC calls; must not be
     *                          the ActiveJ event-loop thread
     */
    public YappcModelRegistryClient(ModelRegistryService delegate, Executor blockingExecutor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor");
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns the single {@code PRODUCTION} or {@code ACTIVE} model for the
     * given name; empty when no active model exists.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @return promise of an optional model metadata
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
     * Finds a specific model by name and version.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @param version   exact version string
     * @return promise of an optional model metadata
     */
    public Promise<Optional<ModelMetadata>> findByVersion(
            String tenantId, String modelName, String version) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(version, "version");
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.findByName(tenantId, modelName, version));
    }

    /**
     * Lists all registered model versions for the given name, newest first.
     *
     * @param tenantId  tenant identifier
     * @param modelName logical model name
     * @return promise resolving to the version list
     */
    public Promise<List<ModelMetadata>> listVersions(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.listVersions(tenantId, modelName));
    }

    /**
     * Lists all models in the specified deployment status for the given tenant.
     *
     * @param tenantId tenant identifier
     * @param status   deployment status filter
     * @return promise resolving to matching models
     */
    public Promise<List<ModelMetadata>> listByStatus(String tenantId, DeploymentStatus status) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(status, "status");
        return Promise.ofBlocking(blockingExecutor, () ->
                delegate.findByStatus(tenantId, status));
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a new model version in the STAGED state.
     *
     * <p>Idempotent for the same (tenantId, modelName, version) tuple.
     *
     * @param tenantId        tenant identifier
     * @param modelName       logical model name
     * @param version         semantic version string
     * @param framework       ML framework (e.g. {@code "pytorch"}, {@code "sklearn"})
     * @param trainingMetrics key/value training quality metrics
     * @return promise completing when the registration is persisted
     */
    public Promise<Void> registerStaged(
            String tenantId, String modelName, String version,
            String framework, Map<String, Double> trainingMetrics) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(version, "version");
        return Promise.ofBlocking(blockingExecutor, () -> {
            Instant now = Instant.now();
            ModelMetadata model = ModelMetadata.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .name(modelName)
                    .version(version)
                    .framework(framework)
                    .deploymentStatus(DeploymentStatus.STAGED)
                    .trainingMetrics(trainingMetrics != null ? trainingMetrics : Map.of())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            delegate.register(model);
            log.info("[ModelRegistry] Registered STAGED model '{}/{}' tenant='{}'",
                    modelName, version, tenantId);
            return null;
        });
    }

    // =========================================================================
    // Lifecycle management
    // =========================================================================

    /**
     * Promotes a model to {@link DeploymentStatus#PRODUCTION}.
     *
     * @param tenantId tenant identifier
     * @param modelId  model UUID
     * @return promise completing when the status is updated
     */
    public Promise<Void> promoteToProduction(String tenantId, UUID modelId) {
        return updateStatus(tenantId, modelId, DeploymentStatus.PRODUCTION, "promoted to PRODUCTION");
    }

    /**
     * Marks a model as {@link DeploymentStatus#CANARY} for partial traffic evaluation.
     *
     * @param tenantId tenant identifier
     * @param modelId  model UUID
     * @return promise completing when the status is updated
     */
    public Promise<Void> markAsCanary(String tenantId, UUID modelId) {
        return updateStatus(tenantId, modelId, DeploymentStatus.CANARY, "marked as CANARY");
    }

    /**
     * Deprecates a model version.
     *
     * @param tenantId tenant identifier
     * @param modelId  model UUID
     * @return promise completing when the status is updated
     */
    public Promise<Void> deprecate(String tenantId, UUID modelId) {
        return updateStatus(tenantId, modelId, DeploymentStatus.DEPRECATED, "deprecated");
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
            log.info("[ModelRegistry] Model '{}' {} for tenant='{}'", modelId, logLabel, tenantId);
            return null;
        });
    }
}
