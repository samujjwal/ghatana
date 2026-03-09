package com.ghatana.pipeline.registry.repository;

import com.ghatana.platform.core.common.pagination.Page;
import com.ghatana.pipeline.registry.model.Pipeline;
import com.ghatana.platform.domain.auth.TenantId;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for pipeline persistence with dual config format support.
 *
 * <p>Supports both legacy string config and new structured config formats.
 * Implementations must handle:
 * <ul>
 *   <li>Storing both config formats (binary proto + JSON)</li>
 *   <li>Reading with preference for structured config</li>
 *   <li>Fallback to legacy config for backward compatibility</li>
 *   <li>Migration tracking via has_structured_config flag</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Pipeline persistence with dual config format support
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PipelineRepository {

    /**
     * Save or update a pipeline with dual format support.
     *
     * <p>For new pipelines with structured config:
     * <ul>
     *   <li>Stores config as Protocol Buffer bytes (binary)</li>
     *   <li>Stores config as JSON text for querying</li>
     *   <li>Sets has_structured_config = true</li>
     * </ul>
     *
     * <p>For legacy pipelines:
     * <ul>
     *   <li>Stores config as JSON string</li>
     *   <li>Sets has_structured_config = false</li>
     * </ul>
     *
     * @param pipeline the pipeline to save
     * @return promise with saved pipeline
     */
    Promise<Pipeline> save(Pipeline pipeline);

    /**
     * Find a pipeline by ID with format preference.
     *
     * <p>Reading strategy:
     * <ol>
     *   <li>If has_structured_config = true: Parse config_proto bytes</li>
     *   <li>Else if config exists: Use legacy JSON config</li>
     *   <li>Return empty if neither format exists</li>
     * </ol>
     *
     * @param id the pipeline ID
     * @param tenantId the tenant ID
     * @return promise with optional pipeline
     */
    Promise<Optional<Pipeline>> findById(String id, TenantId tenantId);

    /**
     * Find the latest version of a pipeline by name.
     *
     * @param name the pipeline name
     * @param tenantId the tenant ID
     * @return promise with optional pipeline
     */
    Promise<Optional<Pipeline>> findLatestVersion(String name, TenantId tenantId);

    /**
     * Find a specific version of a pipeline by name and version number.
     *
     * @param name the pipeline name
     * @param version the version number
     * @param tenantId the tenant ID
     * @return promise with optional pipeline
     */
    Promise<Optional<Pipeline>> findByNameAndVersion(String name, int version, TenantId tenantId);

    /**
     * Find all versions of a pipeline by name.
     *
     * @param name the pipeline name
     * @param tenantId the tenant ID
     * @return promise with list of all versions
     */
    Promise<List<Pipeline>> findAllVersions(String name, TenantId tenantId);

    /**
     * List pipelines with pagination and filtering.
     *
     * <p>Can filter by:
     * <ul>
     *   <li>Tenant ID</li>
     *   <li>Active status</li>
     *   <li>Name filter</li>
     * </ul>
     *
     * @param tenantId the tenant ID
     * @param nameFilter optional name filter
     * @param activeOnly optional active status filter
     * @param page page number (1-based)
     * @param size page size
     * @return promise with page of pipelines
     */
    Promise<Page<Pipeline>> findAll(TenantId tenantId, String nameFilter, Boolean activeOnly, int page, int size);

    /**
     * Check if a pipeline exists.
     *
     * @param id the pipeline ID
     * @param tenantId the tenant ID
     * @return promise with boolean indicating existence
     */
    Promise<Boolean> exists(String id, TenantId tenantId);

    /**
     * Delete a pipeline.
     *
     * @param id the pipeline ID
     * @param tenantId the tenant ID
     * @param softDelete true to soft delete (mark inactive), false for hard delete
     * @param deletedBy the user deleting the pipeline
     * @return promise that completes when delete is done
     */
    Promise<Void> delete(String id, TenantId tenantId, boolean softDelete, String deletedBy);

    /**
     * Get the next version number for a pipeline name.
     *
     * @param name the pipeline name
     * @param tenantId the tenant ID
     * @return promise with next version number
     */
    Promise<Integer> nextVersion(String name, TenantId tenantId);

    /**
     * Count pipelines using structured config format.
     *
     * <p>Useful for monitoring migration progress.
     *
     * @param tenantId optional tenant filter (null for all)
     * @return promise with count of pipelines using structured config
     */
    Promise<Long> countStructuredConfigPipelines(TenantId tenantId);

    /**
     * Count pipelines using legacy config format.
     *
     * <p>Useful for identifying pipelines needing migration.
     *
     * @param tenantId optional tenant filter (null for all)
     * @return promise with count of pipelines using legacy config
     */
    Promise<Long> countLegacyConfigPipelines(TenantId tenantId);
}

