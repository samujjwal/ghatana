/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Domain Entities
 */
package com.ghatana.yappc.infrastructure.datacloud.entity;

import com.ghatana.products.yappc.domain.AggregateRoot;
import com.ghatana.products.yappc.domain.events.ProjectCompletedEvent;
import com.ghatana.products.yappc.domain.events.ProjectCreatedEvent;
import com.ghatana.products.yappc.domain.events.ProjectStageAdvancedEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Project Entity - Root aggregate for YAPPC projects in Data-Cloud.
 *
 * <p><b>Purpose</b><br>
 * Represents a YAPPC project with full lifecycle state, metadata, and
 * relationships to tasks and phases. This entity is persisted via
 * YappcDataCloudRepository to Data-Cloud.
 *
 * <p><b>Data Cloud Ownership</b><br>
 * Project state (metadata, configuration, current phase) is owned by Data-Cloud.
 * Events about project changes are published to Event Cloud (AEP).
 *
 * <p><b>Fields</b><br>
 * - Core: id, name, description, status<br>
 * - Lifecycle: currentStage, startedAt, completedAt<br>
 * - Configuration: settings, environmentVariables<br>
 * - Relationships: taskIds (references, not embedded)<br>
 *
 * @see TaskEntity
 * @see PhaseStateEntity
 * @see com.ghatana.yappc.infrastructure.datacloud.adapter.YappcDataCloudRepository
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 
 * @doc.type class
 * @doc.purpose Handles project entity operations
 * @doc.layer platform
 * @doc.pattern Aggregate Root / Domain-Driven Design
*/
public class ProjectEntity extends AggregateRoot<UUID> {

  private UUID id;
  private String name;
  private String description;
  private String status; // ACTIVE, PAUSED, COMPLETED, ARCHIVED

  // Lifecycle tracking
  private String currentStage; // intent, context, plan, execute, verify, observe, learn, institutionalize
  private Instant startedAt;
  private Instant completedAt;
  private Instant lastActivityAt;

  // Configuration
  private Map<String, Object> settings;
  private Map<String, String> environmentVariables;

  // Relationships (store IDs, not embedded objects)
  private java.util.List<UUID> taskIds;

  // Metadata
  private String createdBy;
  private String tenantId;
  private String version; // entity version for optimistic locking

  /**
   * Creates a new project entity.
   */
  public ProjectEntity() {
    this.id = UUID.randomUUID();
    this.status = "ACTIVE";
    this.currentStage = "intent";
    this.startedAt = Instant.now();
    this.lastActivityAt = Instant.now();
    this.taskIds = new java.util.ArrayList<>();
    this.settings = new java.util.HashMap<>();
    this.environmentVariables = new java.util.HashMap<>();
    this.version = "1";
  }

  /**
   * Creates a project with basic info and raises a {@link ProjectCreatedEvent}.
   *
   * @param name        project display name
   * @param description project description
   * @param createdBy   user or service initiating creation
   */
  @SuppressWarnings("this-escape") // Safe: raiseEvent() only appends to a final ArrayList in AggregateRoot
  public ProjectEntity(String name, String description, String createdBy) {
    this();
    this.name = name;
    this.description = description;
    this.createdBy = createdBy;
    raiseEvent(new ProjectCreatedEvent(this.id, this.tenantId, name, createdBy));
  }

  // Getters and setters

  @Override
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getCurrentStage() {
    return currentStage;
  }

  public void setCurrentStage(String currentStage) {
    this.currentStage = currentStage;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt;
  }

  public void setLastActivityAt(Instant lastActivityAt) {
    this.lastActivityAt = lastActivityAt;
  }

  public Map<String, Object> getSettings() {
    return settings;
  }

  public void setSettings(Map<String, Object> settings) {
    this.settings = settings;
  }

  public Map<String, String> getEnvironmentVariables() {
    return environmentVariables;
  }

  public void setEnvironmentVariables(Map<String, String> environmentVariables) {
    this.environmentVariables = environmentVariables;
  }

  public java.util.List<UUID> getTaskIds() {
    return taskIds;
  }

  public void setTaskIds(java.util.List<UUID> taskIds) {
    this.taskIds = taskIds;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Adds a task reference to this project.
   *
   * @param taskId the task ID to add
   */
  public void addTask(UUID taskId) {
    if (!taskIds.contains(taskId)) {
      taskIds.add(taskId);
    }
    touch();
  }

  /**
   * Removes a task reference from this project.
   *
   * @param taskId the task ID to remove
   */
  public void removeTask(UUID taskId) {
    taskIds.remove(taskId);
    touch();
  }

  /**
   * Updates the last activity timestamp.
   */
  public void touch() {
    this.lastActivityAt = Instant.now();
  }

  /**
   * Advances the project to the next lifecycle stage.
   *
   * @param newStage the stage to transition to
   * @return true if transition was valid
   */
  public boolean advanceStage(String newStage) {
    if (isValidStageTransition(this.currentStage, newStage)) {
      String previousStage = this.currentStage;
      this.currentStage = newStage;
      touch();

      raiseEvent(new ProjectStageAdvancedEvent(this.id, this.tenantId, previousStage, newStage));

      if ("institutionalize".equals(newStage)) {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
        raiseEvent(new ProjectCompletedEvent(this.id, this.tenantId, this.completedAt));
      }

      return true;
    }
    return false;
  }

  private boolean isValidStageTransition(String from, String to) {
    // Define valid transitions based on stages.yaml
    Map<String, java.util.List<String>> validTransitions = Map.of(
        "intent", java.util.List.of("context"),
        "context", java.util.List.of("plan", "intent"),
        "plan", java.util.List.of("execute", "context"),
        "execute", java.util.List.of("verify", "plan"),
        "verify", java.util.List.of("observe", "execute"),
        "observe", java.util.List.of("learn", "execute"),
        "learn", java.util.List.of("institutionalize", "observe"),
        "institutionalize", java.util.List.of() // Terminal stage
    );

    java.util.List<String> validNext = validTransitions.getOrDefault(from, java.util.List.of());
    return validNext.contains(to);
  }

  /**
   * Gets the collection name for Data-Cloud persistence.
   *
   * @return collection name
   */
  public static String getCollectionName() {
    return "projects";
  }
}
