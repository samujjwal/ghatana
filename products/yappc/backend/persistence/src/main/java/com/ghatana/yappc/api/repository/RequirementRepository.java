/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.Requirement;
import com.ghatana.yappc.api.domain.Requirement.Priority;
import com.ghatana.yappc.api.domain.Requirement.RequirementStatus;
import com.ghatana.yappc.api.domain.Requirement.RequirementType;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port interface for Requirement persistence.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for requirement storage and retrieval operations. Supports multi-tenancy and
 * async operations via ActiveJ Promise.
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations are tenant-scoped. Tenant ID must be provided for all queries to ensure proper
 * data isolation.
 *
 * <p><b>Implementation Notes</b><br>
 * Implementations should: - Validate tenant isolation on all operations - Return empty
 * Optional/List for not-found cases - Use Promise for async execution - Log operations for audit
 * trail
 *
 * @doc.type interface
 * @doc.purpose Requirement repository port
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface RequirementRepository {

  /**
   * Save a requirement (create or update).
   *
   * @param requirement the requirement to save
   * @return Promise of saved requirement with generated ID
   */
  Promise<Requirement> save(Requirement requirement);

  /**
   * Find requirement by ID within tenant.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @return Promise of Optional requirement
   */
  Promise<Optional<Requirement>> findById(String tenantId, UUID id);

  /**
   * Find all requirements for a tenant.
   *
   * @param tenantId the tenant ID
   * @return Promise of list of requirements
   */
  Promise<List<Requirement>> findAllByTenant(String tenantId);

  /**
   * Find requirements by project.
   *
   * @param tenantId the tenant ID
   * @param projectId the project ID
   * @return Promise of list of requirements
   */
  Promise<List<Requirement>> findByProject(String tenantId, String projectId);

  /**
   * Find requirements by status.
   *
   * @param tenantId the tenant ID
   * @param status the requirement status
   * @return Promise of list of requirements
   */
  Promise<List<Requirement>> findByStatus(String tenantId, RequirementStatus status);

  /**
   * Find requirements by type.
   *
   * @param tenantId the tenant ID
   * @param type the requirement type
   * @return Promise of list of requirements
   */
  Promise<List<Requirement>> findByType(String tenantId, RequirementType type);

  /**
   * Find requirements by priority.
   *
   * @param tenantId the tenant ID
   * @param priority the priority level
   * @return Promise of list of requirements
   */
  Promise<List<Requirement>> findByPriority(String tenantId, Priority priority);

  /**
   * Find requirements assigned to a user.
   *
   * @param tenantId the tenant ID
   * @param userId the user ID
   * @return Promise of list of requirements
   */
  Promise<List<Requirement>> findByAssignee(String tenantId, String userId);

  /**
   * Find requirements with low quality scores.
   *
   * @param tenantId the tenant ID
   * @param threshold the quality threshold (0.0 - 1.0)
   * @return Promise of list of requirements below threshold
   */
  Promise<List<Requirement>> findBelowQualityThreshold(String tenantId, double threshold);

  /**
   * Delete a requirement.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @return Promise completing when deleted
   */
  Promise<Void> delete(String tenantId, UUID id);

  /**
   * Count requirements by status.
   *
   * @param tenantId the tenant ID
   * @return Promise of map status -> count (funnel data)
   */
  Promise<java.util.Map<RequirementStatus, Long>> countByStatus(String tenantId);

  /**
   * Check if requirement exists.
   *
   * @param tenantId the tenant ID
   * @param id the requirement ID
   * @return Promise of boolean
   */
  Promise<Boolean> exists(String tenantId, UUID id);
}
