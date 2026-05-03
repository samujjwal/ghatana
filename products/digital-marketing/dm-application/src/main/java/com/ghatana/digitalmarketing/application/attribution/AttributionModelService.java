package com.ghatana.digitalmarketing.application.attribution;

import com.ghatana.digitalmarketing.domain.attribution.AttributionModel;
import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Service for managing attribution models (DMOS-P3-005).
 *
 * @doc.type interface
 * @doc.purpose Service interface for attribution model operations
 * @doc.layer application
 */
public interface AttributionModelService {

    /**
     * Create a new attribution model (multi-touch attribution).
     */
    Promise<AttributionModel> createModel(DmTenantId tenantId, DmWorkspaceId workspaceId, String modelName, String modelType, Map<String, Double> touchpointWeights);

    /**
     * Get an attribution model by ID.
     */
    Promise<AttributionModel> getModel(String modelId);

    /**
     * Get active attribution model for workspace (media mix model lifecycle).
     */
    Promise<AttributionModel> getActiveModel(DmWorkspaceId workspaceId);

    /**
     * Calculate attribution with confidence intervals.
     */
    Promise<Map<String, Double>> calculateAttribution(String modelId, Map<String, Double> touchpointData);

    /**
     * Update an attribution model.
     */
    Promise<AttributionModel> updateModel(String modelId, String modelName, Map<String, Double> touchpointWeights);

    /**
     * Activate an attribution model.
     */
    Promise<AttributionModel> activateModel(String modelId);

    /**
     * Deactivate an attribution model.
     */
    Promise<Void> deactivateModel(String modelId);

    /**
     * Generate budget optimization recommendations.
     */
    Promise<Map<String, Double>> generateBudgetRecommendations(String modelId, Map<String, Double> currentBudget);
}
