package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.domain.attribution.AttributionModel;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service implementation for managing attribution models (DMOS-P3-005).
 *
 * @doc.type class
 * @doc.purpose Service implementation for attribution model operations
 * @doc.layer application
 */
public final class AttributionModelServiceImpl implements AttributionModelService {

    private static final Logger logger = LoggerFactory.getLogger(AttributionModelServiceImpl.class);

    private final AttributionModelRepository repository;

    public AttributionModelServiceImpl(AttributionModelRepository repository) {
        this.repository = repository;
    }

    @Override
    public Promise<AttributionModel> createModel(DmTenantId tenantId, DmWorkspaceId workspaceId, String modelName, String modelType, Map<String, Double> touchpointWeights) {
        String modelId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        AttributionModel model = AttributionModel.builder()
            .modelId(modelId)
            .tenantId(tenantId)
            .workspaceId(workspaceId)
            .modelName(modelName)
            .modelType(modelType)
            .touchpointWeights(touchpointWeights)
            .confidenceIntervalLower(0.0)
            .confidenceIntervalUpper(1.0)
            .active(true)
            .createdAt(now)
            .updatedAt(now)
            .build();

        logger.info("Creating attribution model: {} for workspace: {}", modelName, workspaceId.getValue());
        return repository.save(model);
    }

    @Override
    public Promise<AttributionModel> getModel(String modelId) {
        return repository.findById(modelId)
            .then(modelOpt -> modelOpt.orElseThrow(() -> new IllegalArgumentException("Attribution model not found: " + modelId)));
    }

    @Override
    public Promise<AttributionModel> getActiveModel(DmWorkspaceId workspaceId) {
        return repository.findActiveByWorkspace(workspaceId)
            .then(modelOpt -> modelOpt.orElseThrow(() -> new IllegalArgumentException("No active attribution model for workspace: " + workspaceId.getValue())));
    }

    @Override
    public Promise<Map<String, Double>> calculateAttribution(String modelId, Map<String, Double> touchpointData) {
        return getModel(modelId)
            .then(model -> {
                Map<String, Double> attribution = new HashMap<>();
                Map<String, Double> weights = model.getTouchpointWeights();

                for (Map.Entry<String, Double> entry : touchpointData.entrySet()) {
                    String touchpoint = entry.getKey();
                    Double value = entry.getValue();
                    Double weight = weights.getOrDefault(touchpoint, 0.0);
                    attribution.put(touchpoint, value * weight);
                }

                logger.info("Calculated attribution for model: {}", modelId);
                return Promise.of(attribution);
            });
    }

    @Override
    public Promise<AttributionModel> updateModel(String modelId, String modelName, Map<String, Double> touchpointWeights) {
        return getModel(modelId)
            .then(model -> {
                AttributionModel updated = AttributionModel.builder()
                    .modelId(model.getModelId())
                    .tenantId(model.getTenantId())
                    .workspaceId(model.getWorkspaceId())
                    .modelName(modelName)
                    .modelType(model.getModelType())
                    .touchpointWeights(touchpointWeights)
                    .confidenceIntervalLower(model.getConfidenceIntervalLower())
                    .confidenceIntervalUpper(model.getConfidenceIntervalUpper())
                    .active(model.isActive())
                    .createdAt(model.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Updated attribution model: {}", modelId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<AttributionModel> activateModel(String modelId) {
        return getModel(modelId)
            .then(model -> {
                AttributionModel updated = AttributionModel.builder()
                    .modelId(model.getModelId())
                    .tenantId(model.getTenantId())
                    .workspaceId(model.getWorkspaceId())
                    .modelName(model.getModelName())
                    .modelType(model.getModelType())
                    .touchpointWeights(model.getTouchpointWeights())
                    .confidenceIntervalLower(model.getConfidenceIntervalLower())
                    .confidenceIntervalUpper(model.getConfidenceIntervalUpper())
                    .active(true)
                    .createdAt(model.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                logger.info("Activated attribution model: {}", modelId);
                return repository.update(updated);
            });
    }

    @Override
    public Promise<Void> deactivateModel(String modelId) {
        return getModel(modelId)
            .then(model -> {
                AttributionModel updated = AttributionModel.builder()
                    .modelId(model.getModelId())
                    .tenantId(model.getTenantId())
                    .workspaceId(model.getWorkspaceId())
                    .modelName(model.getModelName())
                    .modelType(model.getModelType())
                    .touchpointWeights(model.getTouchpointWeights())
                    .confidenceIntervalLower(model.getConfidenceIntervalLower())
                    .confidenceIntervalUpper(model.getConfidenceIntervalUpper())
                    .active(false)
                    .createdAt(model.getCreatedAt())
                    .updatedAt(Instant.now())
                    .build();

                return repository.update(updated).then(__ -> {
                    logger.info("Deactivated attribution model: {}", modelId);
                    return Promise.of(null);
                });
            });
    }

    @Override
    public Promise<Map<String, Double>> generateBudgetRecommendations(String modelId, Map<String, Double> currentBudget) {
        return getModel(modelId)
            .then(model -> {
                Map<String, Double> recommendations = new HashMap<>();
                Map<String, Double> weights = model.getTouchpointWeights();

                for (Map.Entry<String, Double> entry : currentBudget.entrySet()) {
                    String channel = entry.getKey();
                    Double budget = entry.getValue();
                    Double weight = weights.getOrDefault(channel, 0.0);

                    if (weight > 0.5) {
                        recommendations.put(channel, budget * 1.2);
                    } else if (weight < 0.3) {
                        recommendations.put(channel, budget * 0.8);
                    } else {
                        recommendations.put(channel, budget);
                    }
                }

                logger.info("Generated budget recommendations for model: {}", modelId);
                return Promise.of(recommendations);
            });
    }
}
