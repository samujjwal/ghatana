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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of RequirementRepository for development/testing.
 *
 * <p><b>Purpose</b><br>
 * Provides fast, in-memory requirement storage without database dependency. Thread-safe using
 * ConcurrentHashMap for concurrent access.
 *
 * <p><b>Limitations</b><br>
 * - Data lost on application restart - Not suitable for production use - No pagination support (all
 * queries return full results)
 *
 * @doc.type class
 * @doc.purpose In-memory requirement repository (test implementation)
 * @doc.layer infrastructure
 * @doc.pattern Adapter (Hexagonal Architecture)
 */
public class InMemoryRequirementRepository implements RequirementRepository {

  private static final Logger logger = LoggerFactory.getLogger(InMemoryRequirementRepository.class);

  // Storage: tenantId -> (requirementId -> Requirement)
  private final Map<String, Map<UUID, Requirement>> store = new ConcurrentHashMap<>();

  @Override
  public Promise<Requirement> save(Requirement requirement) {
    Objects.requireNonNull(requirement, "Requirement must not be null");
    Objects.requireNonNull(requirement.getTenantId(), "Tenant ID must not be null");

    String tenantId = requirement.getTenantId();
    store
        .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>())
        .put(requirement.getId(), requirement);

    logger.debug("Saved requirement {} for tenant {}", requirement.getId(), tenantId);
    return Promise.of(requirement);
  }

  @Override
  public Promise<Optional<Requirement>> findById(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");

    Map<UUID, Requirement> tenantStore = store.get(tenantId);
    if (tenantStore == null) {
      return Promise.of(Optional.empty());
    }

    return Promise.of(Optional.ofNullable(tenantStore.get(id)));
  }

  @Override
  public Promise<List<Requirement>> findAllByTenant(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    Map<UUID, Requirement> tenantStore = store.get(tenantId);
    if (tenantStore == null) {
      return Promise.of(Collections.emptyList());
    }

    return Promise.of(new ArrayList<>(tenantStore.values()));
  }

  @Override
  public Promise<List<Requirement>> findByProject(String tenantId, String projectId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(projectId, "Project ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .filter(r -> projectId.equals(r.getProjectId()))
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<Requirement>> findByStatus(String tenantId, RequirementStatus status) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(status, "Status must not be null");

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .filter(r -> status == r.getStatus())
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<Requirement>> findByType(String tenantId, RequirementType type) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(type, "Type must not be null");

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .filter(r -> type == r.getType())
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<Requirement>> findByPriority(String tenantId, Priority priority) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(priority, "Priority must not be null");

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .filter(r -> priority == r.getPriority())
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<Requirement>> findByAssignee(String tenantId, String userId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(userId, "User ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .filter(r -> userId.equals(r.getAssignedTo()))
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<List<Requirement>> findBelowQualityThreshold(String tenantId, double threshold) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    if (threshold < 0.0 || threshold > 1.0) {
      return Promise.ofException(
          new IllegalArgumentException("Threshold must be between 0.0 and 1.0"));
    }

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .filter(
                        r -> {
                          Requirement.QualityMetrics qm = r.getQualityMetrics();
                          return qm.getTestabilityScore() < threshold
                              || qm.getCompletenessScore() < threshold
                              || qm.getClarityScore() < threshold;
                        })
                    .collect(Collectors.toList()));
  }

  @Override
  public Promise<Void> delete(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");

    Map<UUID, Requirement> tenantStore = store.get(tenantId);
    if (tenantStore != null) {
      tenantStore.remove(id);
      logger.debug("Deleted requirement {} from tenant {}", id, tenantId);
    }

    return Promise.complete();
  }

  @Override
  public Promise<Map<RequirementStatus, Long>> countByStatus(String tenantId) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");

    return findAllByTenant(tenantId)
        .map(
            requirements ->
                requirements.stream()
                    .collect(Collectors.groupingBy(Requirement::getStatus, Collectors.counting())));
  }

  @Override
  public Promise<Boolean> exists(String tenantId, UUID id) {
    Objects.requireNonNull(tenantId, "Tenant ID must not be null");
    Objects.requireNonNull(id, "Requirement ID must not be null");

    Map<UUID, Requirement> tenantStore = store.get(tenantId);
    return Promise.of(tenantStore != null && tenantStore.containsKey(id));
  }

  // ========== Development Helpers ==========

  /** Clear all data (for testing). */
  public void clear() {
    store.clear();
    logger.info("Cleared all requirements from in-memory store");
  }

  /** Clear data for a specific tenant (for testing). */
  public void clearTenant(String tenantId) {
    store.remove(tenantId);
    logger.info("Cleared requirements for tenant {}", tenantId);
  }

  /** Get total count across all tenants (for testing/debugging). */
  public int totalCount() {
    return store.values().stream().mapToInt(Map::size).sum();
  }
}
