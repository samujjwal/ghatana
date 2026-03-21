package com.ghatana.pipeline.registry.repository;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.Pattern;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for pattern persistence.
 *
 * <p>
 * <b>Purpose</b><br>
 * Data access abstraction for pattern storage. Provides CRUD operations with
 * support for multi-tenant isolation and filtering by status.
 *
 * <p>
 * <b>Operations</b><br>
 * - Save: Insert new pattern - Update: Modify existing pattern - Delete: Remove
 * pattern - FindByIdAndTenant: Retrieve specific pattern with tenant isolation
 * - FindByTenant: List patterns for tenant with optional status filter
 *
 * <p>
 * <b>Threading</b><br>
 * All operations return ActiveJ Promise for non-blocking execution.
 *
 * @see Pattern
 * @doc.type interface
 * @doc.purpose Pattern repository abstraction
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface PatternRepository {

    /**
     * Save a new pattern.
     *
     * @param pattern the pattern to save
     * @return Promise containing saved pattern
     */
    Promise<Pattern> save(Pattern pattern);

    /**
     * Update an existing pattern.
     *
     * @param pattern the pattern with updated data
     * @return Promise containing updated pattern
     */
    Promise<Pattern> update(Pattern pattern);

    /**
     * Delete a pattern by ID.
     *
     * @param id the pattern ID
     * @return Promise that completes when deletion is done
     */
    Promise<Void> delete(String id);

    /**
     * Find pattern by ID and tenant.
     *
     * @param id the pattern ID
     * @param tenantId the tenant owner
     * @return Promise containing optional pattern
     */
    Promise<Optional<Pattern>> findByIdAndTenant(String id, TenantId tenantId);

    /**
     * Find all patterns for a tenant with optional status filter.
     *
     * @param tenantId the tenant owner
     * @param status optional status filter (null for all)
     * @return Promise containing list of patterns
     */
    Promise<List<Pattern>> findByTenant(TenantId tenantId, String status);
}
