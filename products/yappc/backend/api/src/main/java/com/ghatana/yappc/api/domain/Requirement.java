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
 * Domain entity representing a software requirement.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates all attributes of a requirement including metadata, lifecycle state, quality
 * metrics, and relationships.
 *
 * <p><b>Lifecycle States</b><br>
 *
 * <pre>
 * DRAFT → REVIEW → APPROVED → IMPLEMENTED → VERIFIED
 *                    ↓
 *                 REJECTED
 * </pre>
 *
 * <p><b>Quality Metrics</b><br>
 * - Testability Score: 0.0 - 1.0 (how easily testable) - Completeness: 0.0 - 1.0 (how complete the
 * specification) - Ambiguity Flags: List of detected ambiguous phrases
 *
 * @doc.type class
 * @doc.purpose Requirement domain entity
 * @doc.layer domain
 * @doc.pattern Entity
 */
public class Requirement {

  private UUID id;
  private String tenantId;
  private String projectId;
  private String title;
  private String description;
  private RequirementType type;
  private RequirementStatus status;
  private Priority priority;
  private String category;
  private String createdBy;
  private String assignedTo;
  private Instant createdAt;
  private Instant updatedAt;
  private Integer versionNumber;
  private List<String> tags;
  private List<String> dependencies;
  private QualityMetrics qualityMetrics;
  private Map<String, Object> metadata;

  public Requirement() {
    this.id = UUID.randomUUID();
    this.status = RequirementStatus.DRAFT;
    this.priority = Priority.MEDIUM;
    this.versionNumber = 1;
    this.tags = new ArrayList<>();
    this.dependencies = new ArrayList<>();
    this.qualityMetrics = new QualityMetrics();
    this.metadata = new HashMap<>();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  // ========== Enums ==========

  public enum RequirementType {
    FUNCTIONAL,
    NON_FUNCTIONAL,
    CONSTRAINT,
    INTERFACE,
    SECURITY,
    PERFORMANCE,
    USABILITY,
    COMPLIANCE
  }

  public enum RequirementStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    REJECTED,
    IMPLEMENTED,
    VERIFIED,
    DEPRECATED
  }

  public enum Priority {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW
  }

  // ========== Quality Metrics ==========

  public static class QualityMetrics {
    private double testabilityScore;
    private double completenessScore;
    private double clarityScore;
    private List<String> ambiguityFlags;

    public QualityMetrics() {
      this.testabilityScore = 0.0;
      this.completenessScore = 0.0;
      this.clarityScore = 0.0;
      this.ambiguityFlags = new ArrayList<>();
    }

    public double getTestabilityScore() {
      return testabilityScore;
    }

    public void setTestabilityScore(double testabilityScore) {
      this.testabilityScore = testabilityScore;
    }

    public double getCompletenessScore() {
      return completenessScore;
    }

    public void setCompletenessScore(double completenessScore) {
      this.completenessScore = completenessScore;
    }

    public double getClarityScore() {
      return clarityScore;
    }

    public void setClarityScore(double clarityScore) {
      this.clarityScore = clarityScore;
    }

    public List<String> getAmbiguityFlags() {
      return ambiguityFlags;
    }

    public void setAmbiguityFlags(List<String> ambiguityFlags) {
      this.ambiguityFlags = ambiguityFlags;
    }
  }

  // ========== Builder Pattern ==========

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final Requirement requirement = new Requirement();

    public Builder id(UUID id) {
      requirement.id = id;
      return this;
    }

    public Builder tenantId(String tenantId) {
      requirement.tenantId = tenantId;
      return this;
    }

    public Builder projectId(String projectId) {
      requirement.projectId = projectId;
      return this;
    }

    public Builder title(String title) {
      requirement.title = title;
      return this;
    }

    public Builder description(String description) {
      requirement.description = description;
      return this;
    }

    public Builder type(RequirementType type) {
      requirement.type = type;
      return this;
    }

