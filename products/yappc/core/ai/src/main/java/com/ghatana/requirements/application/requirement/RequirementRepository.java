package com.ghatana.requirements.application.requirement;

import com.ghatana.requirements.domain.requirement.Requirement;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Optional;

/**
 * Requirement persistence abstraction.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for requirement storage and retrieval with version history.
 * Implementation will use core/database abstractions with PostgreSQL backend.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RequirementRepository repo = new PostgreSQLRequirementRepository(dataSource);
 * 
 * // Save requirement with versions
 * Requirement saved = repo.save(requirement).getResult();
 * 
 * // Find requirement
 * Optional<Requirement> found = repo.findById(requirementId).getResult();
 * 
 * // List project requirements
 * List<Requirement> reqs = repo.findByProjectId(projectId).getResult();
 * 
 * // Find requirements by status
 * List<Requirement> pending = repo.findByStatus(RequirementStatus.PENDING_REVIEW)
 *     .getResult();
 * }</pre>
 *
 * <p><b>Version Management</b><br>
 * Repository persists complete version history. Retrieving a requirement returns
 * the aggregate with all versions loaded, enabling version history queries
 * and revert operations.
 *
 * <p><b>Integration Points</b><br>
 * - Uses core/database abstractions
 * - Returns ActiveJ Promise for async operations
 * - Persists version history in dedicated table
 *
 * @doc.type interface
 * @doc.purpose Requirement persistence contract
 * @doc.layer product
 * @doc.pattern Port, Repository
 */
public interface RequirementRepository {

  /**
   * Save or update requirement with all versions.
   *
   * @param requirement requirement to save
   * @return promise completing with saved requirement
   * @throws IllegalArgumentException if requirement is invalid
   */
  Promise<Requirement> save(Requirement requirement);

  /**
   * Find requirement by ID with version history.
   *
   * @param requirementId requirement identifier
   * @return promise completing with optional requirement (with versions loaded)
   */
  Promise<Optional<Requirement>> findById(String requirementId);

  /**
   * Find all requirements in project.
   *
   * @param projectId project identifier
   * @return promise completing with list of requirements
   */
  Promise<List<Requirement>> findByProjectId(String projectId);

  /**
   * Find all requirements with given status.
   *
   * @param statusName requirement status name
   * @return promise completing with list of requirements
   */
  Promise<List<Requirement>> findByStatus(String statusName);

  /**
   * Find requirements created by user.
   *
   * @param userId creator user identifier
   * @return promise completing with list of requirements
   */
  Promise<List<Requirement>> findByCreatedBy(String userId);

  /**
   * Find requirements assigned to user.
   *
   * @param userId assigned user identifier
   * @return promise completing with list of requirements
   */
  Promise<List<Requirement>> findByAssignedTo(String userId);

  /**
   * Find requirements by priority.
   *
   * @param priorityName requirement priority name
   * @return promise completing with list of requirements
   */
  Promise<List<Requirement>> findByPriority(String priorityName);

  /**
   * Find requirements by type.
   *
   * @param typeName requirement type name
   * @return promise completing with list of requirements
   */
  Promise<List<Requirement>> findByType(String typeName);

  /**
   * Find requirements by tag in metadata.
   *
   * @param tag tag to search for
   * @return promise completing with list of requirements containing tag
   */
  Promise<List<Requirement>> findByTag(String tag);

  /**
   * Check if requirement exists.
   *
   * @param requirementId requirement identifier
   * @return promise completing with existence check
   */
  Promise<Boolean> exists(String requirementId);

  /**
   * Delete requirement.
   *
   * @param requirementId requirement identifier
   * @return promise completing when deleted
   * @throws IllegalArgumentException if requirement not found
   */
  Promise<Void> delete(String requirementId);

  /**
   * Count requirements in project.
   *
   * @param projectId project identifier
   * @return promise completing with requirement count
   */
  Promise<Long> countByProjectId(String projectId);

  /**
   * Get specific version of requirement.
   *
   * @param requirementId requirement identifier
   * @param versionNumber version number
   * @return promise completing with optional requirement at that version
   */
  Promise<Optional<Requirement>> findVersion(String requirementId, int versionNumber);

  /**
   * Search requirements by title or description (full-text).
   *
   * @param query search query
   * @param projectId optional project filter
   * @return promise completing with matching requirements
   */
  Promise<List<Requirement>> search(String query, String projectId);
}