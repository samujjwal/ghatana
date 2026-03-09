/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Repository Factories
 */
package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.entity.TaskEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Task Repository - Data-Cloud persistence for TaskEntity.
 *
 * <p><b>Purpose</b><br>
 * Provides CRUD operations and query methods for YAPPC tasks.
 * Supports filtering by status, stage, assigned agent, and project.
 *
 * <p><b>Query Methods</b><br>
 * - findByStatus: PENDING, IN_PROGRESS, COMPLETED, FAILED tasks<br>
 * - findByStage: Tasks for specific lifecycle stage<br>
 * - findByAssignedAgent: Tasks assigned to specific agent<br>
 * - findByProject: Tasks belonging to a project<br>
 * - findPendingForAgent: Tasks pending assignment by capability<br>
 *
 * @see TaskEntity
 * @see YappcDataCloudRepository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type class
 * @doc.purpose Handles task repository operations
 * @doc.layer platform
 * @doc.pattern Repository
*/
public class TaskRepository {

  private final YappcDataCloudRepository<TaskEntity> delegate;
  private static final String COLLECTION = TaskEntity.getCollectionName();

  public TaskRepository(
      @NotNull EntityRepository entityRepository,
      @NotNull YappcEntityMapper mapper) {
    this.delegate = new YappcDataCloudRepository<>(
        entityRepository, mapper, COLLECTION, TaskEntity.class);
  }

  // Basic CRUD

  @NotNull
  public Promise<TaskEntity> save(@NotNull TaskEntity task) {
    return delegate.save(task);
  }

  @NotNull
  public Promise<Optional<TaskEntity>> findById(@NotNull UUID id) {
    return delegate.findById(id);
  }

  @NotNull
  public Promise<List<TaskEntity>> findAll() {
    return delegate.findAll();
  }

  @NotNull
  public Promise<Void> deleteById(@NotNull UUID id) {
    return delegate.deleteById(id);
  }

  // Query Methods

  /**
   * Finds tasks by status.
   *
   * @param status PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
   * @return matching tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findByStatus(@NotNull String status) {
    return delegate.findByField("status", status);
  }

  /**
   * Finds tasks in a specific lifecycle stage.
   *
   * @param stage intent, context, plan, execute, verify, observe, learn, institutionalize
   * @return matching tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findByStage(@NotNull String stage) {
    return delegate.findByField("stage", stage);
  }

  /**
   * Finds tasks assigned to a specific agent.
   *
   * @param agentId agent identifier (e.g., "agent.yappc.java-expert")
   * @return matching tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findByAssignedAgent(@NotNull String agentId) {
    return delegate.findByField("assignedAgentId", agentId);
  }

  /**
   * Finds tasks for a specific project.
   *
   * @param projectId project identifier
   * @return matching tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findByProject(@NotNull UUID projectId) {
    return delegate.findByField("projectId", projectId);
  }

  /**
   * Finds pending tasks that require a specific capability.
   *
   * @param capability capability ID (e.g., "code-generation")
   * @return matching tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findPendingByCapability(@NotNull String capability) {
    // This is a simplified version - in production would need array contains query
    Map<String, Object> filter = Map.of(
        "status", "PENDING"
    );
    return delegate.findByFilter(filter, "priority DESC, createdAt ASC", 100, 0);
  }

  /**
   * Finds tasks by priority.
   *
   * @param priority LOW, MEDIUM, HIGH, CRITICAL
   * @return matching tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findByPriority(@NotNull String priority) {
    return delegate.findByField("priority", priority);
  }

  /**
   * Finds incomplete tasks (PENDING, ASSIGNED, IN_PROGRESS) for a project.
   *
   * @param projectId project identifier
   * @return incomplete tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findIncompleteByProject(@NotNull UUID projectId) {
    Map<String, Object> filter = Map.of(
        "projectId", projectId,
        "status", Map.of("$nin", List.of("COMPLETED", "FAILED", "CANCELLED"))
    );
    return delegate.findByFilter(filter, "priority DESC, createdAt ASC", 1000, 0);
  }

  /**
   * Finds failed tasks that can be retried.
   *
   * @return retryable tasks
   */
  @NotNull
  public Promise<List<TaskEntity>> findRetryable() {
    Map<String, Object> filter = Map.of(
        "status", "FAILED"
    );
    // Would need to filter where retryCount < maxRetries in production
    return delegate.findByFilter(filter, "retryCount ASC", 100, 0);
  }

  /**
   * Finds tasks with deadlines approaching.
   *
   * @param before deadline threshold
   * @return tasks due before threshold
   */
  @NotNull
  public Promise<List<TaskEntity>> findDueBefore(@NotNull java.time.Instant before) {
    Map<String, Object> filter = Map.of(
        "deadlineAt", Map.of("$lte", before.toString()),
        "status", Map.of("$nin", List.of("COMPLETED", "CANCELLED"))
    );
    return delegate.findByFilter(filter, "deadlineAt ASC", 100, 0);
  }

  // Factory

  /**
   * Creates a TaskRepository with the given dependencies.
   *
   * @param entityRepository the data-cloud entity repository
   * @param mapper the entity mapper
   * @return new repository instance
   */
  @NotNull
  public static TaskRepository create(
      @NotNull EntityRepository entityRepository,
      @NotNull YappcEntityMapper mapper) {
    return new TaskRepository(entityRepository, mapper);
  }
}
