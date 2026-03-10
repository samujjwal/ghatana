package com.ghatana.yappc.ai.requirements.domain.requirement;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Requirement domain model (aggregate root).
 *
 * <p><b>Purpose</b><br>
 * Represents a system requirement with full lifecycle management, versioning,
 * and workflow integration. Maintains complete version history for audit trails.
 *
 * <p><b>Hierarchy</b><br>
 * Project → Requirement(s) with version history
 *
 * <p><b>Versioning</b><br>
 * Each change creates a new version snapshot for audit trail:
 * - Initial creation is version 1
 * - Changes (title, description, priority, etc.) create new versions
 * - Can revert to previous versions
 * - Complete history is maintained
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Requirement req = Requirement.builder()
 *     .requirementId("req-123")
 *     .projectId("proj-456")
 *     .title("User Login")
 *     .description("Allow users to authenticate")
 *     .type(RequirementType.FUNCTIONAL)
 *     .priority(RequirementPriority.MUST_HAVE)
 *     .createdBy("user-789")
 *     .build();
 *
 * // Create version when initial state set
 * req.createVersion("user-789", "Initial creation");
 *
 * // Later, update requirement
 * req.setDescription("Allow users to authenticate with MFA");
 *
 * // Create new version after change
 * req.createVersion("user-789", "Added MFA requirement");
 *
 * // Can revert to previous version
 * req.revertToVersion(1);
 *
 * // View history
 * List<RequirementVersion> history = req.getVersionHistory();
 * }</pre>
 *
 * <p><b>Integration Points</b><br>
 * - ProjectRepository: Persisted via Requirement repository
 * - WorkflowEngine: Approval workflows for status changes
 * - PatternDetector: Anomaly detection for requirement patterns
 * - ApprovalRecommender: ML-based approver suggestions
 * - AI Suggestions: Links to AI recommendation system
 *
 * <p><b>Thread Safety</b><br>
 * Mutable - designed for single-threaded use within transaction boundaries.
 * For multi-threaded access, use external synchronization.
 *
 * @doc.type class
 * @doc.purpose Requirement aggregate root with versioning
 * @doc.layer product
 * @doc.pattern Domain Model, Aggregate Root, Memento
 */
public final class Requirement {
  private final String requirementId;
  private final String projectId;
  private String title;
  private String description;
  private RequirementType type;
  private RequirementPriority priority;
  private RequirementStatus status;
  private final String createdBy;
  private String assignedTo;
  private final List<RequirementVersion> versions;
  private int currentVersion;
  private RequirementMetadata metadata;
  private final Instant createdAt;
  private Instant updatedAt;

