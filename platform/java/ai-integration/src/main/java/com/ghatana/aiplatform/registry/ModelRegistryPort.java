package com.ghatana.aiplatform.registry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for model registry operations.
 *
 * <p>Abstracts the persistence-backed {@link ModelRegistryService} to enable
 * lightweight test doubles without requiring a live database connection.
 *
 * @doc.type interface
 * @doc.purpose Port abstraction for ML model registry operations
 * @doc.layer platform
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface ModelRegistryPort {

    /**
     * Registers a new model or updates existing model metadata.
     *
     * @param model the model metadata to register
     */
    void register(ModelMetadata model);

    /**
     * Finds a model by tenant, name, and version.
     *
     * @param tenantId tenant identifier
     * @param name     model name
     * @param version  model version
     * @return optional model metadata
     */
    Optional<ModelMetadata> findByName(String tenantId, String name, String version);

    /**
     * Lists all models for a tenant with the given deployment status.
     *
     * @param tenantId tenant identifier
     * @param status   deployment status filter
     * @return list of matching models
     */
    List<ModelMetadata> findByStatus(String tenantId, DeploymentStatus status);

    /**
     * Lists all versions of a model for a tenant.
     *
     * @param tenantId tenant identifier
     * @param name     model name
     * @return list of model versions
     */
    List<ModelMetadata> listVersions(String tenantId, String name);

    /**
     * Updates the deployment status of a model.
     *
     * @param tenantId  tenant identifier
     * @param modelId   model unique identifier
     * @param newStatus new deployment status
     */
    void updateStatus(String tenantId, UUID modelId, DeploymentStatus newStatus);
}
