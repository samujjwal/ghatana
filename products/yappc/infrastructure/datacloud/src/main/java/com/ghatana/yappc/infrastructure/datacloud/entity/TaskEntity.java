/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Domain Entities
 */
package com.ghatana.yappc.infrastructure.datacloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Task Entity - Represents a unit of work within a YAPPC project.
 *
 * <p><b>Purpose</b><br>
 * Tracks tasks through the lifecycle stages, linking work items to
 * agents, capabilities, and execution state. Tasks are persisted in
 * Data-Cloud and events are published to Event Cloud on state changes.
 *
 * <p><b>Data Cloud Ownership</b><br>
 * Task state (status, results, assignments) is owned by Data-Cloud.
 * Task events (created, started, completed, failed) are published to Event Cloud.
 *
 * <p><b>Task Lifecycle</b><br>
 * PENDING → ASSIGNED → IN_PROGRESS → COMPLETED/FAILED/CANCELLED
 *
 * <p><b>Fields</b><br>
 * - Core: id, projectId, title, description<br>
 * - Assignment: assignedAgentId, requiredCapabilities<br>
 * - Execution: status, priority, startedAt, completedAt<br>
 * - Results: resultSummary, resultData, errorMessage<br>
 *
 * @see ProjectEntity
 * @see PhaseStateEntity
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * @doc.type class
 * @doc.purpose Handles task entity operations
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class TaskEntity {

  // Core identification
  private UUID id;
  private UUID projectId;
  private String title;
  private String description;

  // Assignment
  private String assignedAgentId; // e.g., "agent.yappc.java-expert"
  private java.util.List<String> requiredCapabilities;

  // Execution state
  private String status; // PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED
  private String priority; // LOW, MEDIUM, HIGH, CRITICAL
  private String stage; // intent, context, plan, execute, verify, observe, learn, institutionalize

  // Timestamps
  private Instant createdAt;
  private Instant startedAt;
  private Instant completedAt;
  private Instant deadlineAt;
  private Instant lastUpdatedAt;

  // Results
  private String resultSummary;
  private Map<String, Object> resultData;
  private String errorMessage;
  private Integer retryCount;
  private Integer maxRetries;

  // Metadata
  private String createdBy;
  private String tenantId;
  private Map<String, String> labels;
  private String version;

  // AEP integration
  private String eventCorrelationId; // Links to Event Cloud events
  private String checkpointId; // Links to agent-framework checkpoint

  /**
   * Creates a new task entity.
   */
  public TaskEntity() {
    this.id = UUID.randomUUID();
    this.status = "PENDING";
    this.priority = "MEDIUM";
    this.stage = "intent";
    this.createdAt = Instant.now();
    this.lastUpdatedAt = Instant.now();
    this.requiredCapabilities = new java.util.ArrayList<>();
    this.labels = new java.util.HashMap<>();
    this.resultData = new java.util.HashMap<>();
    this.retryCount = 0;
    this.maxRetries = 3;
    this.version = "1";
  }

  /**
   * Creates a task for a specific project.
   */
  public TaskEntity(UUID projectId, String title, String description, String stage) {
    this();
    this.projectId = projectId;
    this.title = title;
    this.description = description;
    this.stage = stage;
  }

  // Getters and setters

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public void setProjectId(UUID projectId) {
    this.projectId = projectId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getAssignedAgentId() {
    return assignedAgentId;
  }

  public void setAssignedAgentId(String assignedAgentId) {
    this.assignedAgentId = assignedAgentId;
  }

  public java.util.List<String> getRequiredCapabilities() {
    return requiredCapabilities;
  }

  public void setRequiredCapabilities(java.util.List<String> requiredCapabilities) {
    this.requiredCapabilities = requiredCapabilities;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
    this.lastUpdatedAt = Instant.now();
  }

  public String getPriority() {
    return priority;
  }

  public void setPriority(String priority) {
    this.priority = priority;
  }

  public String getStage() {
    return stage;
  }

  public void setStage(String stage) {
    this.stage = stage;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
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

  public Instant getDeadlineAt() {
    return deadlineAt;
  }

  public void setDeadlineAt(Instant deadlineAt) {
    this.deadlineAt = deadlineAt;
  }

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(Instant lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public String getResultSummary() {
    return resultSummary;
  }

  public void setResultSummary(String resultSummary) {
    this.resultSummary = resultSummary;
  }

  public Map<String, Object> getResultData() {
    return resultData;
  }

  public void setResultData(Map<String, Object> resultData) {
    this.resultData = resultData;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public Integer getRetryCount() {
    return retryCount;
  }

  public void setRetryCount(Integer retryCount) {
    this.retryCount = retryCount;
  }

  public Integer getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(Integer maxRetries) {
    this.maxRetries = maxRetries;
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

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getEventCorrelationId() {
    return eventCorrelationId;
  }

  public void setEventCorrelationId(String eventCorrelationId) {
    this.eventCorrelationId = eventCorrelationId;
  }

  public String getCheckpointId() {
    return checkpointId;
  }

  public void setCheckpointId(String checkpointId) {
    this.checkpointId = checkpointId;
  }

  /**
   * Adds a required capability for this task.
   *
   * @param capability the capability ID (e.g., "code-generation")
   * @return this task for chaining
   */
  public TaskEntity requiresCapability(String capability) {
    if (!requiredCapabilities.contains(capability)) {
      requiredCapabilities.add(capability);
    }
    return this;
  }

  /**
   * Adds a label to this task.
   *
   * @param key label key
   * @param value label value
   * @return this task for chaining
   */
  public TaskEntity withLabel(String key, String value) {
    this.labels.put(key, value);
    return this;
  }

  /**
   * Marks the task as started.
   *
   * @return true if state change was valid
   */
  public boolean start() {
    if ("PENDING".equals(status) || "ASSIGNED".equals(status)) {
      this.status = "IN_PROGRESS";
      this.startedAt = Instant.now();
      this.lastUpdatedAt = Instant.now();
      return true;
    }
    return false;
  }

  /**
   * Marks the task as completed with results.
   *
   * @param summary result summary
   * @param data detailed result data
   * @return true if state change was valid
   */
  public boolean complete(String summary, Map<String, Object> data) {
    if ("IN_PROGRESS".equals(status)) {
      this.status = "COMPLETED";
      this.resultSummary = summary;
      this.resultData = data != null ? data : new java.util.HashMap<>();
      this.completedAt = Instant.now();
      this.lastUpdatedAt = Instant.now();
      return true;
    }
    return false;
  }

  /**
   * Marks the task as failed with error.
   *
   * @param errorMessage the error description
   * @return true if retry is possible
   */
  public boolean fail(String errorMessage) {
    this.errorMessage = errorMessage;
    this.retryCount++;
    this.lastUpdatedAt = Instant.now();

    if (retryCount < maxRetries) {
      this.status = "PENDING"; // Retry
      return true;
    } else {
      this.status = "FAILED";
      this.completedAt = Instant.now();
      return false;
    }
  }

  /**
   * Checks if the task can be retried.
   *
   * @return true if retries remain
   */
  public boolean canRetry() {
    return retryCount < maxRetries;
  }

  /**
   * Gets the duration of task execution in seconds.
   *
   * @return duration, or -1 if not completed
   */
  public long getDurationSeconds() {
    if (startedAt == null) {
      return -1;
    }
    Instant end = completedAt != null ? completedAt : Instant.now();
    return java.time.Duration.between(startedAt, end).getSeconds();
  }

  /**
   * Gets the collection name for Data-Cloud persistence.
   *
   * @return collection name
   */
  public static String getCollectionName() {
    return "tasks";
  }
}
