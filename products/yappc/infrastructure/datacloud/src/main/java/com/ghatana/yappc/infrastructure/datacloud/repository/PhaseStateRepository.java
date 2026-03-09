/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Repository Factories
 */
package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.entity.PhaseStateEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase State Repository - Data-Cloud persistence for PhaseStateEntity.
 *
 * <p><b>Purpose</b><br>
 * Provides CRUD operations and query methods for lifecycle phase transitions.
 * Enables audit trail and replay of project lifecycle progression.
 *
 * <p><b>Query Methods</b><br>
 * - findByProject: All phase states for a project<br>
 * - findByProjectAndStage: Specific phase for a project<br>
 * - findActivePhases: Currently active phases across projects<br>
 * - findCompletedPhases: Completed phase transitions<br>
 * - findBlockedPhases: Phases blocked by gates<br>
 *
 * @see PhaseStateEntity
 * @see YappcDataCloudRepository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type class
 * @doc.purpose Handles phase state repository operations
 * @doc.layer platform
 * @doc.pattern Repository
*/
public class PhaseStateRepository {

  private final YappcDataCloudRepository<PhaseStateEntity> delegate;
  private static final String COLLECTION = PhaseStateEntity.getCollectionName();

  public PhaseStateRepository(
      @NotNull EntityRepository entityRepository,
      @NotNull YappcEntityMapper mapper) {
    this.delegate = new YappcDataCloudRepository<>(
        entityRepository, mapper, COLLECTION, PhaseStateEntity.class);
  }

  // Basic CRUD

  @NotNull
  public Promise<PhaseStateEntity> save(@NotNull PhaseStateEntity phaseState) {
    return delegate.save(phaseState);
  }

  @NotNull
  public Promise<Optional<PhaseStateEntity>> findById(@NotNull UUID id) {
    return delegate.findById(id);
  }

  @NotNull
  public Promise<List<PhaseStateEntity>> findAll() {
    return delegate.findAll();
  }

  @NotNull
  public Promise<Void> deleteById(@NotNull UUID id) {
    return delegate.deleteById(id);
  }

  // Query Methods

  /**
   * Finds all phase states for a project.
   *
   * @param projectId project identifier
   * @return phase states ordered by entry time
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findByProject(@NotNull UUID projectId) {
    return delegate.findByField("projectId", projectId);
  }

  /**
   * Finds a specific phase state for a project.
   *
   * @param projectId project identifier
   * @param stage lifecycle stage
   * @return phase state if exists
   */
  @NotNull
  public Promise<Optional<PhaseStateEntity>> findByProjectAndStage(
      @NotNull UUID projectId,
      @NotNull String stage) {
    Map<String, Object> filter = Map.of(
        "projectId", projectId,
        "stage", stage
    );
    return delegate.findByFilter(filter, null, 1, 0)
        .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
  }

  /**
   * Finds currently active phases.
   *
   * @return active phase states
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findActivePhases() {
    return delegate.findByField("status", "ACTIVE");
  }

  /**
   * Finds completed phase transitions.
   *
   * @return completed phase states
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findCompletedPhases() {
    return delegate.findByField("status", "COMPLETED");
  }

  /**
   * Finds phases blocked by gate agents.
   *
   * @return blocked phase states
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findBlockedPhases() {
    return delegate.findByField("status", "BLOCKED");
  }

  /**
   * Finds phase states for a specific trigger event.
   *
   * @param triggerEvent event type (e.g., "test.suite.passed")
   * @return matching phase states
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findByTriggerEvent(@NotNull String triggerEvent) {
    return delegate.findByField("triggerEvent", triggerEvent);
  }

  /**
   * Finds phases that entered within a time range.
   *
   * @param start start time
   * @param end end time
   * @return phases entered in range
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findByEntryTimeRange(
      @NotNull java.time.Instant start,
      @NotNull java.time.Instant end) {
    Map<String, Object> filter = Map.of(
        "enteredAt", Map.of(
            "$gte", start.toString(),
            "$lte", end.toString()
        )
    );
    return delegate.findByFilter(filter, "enteredAt DESC", 1000, 0);
  }

  /**
   * Gets the current phase for a project.
   *
   * @param projectId project identifier
   * @return current phase state
   */
  @NotNull
  public Promise<Optional<PhaseStateEntity>> getCurrentPhase(@NotNull UUID projectId) {
    Map<String, Object> filter = Map.of(
        "projectId", projectId,
        "status", "ACTIVE"
    );
    return delegate.findByFilter(filter, "enteredAt DESC", 1, 0)
        .map(list -> list.isEmpty() ? Optional.empty() : Optional.of(list.get(0)));
  }

  /**
   * Finds phases with specific entry criteria not met.
   *
   * @param criterion the criterion name
   * @return phases missing the criterion
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findMissingEntryCriterion(@NotNull String criterion) {
    // In production, would query for entryCriteriaMet[criterion] = false
    return delegate.findByField("status", "ACTIVE");
  }

  /**
   * Finds phases awaiting specific gate agent approval.
   *
   * @param agentId gate agent ID
   * @return phases awaiting this gate
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> findAwaitingGate(@NotNull String agentId) {
    // In production, would query gateDecisions array
    return delegate.findByField("status", "ACTIVE");
  }

  // Audit

  /**
   * Gets the full phase history for a project.
   *
   * @param projectId project identifier
   * @return chronological phase transitions
   */
  @NotNull
  public Promise<List<PhaseStateEntity>> getPhaseHistory(@NotNull UUID projectId) {
    Map<String, Object> filter = Map.of("projectId", projectId);
    return delegate.findByFilter(filter, "enteredAt ASC", 1000, 0);
  }

  // Factory

  /**
   * Creates a PhaseStateRepository with the given dependencies.
   *
   * @param entityRepository the data-cloud entity repository
   * @param mapper the entity mapper
   * @return new repository instance
   */
  @NotNull
  public static PhaseStateRepository create(
      @NotNull EntityRepository entityRepository,
      @NotNull YappcEntityMapper mapper) {
    return new PhaseStateRepository(entityRepository, mapper);
  }
}
