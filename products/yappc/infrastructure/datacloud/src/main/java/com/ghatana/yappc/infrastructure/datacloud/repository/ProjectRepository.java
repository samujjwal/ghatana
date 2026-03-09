/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Repository Factories
 */
package com.ghatana.yappc.infrastructure.datacloud.repository;

import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository;
import com.ghatana.yappc.infrastructure.datacloud.entity.ProjectEntity;
import com.ghatana.yappc.infrastructure.datacloud.mapper.YappcEntityMapper;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Project Repository - Data-Cloud persistence for ProjectEntity.
 *
 * <p><b>Purpose</b><br>
 * Provides CRUD operations and query methods for YAPPC projects.
 * Wraps YappcDataCloudRepository with project-specific queries.
 *
 * <p><b>Query Methods</b><br>
 * - findByStatus: Active, paused, completed projects<br>
 * - findByStage: Projects in specific lifecycle stage<br>
 * - findByTenant: Projects for a tenant<br>
 *
 * @see ProjectEntity
 * @see YappcDataCloudRepository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type class
 * @doc.purpose Handles project repository operations
 * @doc.layer platform
 * @doc.pattern Repository
*/
public class ProjectRepository {

  private final YappcDataCloudRepository<ProjectEntity> delegate;
  private static final String COLLECTION = ProjectEntity.getCollectionName();

  public ProjectRepository(
      @NotNull EntityRepository entityRepository,
      @NotNull YappcEntityMapper mapper) {
    this.delegate = new YappcDataCloudRepository<>(
        entityRepository, mapper, COLLECTION, ProjectEntity.class);
  }

  // Basic CRUD

  @NotNull
  public Promise<ProjectEntity> save(@NotNull ProjectEntity project) {
    return delegate.save(project);
  }

  @NotNull
  public Promise<Optional<ProjectEntity>> findById(@NotNull UUID id) {
    return delegate.findById(id);
  }

  @NotNull
  public Promise<List<ProjectEntity>> findAll() {
    return delegate.findAll();
  }

  @NotNull
  public Promise<Void> deleteById(@NotNull UUID id) {
    return delegate.deleteById(id);
  }

  // Query Methods

  /**
   * Finds projects by status.
   *
   * @param status ACTIVE, PAUSED, COMPLETED, ARCHIVED
   * @return matching projects
   */
  @NotNull
  public Promise<List<ProjectEntity>> findByStatus(@NotNull String status) {
    return delegate.findByField("status", status);
  }

  /**
   * Finds projects in a specific lifecycle stage.
   *
   * @param stage intent, context, plan, execute, verify, observe, learn, institutionalize
   * @return matching projects
   */
  @NotNull
  public Promise<List<ProjectEntity>> findByStage(@NotNull String stage) {
    return delegate.findByField("currentStage", stage);
  }

  /**
   * Finds projects for a tenant.
   *
   * @param tenantId tenant identifier
   * @return matching projects
   */
  @NotNull
  public Promise<List<ProjectEntity>> findByTenant(@NotNull String tenantId) {
    return delegate.findByField("tenantId", tenantId);
  }

  /**
   * Finds active projects (not completed or archived).
   *
   * @return active projects
   */
  @NotNull
  public Promise<List<ProjectEntity>> findActive() {
    Map<String, Object> filter = Map.of(
        "status", "ACTIVE"
    );
    return delegate.findByFilter(filter, "lastActivityAt DESC", 1000, 0);
  }

  /**
   * Finds projects created by a specific user.
   *
   * @param createdBy user identifier
   * @return matching projects
   */
  @NotNull
  public Promise<List<ProjectEntity>> findByCreatedBy(@NotNull String createdBy) {
    return delegate.findByField("createdBy", createdBy);
  }

  /**
   * Finds projects with recent activity.
   *
   * @param since timestamp for last activity
   * @return recently active projects
   */
  @NotNull
  public Promise<List<ProjectEntity>> findRecentlyActive(@NotNull java.time.Instant since) {
    Map<String, Object> filter = Map.of(
        "lastActivityAt", Map.of("$gte", since.toString())
    );
    return delegate.findByFilter(filter, "lastActivityAt DESC", 100, 0);
  }

  // Factory

  /**
   * Creates a ProjectRepository with the given dependencies.
   *
   * @param entityRepository the data-cloud entity repository
   * @param mapper the entity mapper
   * @return new repository instance
   */
  @NotNull
  public static ProjectRepository create(
      @NotNull EntityRepository entityRepository,
      @NotNull YappcEntityMapper mapper) {
    return new ProjectRepository(entityRepository, mapper);
  }
}
