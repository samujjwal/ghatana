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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of AISuggestionRepository for development/testing.
 *
 * <p><b>Purpose</b><br>
 * Provides fast, in-memory AI suggestion storage without database dependency. Thread-safe using
 * ConcurrentHashMap for concurrent access.
 *
 * <p><b>Limitations</b><br>
 * - Data lost on application restart - Not suitable for production use - No pagination support
 *
 * @doc.type class
 * @doc.purpose In-memory AI suggestion repository (test implementation)
 * @doc.layer infrastructure
 * @doc.pattern Adapter (Hexagonal Architecture)
 */
public class InMemoryAISuggestionRepository implements AISuggestionRepository {

  private static final Logger logger =
      LoggerFactory.getLogger(InMemoryAISuggestionRepository.class);

  // Storage: tenantId -> (suggestionId -> AISuggestion)
  private final Map<String, Map<UUID, AISuggestion>> store = new ConcurrentHashMap<>();

  @Override
  public Promise<AISuggestion> save(AISuggestion suggestion) {
    Objects.requireNonNull(suggestion, "Suggestion must not be null");
    Objects.requireNonNull(suggestion.getTenantId(), "Tenant ID must not be null");

    String tenantId = suggestion.getTenantId();
    store
        .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
        .put(suggestion.getId(), suggestion);

    logger.debug("Saved AI suggestion {} for tenant {}", suggestion.getId(), tenantId);
    return Promise.of(suggestion);
  }

  @Override
  public Promise<Optional<AISuggestion>> findById(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Suggestion ID must not be null");

    Map<UUID, AISuggestion> tenantStore = store.get(tenantId);
    if (tenantStore == null) {
      return Promise.of(Optional.empty());
    }

    return Promise.of(Optional.ofNullable(tenantStore.get(id)));
  }

  private Promise<List<AISuggestion>> findAllByTenant(String tenantId) {
    Map<UUID, AISuggestion> tenantStore = store.get(tenantId);
    if (tenantStore == null) {
      return Promise.of(Collections.emptyList());
    }
    return Promise.of(new ArrayList<>(tenantStore.values()));
  }

  @Override
  public Promise<List<AISuggestion>> findPending(String tenantId) {
    return findByStatus(tenantId, SuggestionStatus.PENDING);
  }

  @Override
  public Promise<List<AISuggestion>> findPendingByProject(String tenantId, String projectId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(projectId, "Project ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> SuggestionStatus.PENDING == s.getStatus())
                    .filter(s -> projectId.equals(s.getProjectId()))
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findByStatus(String tenantId, SuggestionStatus status) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(status, "Status must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> status == s.getStatus())
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findByType(String tenantId, SuggestionType type) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(type, "Type must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream().filter(s -> type == s.getType()).collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findByTargetResource(
      String tenantId, String resourceType, String resourceId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(resourceType, "Resource type must not be null");
    Objects.requireNonNull(resourceId, "Resource ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> resourceType.equals(s.getTargetResourceType()))
                    .filter(s -> resourceId.equals(s.getTargetResourceId()))
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findByMinConfidence(
      String tenantId, double confidenceThreshold) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    if (confidenceThreshold < 0.0 || confidenceThreshold > 1.0) {
      return Promise.ofException(
          new IllegalArgumentException("Confidence must be between 0.0 and 1.0"));
    }

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> s.getConfidence() >= confidenceThreshold)
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findByPriority(String tenantId, Priority priority) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(priority, "Priority must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> priority == s.getPriority())
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findCreatedAfter(String tenantId, Instant after) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(after, "After timestamp must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> s.getCreatedAt().isAfter(after))
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<AISuggestion>> findReviewedBy(String tenantId, String userId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(userId, "User ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> userId.equals(s.getReviewedBy()))
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<Void> delete(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Suggestion ID must not be null");

    Map<UUID, AISuggestion> tenantStore = store.get(tenantId);
    if (tenantStore != null) {
      tenantStore.remove(id);
      logger.debug("Deleted AI suggestion {} from tenant {}", id, tenantId);
    }

    return Promise.complete();
  }

  @Override
  public Promise<Map<SuggestionStatus, Long>> countByStatus(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .collect(
                        Collectors.groupingBy(AISuggestion::getStatus, Collectors.counting())));
  }

  @Override
  public Promise<Map<SuggestionType, Long>> countPendingByType(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> SuggestionStatus.PENDING == s.getStatus())
                    .collect(Collectors.groupingBy(AISuggestion::getType, Collectors.counting())));
  }

  @Override
  public Promise<Double> getAcceptanceRate(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    return countByStatus(tenantId)
        .map(
            counts -> {
              long accepted =
                  counts.getOrDefault(SuggestionStatus.ACCEPTED, 0L)
                      + counts.getOrDefault(SuggestionStatus.APPLIED, 0L);
              long rejected = counts.getOrDefault(SuggestionStatus.REJECTED, 0L);
              long total = accepted + rejected;

              if (total == 0) {
                return 0.0;
              }
              return (double) accepted / total;
            });
  }

  @Override
  public Promise<Long> countUrgent(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            suggestions ->
                suggestions.stream()
                    .filter(s -> SuggestionStatus.PENDING == s.getStatus())
                    .filter(
                        s ->
                            Priority.CRITICAL == s.getPriority()
                                || Priority.HIGH == s.getPriority())
                    .count());
  }

  @Override
  public Promise<Boolean> exists(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Suggestion ID must not be null");

    Map<UUID, AISuggestion> tenantStore = store.get(tenantId);
    return Promise.of(tenantStore != null && tenantStore.containsKey(id));
  }

  // ========== Development Helpers ==========

  /** Clear all data (for testing). */
  public void clear() {
    store.clear();
    logger.info("Cleared all AI suggestions from in-memory store");
  }

  /** Clear data for a specific tenant (for testing). */
  public void clearTenant(String tenantId) {
    store.remove(tenantId);
    logger.info("Cleared AI suggestions for tenant {}", tenantId);
  }

  /** Get total count across all tenants (for testing/debugging). */
  public int totalCount() {
    return store.values().stream().mapToInt(Map::size).sum();
  }
}