    public Builder status(RequirementStatus status) {
      requirement.status = status;
      return this;
    }

    public Builder priority(Priority priority) {
      requirement.priority = priority;
      return this;
    }

    public Builder category(String category) {
      requirement.category = category;
      return this;
    }

    public Builder createdBy(String createdBy) {
      requirement.createdBy = createdBy;
      return this;
    }

    public Builder assignedTo(String assignedTo) {
      requirement.assignedTo = assignedTo;
      return this;
    }

    public Builder tags(List<String> tags) {
      requirement.tags = new ArrayList<>(tags);
      return this;
    }

    public Builder dependencies(List<String> dependencies) {
      requirement.dependencies = new ArrayList<>(dependencies);
      return this;
    }

    public Builder qualityMetrics(QualityMetrics qualityMetrics) {
      requirement.qualityMetrics = qualityMetrics;
      return this;
    }

    public Builder metadata(Map<String, Object> metadata) {
      requirement.metadata = new HashMap<>(metadata);
      return this;
    }

    public Requirement build() {
      Objects.requireNonNull(requirement.tenantId, "Tenant ID must not be null");
      Objects.requireNonNull(requirement.title, "Title must not be null");
      return requirement;
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

  public RequirementType getType() {
    return type;
  }

  public void setType(RequirementType type) {
    this.type = type;
  }

  public RequirementStatus getStatus() {
    return status;
  }

  public void setStatus(RequirementStatus status) {
    this.status = status;
  }

  public Priority getPriority() {
    return priority;
  }

  public void setPriority(Priority priority) {
    this.priority = priority;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(String createdBy) {
    this.createdBy = createdBy;
  }

  public String getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(String assignedTo) {
    this.assignedTo = assignedTo;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Integer getVersionNumber() {
    return versionNumber;
  }

  public void setVersionNumber(Integer versionNumber) {
    this.versionNumber = versionNumber;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public void setDependencies(List<String> dependencies) {
    this.dependencies = dependencies;
  }

  public QualityMetrics getQualityMetrics() {
    return qualityMetrics;
  }

  public void setQualityMetrics(QualityMetrics qualityMetrics) {
    this.qualityMetrics = qualityMetrics;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }

  // ========== Domain Methods ==========

  /**
   * Transition requirement to review status.
   *
   * @throws IllegalStateException if not in DRAFT state
   */
  public void submitForReview() {
    if (status != RequirementStatus.DRAFT) {
      throw new IllegalStateException("Can only submit DRAFT requirements for review");
    }
    this.status = RequirementStatus.REVIEW;
    this.updatedAt = Instant.now();
  }

  /**
   * Approve the requirement.
   *
   * @throws IllegalStateException if not in REVIEW state
   */
  public void approve() {
    if (status != RequirementStatus.REVIEW) {
      throw new IllegalStateException("Can only approve requirements in REVIEW state");
    }
    this.status = RequirementStatus.APPROVED;
    this.updatedAt = Instant.now();
  }

  /**
   * Reject the requirement.
   *
   * @throws IllegalStateException if not in REVIEW state
   */
  public void reject() {
    if (status != RequirementStatus.REVIEW) {
      throw new IllegalStateException("Can only reject requirements in REVIEW state");
    }
    this.status = RequirementStatus.REJECTED;
    this.updatedAt = Instant.now();
  }

  /**
   * Check if requirement meets quality thresholds.
   *
   * @return true if all quality scores >= 0.7
   */
  public boolean meetsQualityThreshold() {
    return qualityMetrics.testabilityScore >= 0.7
        && qualityMetrics.completenessScore >= 0.7
        && qualityMetrics.clarityScore >= 0.7
        && qualityMetrics.ambiguityFlags.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Requirement that)) return false;
    return Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return "Requirement{"
        + "id="
        + id
        + ", tenantId='"
        + tenantId
        + '\''
        + ", title='"
        + title
        + '\''
        + ", status="
        + status
        + ", priority="
        + priority
        + '}';
  }
}
