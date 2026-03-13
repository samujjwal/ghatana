/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Infrastructure - Data-Cloud Domain Entities
 */
package com.ghatana.yappc.infrastructure.datacloud.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ghatana.products.yappc.domain.Identifiable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase State Entity - Captures the state of a lifecycle phase transition.
 *
 * <p><b>Purpose</b><br>
 * Tracks phase transitions for projects, including entry/exit criteria,
 * gate agent decisions, required artifacts, and transition events. This
 * enables replay and audit of the full lifecycle progression.
 *
 * <p><b>Data Cloud Ownership</b><br>
 * Phase state (criteria, artifacts, gate results) is owned by Data-Cloud.
 * Phase transition events are published to Event Cloud (AEP) for reactive
 * agent dispatch.
 *
 * <p><b>Lifecycle Mapping</b><br>
 * Maps to canonical stages from stages.yaml:
 * intent → context → plan → execute → verify → observe → learn → institutionalize
 *
 * <p><b>Fields</b><br>
 * - Core: id, projectId, stage (current), previousStage<br>
 * - Entry: entryCriteria, entryMetAt, entryGateResults<br>
 * - Exit: exitCriteria, exitMetAt, exitGateResults<br>
 * - Artifacts: requiredArtifacts, producedArtifacts<br>
 * - Events: triggerEvent, completionEvent<br>
 *
 * @see ProjectEntity
 * @see TaskEntity
 * @see com.ghatana.yappc.config.lifecycle.stages
 *
 * @author Ghatana AI Platform
 * @since 2.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * @doc.type class
 * @doc.purpose Handles phase state entity operations
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public class PhaseStateEntity implements Identifiable<UUID> {

  // Core identification
  private UUID id;
  private UUID projectId;
  private String stage; // Current stage: intent, context, plan, execute, verify, observe, learn, institutionalize
  private String previousStage; // Previous stage (null for first)

  // Entry tracking
  private Instant enteredAt;
  private Map<String, Boolean> entryCriteriaMet;
  private Map<String, Object> entryGateResults;
  private boolean entryApproved;

  // Exit tracking
  private Instant exitedAt;
  private Map<String, Boolean> exitCriteriaMet;
  private Map<String, Object> exitGateResults;
  private boolean exitApproved;

  // Artifacts
  private java.util.List<String> requiredArtifacts;
  private java.util.List<String> producedArtifacts;
  private Map<String, String> artifactLocations; // artifact name → storage location

  // Events
  private String triggerEvent; // Event that triggered this phase entry
  private String completionEvent; // Event published on phase completion
  private String eventCorrelationId; // Links to Event Cloud

  // Gate agents
  private java.util.List<GateDecision> gateDecisions;

  // Metadata
  private String status; // ACTIVE, COMPLETED, BLOCKED, SKIPPED
  private Instant createdAt;
  private Instant lastUpdatedAt;
  private String tenantId;
  private Map<String, Object> metadata;
  private String version;

  /**
   * Gate decision record for phase transitions.
   */
  public record GateDecision(
      String agentId,
      String condition,
      boolean approved,
      String reason,
      Instant decidedAt
  ) {}

  /**
   * Creates a new phase state.
   */
  public PhaseStateEntity() {
    this.id = UUID.randomUUID();
    this.status = "ACTIVE";
    this.createdAt = Instant.now();
    this.lastUpdatedAt = Instant.now();
    this.entryCriteriaMet = new java.util.HashMap<>();
    this.exitCriteriaMet = new java.util.HashMap<>();
    this.requiredArtifacts = new java.util.ArrayList<>();
    this.producedArtifacts = new java.util.ArrayList<>();
    this.artifactLocations = new java.util.HashMap<>();
    this.gateDecisions = new java.util.ArrayList<>();
    this.metadata = new java.util.HashMap<>();
    this.version = "1";
  }

  /**
   * Creates a phase state for a project entering a stage.
   */
  public PhaseStateEntity(UUID projectId, String stage, String previousStage, String triggerEvent) {
    this();
    this.projectId = projectId;
    this.stage = stage;
    this.previousStage = previousStage;
    this.triggerEvent = triggerEvent;
    this.enteredAt = Instant.now();
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

  public String getStage() {
    return stage;
  }

  public void setStage(String stage) {
    this.stage = stage;
  }

  public String getPreviousStage() {
    return previousStage;
  }

  public void setPreviousStage(String previousStage) {
    this.previousStage = previousStage;
  }

  public Instant getEnteredAt() {
    return enteredAt;
  }

  public void setEnteredAt(Instant enteredAt) {
    this.enteredAt = enteredAt;
  }

  public Map<String, Boolean> getEntryCriteriaMet() {
    return entryCriteriaMet;
  }

  public void setEntryCriteriaMet(Map<String, Boolean> entryCriteriaMet) {
    this.entryCriteriaMet = entryCriteriaMet;
  }

  public Map<String, Object> getEntryGateResults() {
    return entryGateResults;
  }

  public void setEntryGateResults(Map<String, Object> entryGateResults) {
    this.entryGateResults = entryGateResults;
  }

  public boolean isEntryApproved() {
    return entryApproved;
  }

  public void setEntryApproved(boolean entryApproved) {
    this.entryApproved = entryApproved;
  }

  public Instant getExitedAt() {
    return exitedAt;
  }

  public void setExitedAt(Instant exitedAt) {
    this.exitedAt = exitedAt;
  }

  public Map<String, Boolean> getExitCriteriaMet() {
    return exitCriteriaMet;
  }

  public void setExitCriteriaMet(Map<String, Boolean> exitCriteriaMet) {
    this.exitCriteriaMet = exitCriteriaMet;
  }

  public Map<String, Object> getExitGateResults() {
    return exitGateResults;
  }

  public void setExitGateResults(Map<String, Object> exitGateResults) {
    this.exitGateResults = exitGateResults;
  }

  public boolean isExitApproved() {
    return exitApproved;
  }

  public void setExitApproved(boolean exitApproved) {
    this.exitApproved = exitApproved;
  }

  public java.util.List<String> getRequiredArtifacts() {
    return requiredArtifacts;
  }

  public void setRequiredArtifacts(java.util.List<String> requiredArtifacts) {
    this.requiredArtifacts = requiredArtifacts;
  }

  public java.util.List<String> getProducedArtifacts() {
    return producedArtifacts;
  }

  public void setProducedArtifacts(java.util.List<String> producedArtifacts) {
    this.producedArtifacts = producedArtifacts;
  }

  public Map<String, String> getArtifactLocations() {
    return artifactLocations;
  }

  public void setArtifactLocations(Map<String, String> artifactLocations) {
    this.artifactLocations = artifactLocations;
  }

  public String getTriggerEvent() {
    return triggerEvent;
  }

  public void setTriggerEvent(String triggerEvent) {
    this.triggerEvent = triggerEvent;
  }

  public String getCompletionEvent() {
    return completionEvent;
  }

  public void setCompletionEvent(String completionEvent) {
    this.completionEvent = completionEvent;
  }

  public String getEventCorrelationId() {
    return eventCorrelationId;
  }

  public void setEventCorrelationId(String eventCorrelationId) {
    this.eventCorrelationId = eventCorrelationId;
  }

  public java.util.List<GateDecision> getGateDecisions() {
    return gateDecisions;
  }

  public void setGateDecisions(java.util.List<GateDecision> gateDecisions) {
    this.gateDecisions = gateDecisions;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  public void setLastUpdatedAt(Instant lastUpdatedAt) {
    this.lastUpdatedAt = lastUpdatedAt;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  /**
   * Records an entry criterion as met.
   *
   * @param criterion the criterion name
   * @param met true if met
   * @return this phase for chaining
   */
  public PhaseStateEntity markEntryCriterion(String criterion, boolean met) {
    this.entryCriteriaMet.put(criterion, met);
    this.lastUpdatedAt = Instant.now();
    return this;
  }

  /**
   * Records an exit criterion as met.
   *
   * @param criterion the criterion name
   * @param met true if met
   * @return this phase for chaining
   */
  public PhaseStateEntity markExitCriterion(String criterion, boolean met) {
    this.exitCriteriaMet.put(criterion, met);
    this.lastUpdatedAt = Instant.now();
    return this;
  }

  /**
   * Records a produced artifact.
   *
   * @param name artifact name
   * @param location storage location
   * @return this phase for chaining
   */
  public PhaseStateEntity addProducedArtifact(String name, String location) {
    this.producedArtifacts.add(name);
    this.artifactLocations.put(name, location);
    this.lastUpdatedAt = Instant.now();
    return this;
  }

  /**
   * Records a gate agent decision.
   *
   * @param agentId the gate agent ID
   * @param condition the evaluated condition
   * @param approved true if approved
   * @param reason decision reason
   * @return this phase for chaining
   */
  public PhaseStateEntity recordGateDecision(
      String agentId, String condition, boolean approved, String reason) {
    this.gateDecisions.add(new GateDecision(
        agentId, condition, approved, reason, Instant.now()));
    this.lastUpdatedAt = Instant.now();
    return this;
  }

  /**
   * Checks if all entry criteria are met.
   *
   * @return true if all met
   */
  public boolean allEntryCriteriaMet() {
    return !entryCriteriaMet.isEmpty() && entryCriteriaMet.values().stream().allMatch(Boolean::booleanValue);
  }

  /**
   * Checks if all exit criteria are met.
   *
   * @return true if all met
   */
  public boolean allExitCriteriaMet() {
    return !exitCriteriaMet.isEmpty() && exitCriteriaMet.values().stream().allMatch(Boolean::booleanValue);
  }

  /**
   * Marks the phase as complete and ready for transition.
   *
   * @param completionEvent the event to publish
   * @return true if phase was active
   */
  public boolean complete(String completionEvent) {
    if ("ACTIVE".equals(status)) {
      this.status = "COMPLETED";
      this.completionEvent = completionEvent;
      this.exitedAt = Instant.now();
      this.lastUpdatedAt = Instant.now();
      return true;
    }
    return false;
  }

  /**
   * Marks the phase as blocked by a gate.
   *
   * @param reason the blocking reason
   * @return this phase for chaining
   */
  public PhaseStateEntity block(String reason) {
    this.status = "BLOCKED";
    this.metadata.put("block_reason", reason);
    this.lastUpdatedAt = Instant.now();
    return this;
  }

  /**
   * Gets the duration spent in this phase.
   *
   * @return duration in seconds, or -1 if not entered
   */
  public long getDurationSeconds() {
    if (enteredAt == null) {
      return -1;
    }
    Instant end = exitedAt != null ? exitedAt : Instant.now();
    return java.time.Duration.between(enteredAt, end).getSeconds();
  }

  /**
   * Checks if the phase was blocked.
   *
   * @return true if blocked
   */
  public boolean isBlocked() {
    return "BLOCKED".equals(status);
  }

  /**
   * Checks if the phase is complete.
   *
   * @return true if completed
   */
  public boolean isCompleted() {
    return "COMPLETED".equals(status);
  }

  /**
   * Gets the collection name for Data-Cloud persistence.
   *
   * @return collection name
   */
  public static String getCollectionName() {
    return "phase_states";
  }
}
