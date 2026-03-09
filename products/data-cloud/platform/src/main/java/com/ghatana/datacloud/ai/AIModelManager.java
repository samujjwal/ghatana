/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.ai;

import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.DeploymentStatus;
import com.ghatana.aiplatform.registry.ModelMetadata;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AI model lifecycle within DataCloud.
 *
 * <p>Provides registration, retrieval, promotion, and staleness detection
 * for ML models used by quality scoring and other AI-driven features.
 *
 * @doc.type class
 * @doc.purpose AI model lifecycle management for DataCloud
 * @doc.layer product
 * @doc.pattern Service
 */
public class AIModelManager {

    private final ModelRegistryService modelRegistry;
    private final AiMetricsEmitter aiMetrics;
    private final Map<String, ModelMetadata> modelCache = new ConcurrentHashMap<>();

    public AIModelManager(ModelRegistryService modelRegistry, AiMetricsEmitter aiMetrics) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry");
        this.aiMetrics = Objects.requireNonNull(aiMetrics, "aiMetrics");
    }

    /**
     * Registers a model for a tenant.
     */
    public Promise<ModelMetadata> registerModel(String tenantId, ModelMetadata model) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(model, "model is required");
        if (model.getName() == null || model.getName().isBlank()) {
            throw new IllegalArgumentException("model.name is required");
        }
        if (model.getVersion() == null || model.getVersion().isBlank()) {
            throw new IllegalArgumentException("model.version is required");
        }
        if (model.getTenantId() == null || model.getTenantId().isBlank()) {
            throw new IllegalArgumentException("model.tenantId is required");
        }

        return Promise.ofBlocking(Runnable::run, () -> {
            modelRegistry.register(model);
            String cacheKey = tenantId + ":" + model.getName();
            modelCache.put(cacheKey, model);
            return model;
        });
    }

    /**
     * Gets the active (production) model for a tenant by name.
     */
    public Promise<ModelMetadata> getActiveModel(String tenantId, String modelName) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(modelName, "modelName is required");

        String cacheKey = tenantId + ":" + modelName;
        ModelMetadata cached = modelCache.get(cacheKey);
        if (cached != null) {
            return Promise.of(cached);
        }

        return Promise.ofBlocking(Runnable::run, () -> {
            List<ModelMetadata> models = modelRegistry.findByStatus(tenantId, DeploymentStatus.PRODUCTION);
            return models.stream()
                    .filter(m -> modelName.equals(m.getName()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active model found"));
        });
    }

    /**
     * Lists all models for a tenant across all deployment statuses.
     */
    public Promise<List<ModelMetadata>> getAllModels(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId is required");

        return Promise.ofBlocking(Runnable::run, () -> {
            List<ModelMetadata> allModels = new ArrayList<>();
            allModels.addAll(modelRegistry.findByStatus(tenantId, DeploymentStatus.PRODUCTION));
            allModels.addAll(modelRegistry.findByStatus(tenantId, DeploymentStatus.STAGED));
            allModels.addAll(modelRegistry.findByStatus(tenantId, DeploymentStatus.RETIRED));
            return allModels;
        });
    }

    /**
     * Promotes a model to production.
     */
    public Promise<ModelMetadata> promoteToProduction(String tenantId, String modelName, String version) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(modelName, "modelName is required");
        Objects.requireNonNull(version, "version is required");

        return Promise.ofBlocking(Runnable::run, () -> {
            ModelMetadata model = modelRegistry.findByName(tenantId, modelName, version)
                    .orElseThrow(() -> new IllegalStateException("Model not found"));

            if (model.getDeploymentStatus() != DeploymentStatus.STAGED) {
                throw new IllegalStateException("Model must be in STAGED status to promote");
            }

            modelRegistry.updateStatus(tenantId, model.getId(), DeploymentStatus.PRODUCTION);

            String cacheKey = tenantId + ":" + modelName;
            modelCache.remove(cacheKey);

            return modelRegistry.findByName(tenantId, modelName, version)
                    .orElseThrow(() -> new IllegalStateException("Model not found after promotion"));
        });
    }

    /**
     * Checks if a model is stale based on max age.
     */
    public boolean isModelStale(ModelMetadata model, Duration maxAge) {
        if (model == null || model.getCreatedAt() == null) {
            return true;
        }
        Instant threshold = Instant.now().minus(maxAge);
        Instant modelTime = model.getUpdatedAt() != null ? model.getUpdatedAt() : model.getCreatedAt();
        return modelTime.isBefore(threshold);
    }

    /**
     * Records an inference event.
     */
    public void recordInference(String tenantId, String modelName, String version,
                                Duration latency, boolean success) {
        aiMetrics.recordInference(modelName, version, latency, success);
    }

    /**
     * Records prediction quality.
     */
    public void recordPredictionQuality(String tenantId, String modelName, String version, double quality) {
        aiMetrics.recordPredictionQuality(modelName, version, quality);
    }

    /**
     * Clears the model cache.
     */
    public void clearCache() {
        modelCache.clear();
    }
}
