/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.AISuggestion;
import com.ghatana.yappc.api.domain.AISuggestion.Priority;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionStatus;
import com.ghatana.yappc.api.domain.AISuggestion.SuggestionType;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port interface for AI Suggestion persistence.
 *
 * <p><b>Purpose</b><br>
 * Defines contract for AI suggestion storage and retrieval. Supports the human-in-the-loop pattern
 * for AI assistance.
 *
 * <p><b>Key Queries</b><br>
 * - Pending suggestions (inbox) - Suggestions by confidence level - Suggestions by target resource
 * - Suggestion statistics for dashboard
 *
 * <p><b>Multi-Tenancy</b><br>
 * All operations are tenant-scoped for data isolation.
 *
 * @doc.type interface
 * @doc.purpose AI suggestion repository port
 * @doc.layer domain
 * @doc.pattern Port (Hexagonal Architecture)
 */
public interface AISuggestionRepository {

  /**
   * Save a suggestion (create or update).
   *
   * @param suggestion the suggestion to save
   * @return Promise of saved suggestion
   */
  Promise<AISuggestion> save(AISuggestion suggestion);

  /**
   * Find suggestion by ID.
   *
   * @param tenantId the tenant ID
   * @param id the suggestion ID
   * @return Promise of Optional suggestion
   */
  Promise<Optional<AISuggestion>> findById(String tenantId, UUID id);

  /**
   * Find all pending suggestions (inbox).
   *
   * @param tenantId the tenant ID
   * @return Promise of list of pending suggestions
   */
  Promise<List<AISuggestion>> findPending(String tenantId);

  /**
   * Find pending suggestions by project.
   *
   * @param tenantId the tenant ID
   * @param projectId the project ID
   * @return Promise of list of pending suggestions
   */
  Promise<List<AISuggestion>> findPendingByProject(String tenantId, String projectId);

  /**
   * Find suggestions by status.
   *
   * @param tenantId the tenant ID
   * @param status the suggestion status
   * @return Promise of list of suggestions
   */
  Promise<List<AISuggestion>> findByStatus(String tenantId, SuggestionStatus status);

  /**
   * Find suggestions by type.
   *
   * @param tenantId the tenant ID
   * @param type the suggestion type
   * @return Promise of list of suggestions
   */
  Promise<List<AISuggestion>> findByType(String tenantId, SuggestionType type);

  /**
   * Find suggestions by target resource.
   *
   * @param tenantId the tenant ID
   * @param resourceType the resource type (e.g., "REQUIREMENT")
   * @param resourceId the resource ID
   * @return Promise of list of suggestions for the resource
   */
  Promise<List<AISuggestion>> findByTargetResource(
      String tenantId, String resourceType, String resourceId);

  /**
   * Find high-confidence suggestions (>= threshold).
   *
   * @param tenantId the tenant ID
   * @param confidenceThreshold the minimum confidence (0.0 - 1.0)
   * @return Promise of list of high-confidence suggestions
   */
  Promise<List<AISuggestion>> findByMinConfidence(String tenantId, double confidenceThreshold);

  /**
   * Find suggestions by priority.
   *
   * @param tenantId the tenant ID
   * @param priority the priority level
   * @return Promise of list of suggestions
   */
  Promise<List<AISuggestion>> findByPriority(String tenantId, Priority priority);

  /**
   * Find suggestions created after a timestamp.
   *
   * @param tenantId the tenant ID
   * @param after the timestamp
   * @return Promise of list of suggestions
   */
  Promise<List<AISuggestion>> findCreatedAfter(String tenantId, Instant after);

  /**
   * Find suggestions reviewed by a user.
   *
   * @param tenantId the tenant ID
   * @param userId the reviewer user ID
   * @return Promise of list of suggestions
   */
  Promise<List<AISuggestion>> findReviewedBy(String tenantId, String userId);

  /**
   * Delete a suggestion.
   *
   * @param tenantId the tenant ID
   * @param id the suggestion ID
   * @return Promise completing when deleted
   */
  Promise<Void> delete(String tenantId, UUID id);

  /**
   * Count suggestions by status.
   *
   * @param tenantId the tenant ID
   * @return Promise of map status -> count
   */
  Promise<java.util.Map<SuggestionStatus, Long>> countByStatus(String tenantId);

  /**
   * Count pending suggestions by type.
   *
   * @param tenantId the tenant ID
   * @return Promise of map type -> count
   */
  Promise<java.util.Map<SuggestionType, Long>> countPendingByType(String tenantId);

  /**
   * Get acceptance rate (accepted / (accepted + rejected)).
   *
   * @param tenantId the tenant ID
   * @return Promise of acceptance rate (0.0 - 1.0)
   */
  Promise<Double> getAcceptanceRate(String tenantId);

  /**
   * Count suggestions requiring urgent review.
   *
   * @param tenantId the tenant ID
   * @return Promise of count of high-priority pending suggestions
   */
  Promise<Long> countUrgent(String tenantId);

  /**
   * Check if suggestion exists.
   *
   * @param tenantId the tenant ID
   * @param id the suggestion ID
   * @return Promise of boolean
   */
  Promise<Boolean> exists(String tenantId, UUID id);
}