  /**
   * Create Requirement (private - use builder).
   *
   * @param requirementId unique requirement ID
   * @param projectId parent project ID
   * @param title requirement title
   * @param description requirement description
   * @param type requirement type
   * @param priority requirement priority
   * @param status requirement status
   * @param createdBy creator user ID
   * @param assignedTo assigned to user ID (optional)
   * @param metadata requirement metadata
   * @param createdAt creation timestamp
   * @param updatedAt last update timestamp
   */
  private Requirement(
      String requirementId,
      String projectId,
      String title,
      String description,
      RequirementType type,
      RequirementPriority priority,
      RequirementStatus status,
      String createdBy,
      String assignedTo,
      RequirementMetadata metadata,
      Instant createdAt,
      Instant updatedAt) {
    this.requirementId = requirementId;
    this.projectId = projectId;
    this.title = title;
    this.description = description;
    this.type = type;
    this.priority = priority;
    this.status = status;
    this.createdBy = createdBy;
    this.assignedTo = assignedTo;
    this.versions = new ArrayList<>();
    this.currentVersion = 0;
    this.metadata = metadata;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  // Accessors

  /**
   * Get requirement ID.
   *
   * @return requirement ID
   */
  public String getRequirementId() {
    return requirementId;
  }

  /**
   * Get project ID.
   *
   * @return project ID
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Get requirement title.
   *
   * @return title
   */
  public String getTitle() {
    return title;
  }

  /**
   * Get requirement description.
   *
   * @return description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Get requirement type.
   *
   * @return type
   */
  public RequirementType getType() {
    return type;
  }

  /**
   * Get requirement priority.
   *
   * @return priority
   */
  public RequirementPriority getPriority() {
    return priority;
  }

  /**
   * Get requirement status.
   *
   * @return status
   */
  public RequirementStatus getStatus() {
    return status;
  }

  /**
   * Get creator user ID.
   *
   * @return creator ID
   */
  public String getCreatedBy() {
    return createdBy;
  }

  /**
   * Get assigned to user ID.
   *
   * @return assigned user ID or null
   */
  public String getAssignedTo() {
    return assignedTo;
  }

  /**
   * Get requirement metadata.
   *
   * @return metadata
   */
  public RequirementMetadata getMetadata() {
    return metadata;
  }

  /**
   * Get current version number.
   *
   * @return version number
   */
  public int getCurrentVersion() {
    return currentVersion;
  }

  /**
   * Get creation timestamp.
   *
   * @return created at
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Get last update timestamp.
   *
   * @return updated at
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // Mutations

  /**
   * Set requirement title.
   *
   * @param newTitle new title
   */
  public void setTitle(String newTitle) {
    this.title = newTitle;
    this.updatedAt = Instant.now();
  }

  /**
   * Set requirement description.
   *
   * @param newDescription new description
   */
  public void setDescription(String newDescription) {
    this.description = newDescription;
    this.updatedAt = Instant.now();
  }

  /**
   * Set requirement type.
   *
   * @param newType new type
   */
  public void setType(RequirementType newType) {
    this.type = newType;
    this.updatedAt = Instant.now();
  }

  /**
   * Set requirement priority.
   *
   * @param newPriority new priority
   */
  public void setPriority(RequirementPriority newPriority) {
    this.priority = newPriority;
    this.updatedAt = Instant.now();
  }

  /**
   * Set requirement status.
   *
   * @param newStatus new status
   */
  public void setStatus(RequirementStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = Instant.now();
  }

  /**
   * Assign requirement to user.
   *
   * @param userId user to assign to
   */
  public void assignTo(String userId) {
    this.assignedTo = userId;
    this.updatedAt = Instant.now();
  }

  /**
   * Unassign requirement.
   */
  public void unassign() {
    this.assignedTo = null;
    this.updatedAt = Instant.now();
  }

  /**
   * Set requirement metadata.
   *
   * @param newMetadata new metadata
   */
  public void setMetadata(RequirementMetadata newMetadata) {
    this.metadata = newMetadata;
    this.updatedAt = Instant.now();
  }

  // Versioning

  /**
   * Create new version snapshot with current state.
   *
   * <p>This captures the current requirement state as an immutable version.
   * Call this after making changes to record a version.
   *
   * @param changedBy user making the change
   * @param changeReason reason for the change
   * @return created version
   */
  public RequirementVersion createVersion(String changedBy, String changeReason) {
    RequirementVersion version =
        new RequirementVersion(
            requirementId,
            ++currentVersion,
            title,
            description,
            type,
            priority,
            status,
            changedBy,
            changeReason,
            Instant.now());

    versions.add(version);
    return version;
  }

  /**
   * Revert requirement to a previous version.
   *
   * <p>This restores all properties from the specified version number.
   * Does not delete version history - creates a new version recording the revert.
   *
   * @param versionNumber version to revert to
   * @throws IllegalArgumentException if version not found
   */
  public void revertToVersion(int versionNumber) {
    RequirementVersion version =
        versions.stream()
            .filter(v -> v.versionNumber() == versionNumber)
            .findFirst()
            .orElseThrow(
                () -> new IllegalArgumentException("Version not found: " + versionNumber));

    this.title = version.title();
    this.description = version.description();
    this.type = version.type();
    this.priority = version.priority();
    this.status = version.status();
    this.updatedAt = Instant.now();

    // Note: currentVersion is NOT reset. Instead, next createVersion call
    // will create the next version number, maintaining sequence.
  }

  /**
   * Get complete version history (immutable).
   *
   * @return unmodifiable list of all versions
   */
  public List<RequirementVersion> getVersionHistory() {
    return Collections.unmodifiableList(versions);
  }

  /**
   * Get specific version by number.
   *
   * @param versionNumber version number to retrieve
   * @return version or null if not found
   */
  public RequirementVersion getVersion(int versionNumber) {
    return versions.stream()
        .filter(v -> v.versionNumber() == versionNumber)
        .findFirst()
        .orElse(null);
  }

  /**
   * Get latest version.
   *
   * @return most recent version or null if no versions created
   */
  public RequirementVersion getLatestVersion() {
    if (versions.isEmpty()) {
      return null;
    }
    return versions.get(versions.size() - 1);
  }

  // Queries

  /**
   * Check if requirement is assigned.
   *
   * @return true if assigned to someone
   */
  public boolean isAssigned() {
    return assignedTo != null;
  }

  /**
   * Check if requirement is critical (MUST_HAVE).
   *
   * @return true if priority is MUST_HAVE
   */
  public boolean isCritical() {
    return priority.isCritical();
  }

  /**
   * Check if requirement is approved.
   *
   * @return true if status is APPROVED
   */
  public boolean isApproved() {
    return status == RequirementStatus.APPROVED;
  }

  /**
   * Check if requirement is in draft state.
   *
   * @return true if status is DRAFT
   */
  public boolean isDraft() {
    return status == RequirementStatus.DRAFT;
  }

  /**
   * Check if requirement is pending action.
   *
   * @return true if status is PENDING_REVIEW or IN_REVIEW
   */
  public boolean isPendingAction() {
    return status == RequirementStatus.PENDING_REVIEW || status == RequirementStatus.IN_REVIEW;
  }

  // Builder

  /**
   * Create builder for Requirement construction.
   *
   * @return new RequirementBuilder
   */
  public static RequirementBuilder builder() {
    return new RequirementBuilder();
  }

  /**
   * Builder for Requirement with fluent API.
   */
  public static class RequirementBuilder {
    private String requirementId;
    private String projectId;
    private String title;
    private String description = "";
    private RequirementType type = RequirementType.FUNCTIONAL;
    private RequirementPriority priority = RequirementPriority.SHOULD_HAVE;
    private RequirementStatus status = RequirementStatus.DRAFT;
    private String createdBy;
    private String assignedTo;
    private RequirementMetadata metadata = RequirementMetadata.empty();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    public RequirementBuilder requirementId(String id) {
      this.requirementId = id;
      return this;
    }

    public RequirementBuilder projectId(String id) {
      this.projectId = id;
      return this;
    }

    public RequirementBuilder title(String title) {
      this.title = title;
      return this;
    }

    public RequirementBuilder description(String desc) {
      this.description = desc != null ? desc : "";
      return this;
    }

    public RequirementBuilder type(RequirementType type) {
      this.type = type;
      return this;
    }

    public RequirementBuilder priority(RequirementPriority priority) {
      this.priority = priority;
      return this;
    }

    public RequirementBuilder status(RequirementStatus status) {
      this.status = status;
      return this;
    }

    public RequirementBuilder createdBy(String userId) {
      this.createdBy = userId;
      return this;
    }

    public RequirementBuilder assignedTo(String userId) {
      this.assignedTo = userId;
      return this;
    }

    public RequirementBuilder metadata(RequirementMetadata metadata) {
      this.metadata = metadata;
      return this;
    }

    public RequirementBuilder createdAt(Instant instant) {
      this.createdAt = instant;
      return this;
    }

    public RequirementBuilder updatedAt(Instant instant) {
      this.updatedAt = instant;
      return this;
    }

    public Requirement build() {
      Objects.requireNonNull(requirementId, "requirementId is required");
      Objects.requireNonNull(projectId, "projectId is required");
      Objects.requireNonNull(title, "title is required");
      Objects.requireNonNull(createdBy, "createdBy is required");

      return new Requirement(
          requirementId,
          projectId,
          title,
          description,
          type,
          priority,
          status,
          createdBy,
          assignedTo,
          metadata,
          createdAt,
          updatedAt);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Requirement that = (Requirement) o;
    return Objects.equals(requirementId, that.requirementId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(requirementId);
  }

  @Override
  public String toString() {
    return "Requirement{"
        + "requirementId='"
        + requirementId
        + '\''
        + ", title='"
        + title
        + '\''
        + ", status="
        + status
        + ", version="
        + currentVersion
        + '}';
  }
}