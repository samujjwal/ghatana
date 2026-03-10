/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing an AI-generated suggestion.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates AI suggestions that require human review and approval. Supports the
 * human-in-the-loop pattern for AI-assisted requirements.
 *
 * <p><b>Lifecycle States</b><br>
 *
 * <pre>
 * PENDING → ACCEPTED → APPLIED
 *        ↓
 *     REJECTED
 *        ↓
 *     DEFERRED
 * </pre>
 *
 * <p><b>Suggestion Types</b><br>
 * - REQUIREMENT: New requirement suggestions - CLARIFICATION: Clarify ambiguous requirements -
 * REFINEMENT: Improve existing requirements - ALTERNATIVE: Alternative approaches - DECOMPOSITION:
 * Break down complex requirements - DEPENDENCY: Identify hidden dependencies
 *
 * @doc.type class
 * @doc.purpose AI suggestion domain entity
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class AISuggestion {

  private UUID id;
  private String tenantId;
  private String projectId;
  private SuggestionType type;
  private SuggestionStatus status;
  private String title;
  private String content;
  private String rationale;
  private String sourceModel;
  private String targetResourceId;
  private String targetResourceType;
  private double confidence;
  private Priority priority;
  private String createdBy;
  private String reviewedBy;
  private Instant createdAt;
  private Instant reviewedAt;
  private String reviewNotes;
  private List<String> tags;
  private Map<String, Object> metadata;

  public AISuggestion() {
    this.id = UUID.randomUUID();
    this.status = SuggestionStatus.PENDING;
    this.priority = Priority.MEDIUM;
    this.confidence = 0.0;
    this.tags = new ArrayList<>();
    this.metadata = new HashMap<>();
    this.createdAt = Instant.now();
  }

  // ========== Enums ==========

  public enum SuggestionType {
    REQUIREMENT,
    CLARIFICATION,
    REFINEMENT,
    ALTERNATIVE,
    DECOMPOSITION,
    DEPENDENCY,
    EDGE_CASE,
    SECURITY,
    PERFORMANCE,
    TESTABILITY
  }

  public enum SuggestionStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    DEFERRED,
    APPLIED,
    EXPIRED
  }

  public enum Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }

  // ========== Builder Pattern ==========

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final AISuggestion suggestion = new AISuggestion();

    public Builder id(UUID id) {
      suggestion.id = id;
      return this;
    }

    public Builder tenantId(String tenantId) {
      suggestion.tenantId = tenantId;
      return this;
    }

    public Builder projectId(String projectId) {
      suggestion.projectId = projectId;
      return this;
    }

    public Builder type(SuggestionType type) {
      suggestion.type = type;
      return this;
    }

    public Builder status(SuggestionStatus status) {
      suggestion.status = status;
      return this;
    }

    public Builder title(String title) {
      suggestion.title = title;
      return this;
    }

    public Builder content(String content) {
      suggestion.content = content;
      return this;
    }

    public Builder rationale(String rationale) {
      suggestion.rationale = rationale;
      return this;
    }

    public Builder sourceModel(String sourceModel) {
      suggestion.sourceModel = sourceModel;
      return this;
    }

    public Builder targetResourceId(String targetResourceId) {
      suggestion.targetResourceId = targetResourceId;
      return this;
    }

    public Builder targetResourceType(String targetResourceType) {
      suggestion.targetResourceType = targetResourceType;
      return this;
    }

    public Builder confidence(double confidence) {
      suggestion.confidence = confidence;
      return this;
    }

    public Builder priority(Priority priority) {
      suggestion.priority = priority;
      return this;
    }

    public Builder createdBy(String createdBy) {
      suggestion.createdBy = createdBy;
      return this;
    }

    public Builder tags(List<String> tags) {
      suggestion.tags = new ArrayList<>(tags);
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      suggestion.metadata = new HashMap<>(metadata);
      return this;
    }

    public AISuggestion build() {
      Objects.requireNonNull(suggestion.tenantId, "Tenant ID must not be null");
      Objects.requireNonNull(suggestion.type, "Type must not be null");
      Objects.requireNonNull(suggestion.content, "Content must not be null");
      return suggestion;
    }
  }

  // ========== Getters and Setters ==========

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getTenantId() {
    return tenantId;
  }

  public void setTenantId(String tenantId) {
    this.tenantId = tenantId;
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public SuggestionType getType() {
    return type;
  }

  public void setType(SuggestionType type) {
    this.type = type;
  }

  public SuggestionStatus getStatus() {
    return status;
  }

  public void setStatus(SuggestionStatus status) {
    this.status = status;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getRationale() {
    return rationale;
  }

  public void setRationale(String rationale) {
    this.rationale = rationale;
  }

  public String getSourceModel() {
    return sourceModel;
  }

  public void setSourceModel(String sourceModel) {
    this.sourceModel = sourceModel;
  }

  public String getTargetResourceId() {
    return targetResourceId;
  }

  public void setTargetResourceId(String targetResourceId) {
    this.targetResourceId = targetResourceId;
  }

  public String getTargetResourceType() {
    return targetResourceType;
  }

  public void setTargetResourceType(String targetResourceType) {
    this.targetResourceType = targetResourceType;
  }

  public double getConfidence() {
    return confidence;
  }

  public void setConfidence(double confidence) {
    this.confidence = confidence;
  }

  public Priority getPriority() {
    return priority;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getReviewedBy() {
    return reviewedBy;
  }

  public void setReviewedBy(String reviewedBy) {
    this.reviewedBy = reviewedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public void setReviewedAt(Instant reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  public String getReviewNotes() {
    return reviewNotes;
  }

  public void setReviewNotes(String reviewNotes) {
    this.reviewNotes = reviewNotes;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  // ========== Domain Methods ==========

  /**
   * Accept the suggestion with optional notes.
   *
   * @param reviewedBy the user accepting
   * @param notes optional review notes
   * @throws IllegalStateException if not in PENDING state
   */
  public void accept(String reviewedBy, String notes) {
    if (status != SuggestionStatus.PENDING) {
      throw new IllegalStateException("Can only accept PENDING suggestions");
    }
    this.status = SuggestionStatus.ACCEPTED;
    this.reviewedBy = reviewedBy;
    this.reviewedAt = Instant.now();
    this.reviewNotes = notes;
  }

  /**
   * Reject the suggestion with reason.
   *
   * @param reviewedBy the user rejecting
   * @param reason the rejection reason
   * @throws IllegalStateException if not in PENDING state
   */
  public void reject(String reviewedBy, String reason) {
    if (status != SuggestionStatus.PENDING) {
      throw new IllegalStateException("Can only reject PENDING suggestions");
    }
    this.status = SuggestionStatus.REJECTED;
    this.reviewedBy = reviewedBy;
    this.reviewedAt = Instant.now();
    this.reviewNotes = reason;
  }

  /**
   * Defer the suggestion for later review.
   *
   * @param reviewedBy the user deferring
   * @param reason the deferral reason
   * @throws IllegalStateException if not in PENDING state
   */
  public void defer(String reviewedBy, String reason) {
    if (status != SuggestionStatus.PENDING) {
      throw new IllegalStateException("Can only defer PENDING suggestions");
    }
    this.status = SuggestionStatus.DEFERRED;
    this.reviewedBy = reviewedBy;
    this.reviewedAt = Instant.now();
    this.reviewNotes = reason;
  }

  /**
   * Mark as applied after changes have been made.
   *
   * @throws IllegalStateException if not in ACCEPTED state
   */
  public void markApplied() {
    if (status != SuggestionStatus.ACCEPTED) {
      throw new IllegalStateException("Can only apply ACCEPTED suggestions");
    }
    this.status = SuggestionStatus.APPLIED;
  }

  /**
   * Check if suggestion is high confidence (>= 0.8).
   *
   * @return true if confidence >= 0.8
   */
  public boolean isHighConfidence() {
    return confidence >= 0.8;
  }

  /**
   * Check if suggestion needs human review (confidence < 0.9).
   *
   * @return true if requires review
   */
  public boolean needsReview() {
    return confidence < 0.9;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof AISuggestion that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "AISuggestion{"
        + "id="
        + id
        + ", tenantId='"
        + tenantId
        + '\''
        + ", type="
        + type
        + ", status="
        + status
        + ", confidence="
        + confidence
        + '}';
  }
}
