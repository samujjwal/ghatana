package com.ghatana.pipeline.registry.service;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.pipeline.registry.model.Pattern;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for pattern registration and management.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides operations for registering, querying, and managing compiled patterns
 * within the Agentic Event Processor. All operations are tenant-scoped and
 * support asynchronous execution via ActiveJ Promise.
 *
 * <p>
 * <b>Operations</b><br>
 * - Register patterns by specification with compilation and validation -
 * Retrieve patterns by ID or list by tenant and status - Update and delete
 * patterns - Activate/deactivate patterns for execution
 *
 * <p>
 * <b>Observability</b><br>
 * All operations emit metrics (count, errors, latency) and structured logs with
 * tenantId, patternId, userId for audit and monitoring.
 *
 * @see Pattern
 * @see PatternRegistryService
 * @doc.type interface
 * @doc.purpose Pattern registration and management API
 * @doc.layer product
 * @doc.pattern Service
 */
public interface PatternService {

    /**
     * Register a new pattern by specification.
     *
     * <p>
     * GIVEN: Valid PatternSpecification and metadata WHEN: register() is called
     * THEN: Pattern is validated, compiled, persisted, and returned with ID
     *
     * @param pattern the pattern to register
     * @param userId the user registering the pattern (for audit)
     * @return Promise containing registered pattern with ID and compilation
     * results
     */
    Promise<Pattern> register(Pattern pattern, String userId);

    /**
     * Retrieve a pattern by ID and tenant.
     *
     * <p>
     * GIVEN: Valid pattern ID and tenant context WHEN: getById() is called
     * THEN: Pattern is retrieved from storage if it exists
     *
     * @param id the pattern ID
     * @param tenantId the tenant owner
     * @return Promise containing optional pattern
     */
    Promise<Optional<Pattern>> getById(String id, TenantId tenantId);

    /**
     * List patterns for a tenant with optional status filter.
     *
     * <p>
     * GIVEN: Tenant ID and optional status filter WHEN: list() is called THEN:
     * All matching patterns for tenant are returned
     *
     * @param tenantId the tenant owner
     * @param status optional status filter (null for all statuses)
     * @return Promise containing list of patterns
     */
    Promise<List<Pattern>> list(TenantId tenantId, String status);

    /**
     * Update an existing pattern.
     *
     * <p>
     * GIVEN: Valid pattern with updated properties WHEN: update() is called
     * THEN: Pattern is recompiled, validated, and persisted
     *
     * @param id the pattern ID
     * @param pattern the updated pattern data
     * @param userId the user making the update (for audit)
     * @return Promise containing updated pattern
     */
    Promise<Pattern> update(String id, Pattern pattern, String userId);

    /**
     * Delete a pattern permanently.
     *
     * <p>
     * GIVEN: Valid pattern ID and tenant context WHEN: delete() is called THEN:
     * Pattern is marked as deleted or removed from storage
     *
     * @param id the pattern ID
     * @param tenantId the tenant owner
     * @param userId the user deleting the pattern (for audit)
     * @return Promise that completes when deletion is done
     */
    Promise<Void> delete(String id, TenantId tenantId, String userId);

    /**
     * Activate a pattern for execution.
     *
     * <p>
     * GIVEN: Valid pattern ID with COMPILED status WHEN: activate() is called
     * THEN: Pattern status transitions to ACTIVE
     *
     * @param id the pattern ID
     * @param tenantId the tenant owner
     * @param userId the user making the request (for audit)
     * @return Promise that completes when activation is done
     */
    Promise<Void> activate(String id, TenantId tenantId, String userId);

    /**
     * Deactivate a pattern.
     *
     * <p>
     * GIVEN: Valid active pattern ID WHEN: deactivate() is called THEN: Pattern
     * status transitions to INACTIVE
     *
     * @param id the pattern ID
     * @param tenantId the tenant owner
     * @param userId the user making the request (for audit)
     * @return Promise that completes when deactivation is done
     */
    Promise<Void> deactivate(String id, TenantId tenantId, String userId);

    /**
     * Check if a pattern exists for a tenant.
     *
     * <p>
     * GIVEN: Pattern ID and tenant context WHEN: exists() is called THEN:
     * Returns true if pattern exists and belongs to tenant
     *
     * @param id the pattern ID
     * @param tenantId the tenant owner
     * @return Promise containing existence check result
     */
    Promise<Boolean> exists(String id, TenantId tenantId);
}
