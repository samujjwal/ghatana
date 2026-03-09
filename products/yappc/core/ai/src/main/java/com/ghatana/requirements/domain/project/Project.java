package com.ghatana.requirements.domain.project;

import com.ghatana.requirements.domain.workspace.Workspace;
import java.time.Instant;
import java.util.Objects;

/**
 * Project domain model (aggregate root).
 *
 * <p><b>Purpose</b><br>
 * Represents a project within a workspace. Projects contain requirements and are
 * owned by workspaces. Each project can have its own team structure via sub-OrgUnit.
 *
 * <p><b>Hierarchy</b><br>
 * Workspace → Project(s) → Requirement(s)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Project project = Project.builder()
 *     .projectId("proj-123")
 *     .workspaceId("ws-456")
 *     .name("Mobile App 2.0")
 *     .ownerId("user-789")
 *     .status(ProjectStatus.ACTIVE)
 *     .build();
 *     
 * // Check access
 * if (project.hasAccess(userId, workspace)) {
 *     // User can work on this project
 * }
 * }</pre>
 *
 * <p><b>Integration Points</b><br>
 * - Belongs to: Workspace (via workspaceId)
 * - Org Structure: Can have sub-OrgUnit (via getOrgUnitId())
 * - Workflow: Status changes may require approval
 * - Authorization: Access controlled via workspace membership
 *
 * <p><b>Thread Safety</b><br>
 * Mutable - designed for single-threaded use within transaction boundaries.
 * Thread-safe copy operations via builder pattern.
 *
 * @doc.type class
 * @doc.purpose Project aggregate root
 * @doc.layer product
 * @doc.pattern Domain Model, Aggregate Root
 */
public final class Project {
  private final String projectId;
  private final String workspaceId;
  private final String name;
  private String description;
  private final String ownerId;
  private ProjectStatus status;
  private ProjectSettings settings;
  private ProjectMetadata metadata;
  private String templateId;
  private final Instant createdAt;
  private Instant updatedAt;

  /**
   * Create Project (private - use builder).
   *
   * @param projectId unique project ID
   * @param workspaceId parent workspace ID
   * @param name project name
   * @param description project description
   * @param ownerId project owner user ID
   * @param status project status
   * @param settings project settings
   * @param metadata project metadata
   * @param templateId template ID if created from template
   * @param createdAt creation timestamp
   * @param updatedAt last update timestamp
   */
  private Project(
      String projectId,
      String workspaceId,
      String name,
      String description,
      String ownerId,
      ProjectStatus status,
      ProjectSettings settings,
      ProjectMetadata metadata,
      String templateId,
      Instant createdAt,
      Instant updatedAt) {
    this.projectId = projectId;
    this.workspaceId = workspaceId;
    this.name = name;
    this.description = description;
    this.ownerId = ownerId;
    this.status = status;
    this.settings = settings;
    this.metadata = metadata;
    this.templateId = templateId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  // Accessors

  /**
   * Get project identifier.
   *
   * @return project ID
   */
  public String getProjectId() {
    return projectId;
  }

  /**
   * Get parent workspace identifier.
   *
   * @return workspace ID
   */
  public String getWorkspaceId() {
    return workspaceId;
  }

  /**
   * Get project name.
   *
   * @return project name
   */
  public String getName() {
    return name;
  }

  /**
   * Get project description.
   *
   * @return project description
   */
  public String getDescription() {
    return description;
  }

  /**
   * Get project owner user ID.
   *
   * @return owner user ID
   */
  public String getOwnerId() {
    return ownerId;
  }

  /**
   * Get project status.
   *
   * @return project status
   */
  public ProjectStatus getStatus() {
    return status;
  }

  /**
   * Get project settings.
   *
   * @return project settings
   */
  public ProjectSettings getSettings() {
    return settings;
  }

  /**
   * Get project metadata.
   *
   * @return project metadata
   */
  public ProjectMetadata getMetadata() {
    return metadata;
  }

  /**
   * Get template ID if created from template.
   *
   * @return template ID or null
   */
  public String getTemplateId() {
    return templateId;
  }

  /**
   * Get creation timestamp.
   *
   * @return created at instant
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Get last update timestamp.
   *
   * @return updated at instant
   */
  public Instant getUpdatedAt() {
    return updatedAt;
  }

  // Mutations

  /**
   * Update project description.
   *
   * @param newDescription new description
   */
  public void setDescription(String newDescription) {
    this.description = newDescription;
    this.updatedAt = Instant.now();
  }

  /**
   * Update project status.
   *
   * @param newStatus new status
   */
  public void setStatus(ProjectStatus newStatus) {
    this.status = newStatus;
    this.updatedAt = Instant.now();
  }

  /**
   * Update project settings.
   *
   * @param newSettings new settings
   */
  public void setSettings(ProjectSettings newSettings) {
    this.settings = newSettings;
    this.updatedAt = Instant.now();
  }

  /**
   * Update project metadata.
   *
   * @param newMetadata new metadata
   */
  public void setMetadata(ProjectMetadata newMetadata) {
    this.metadata = newMetadata;
    this.updatedAt = Instant.now();
  }

  // Queries

  /**
   * Check if user has access to this project.
   *
   * <p>Access is granted if user is a member of the parent workspace.
   *
   * @param userId user ID to check
   * @param workspace parent workspace
   * @return true if user can access project
   */
  public boolean hasAccess(String userId, Workspace workspace) {
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(workspace, "workspace cannot be null");
    return workspace.isMember(userId);
  }

  /**
   * Check if user is project owner.
   *
   * @param userId user ID to check
   * @return true if user is project owner
   */
  public boolean isOwner(String userId) {
    return ownerId.equals(userId);
  }

  /**
   * Check if project is in active state.
   *
   * @return true if project status is ACTIVE
   */
  public boolean isActive() {
    return status == ProjectStatus.ACTIVE;
  }

  /**
   * Get organization unit ID for this project.
   *
   * <p>Each project can have a corresponding sub-OrgUnit for team structure.
   * The org unit ID is derived from project ID.
   *
   * @return org unit ID for this project
   */
  public String getOrgUnitId() {
    return "project-" + projectId;
  }

  /**
   * Check if project settings require approval for new requirements.
   *
   * @return true if approval required
   */
  public boolean requiresApprovalForRequirements() {
    return settings.requireApprovalForRequirements();
  }

  /**
   * Check if project has AI suggestions enabled.
   *
   * @return true if AI suggestions enabled
   */
  public boolean hasAISuggestionsEnabled() {
    return settings.enableAISuggestions();
  }

  /**
   * Check if a requirement type is allowed in this project.
   *
   * @param type requirement type to check
   * @return true if type is allowed
   */
  public boolean isAllowedRequirementType(String type) {
    return settings.isAllowedRequirementType(type);
  }

  // Builder

  /**
   * Create builder for Project construction.
   *
   * @return new ProjectBuilder
   */
  public static ProjectBuilder builder() {
    return new ProjectBuilder();
  }

  /**
   * Builder for Project with fluent API.
   */
  public static class ProjectBuilder {
    private String projectId;
    private String workspaceId;
    private String name;
    private String description = "";
    private String ownerId;
    private ProjectStatus status = ProjectStatus.DRAFT;
    private ProjectSettings settings = ProjectSettings.defaults();
    private ProjectMetadata metadata = ProjectMetadata.empty();
    private String templateId;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    /**
     * Set project ID.
     *
     * @param projectId project identifier
     * @return this builder
     */
    public ProjectBuilder projectId(String projectId) {
      this.projectId = projectId;
      return this;
    }

    /**
     * Set workspace ID.
     *
     * @param workspaceId parent workspace ID
     * @return this builder
     */
    public ProjectBuilder workspaceId(String workspaceId) {
      this.workspaceId = workspaceId;
      return this;
    }

    /**
     * Set project name.
     *
     * @param name project name
     * @return this builder
     */
    public ProjectBuilder name(String name) {
      this.name = name;
      return this;
    }

    /**
     * Set project description.
     *
     * @param description project description
     * @return this builder
     */
    public ProjectBuilder description(String description) {
      this.description = description != null ? description : "";
      return this;
    }

    /**
     * Set project owner.
     *
     * @param ownerId owner user ID
     * @return this builder
     */
    public ProjectBuilder ownerId(String ownerId) {
      this.ownerId = ownerId;
      return this;
    }

    /**
     * Set project status.
     *
     * @param status project status
     * @return this builder
     */
    public ProjectBuilder status(ProjectStatus status) {
      this.status = status;
      return this;
    }

    /**
     * Set project settings.
     *
     * @param settings project settings
     * @return this builder
     */
    public ProjectBuilder settings(ProjectSettings settings) {
      this.settings = settings;
      return this;
    }

    /**
     * Set project metadata.
     *
     * @param metadata project metadata
     * @return this builder
     */
    public ProjectBuilder metadata(ProjectMetadata metadata) {
      this.metadata = metadata;
      return this;
    }

    /**
     * Set template ID.
     *
     * @param templateId template identifier
     * @return this builder
     */
    public ProjectBuilder templateId(String templateId) {
      this.templateId = templateId;
      return this;
    }

    /**
     * Set creation timestamp.
     *
     * @param createdAt creation timestamp
     * @return this builder
     */
    public ProjectBuilder createdAt(Instant createdAt) {
      this.createdAt = createdAt;
      return this;
    }

    /**
     * Set last update timestamp.
     *
     * @param updatedAt update timestamp
     * @return this builder
     */
    public ProjectBuilder updatedAt(Instant updatedAt) {
      this.updatedAt = updatedAt;
      return this;
    }

    /**
     * Build Project instance.
     *
     * @return constructed Project
     * @throws IllegalArgumentException if required fields missing
     */
    public Project build() {
      Objects.requireNonNull(projectId, "projectId is required");
      Objects.requireNonNull(workspaceId, "workspaceId is required");
      Objects.requireNonNull(name, "name is required");
      Objects.requireNonNull(ownerId, "ownerId is required");

      return new Project(
          projectId,
          workspaceId,
          name,
          description,
          ownerId,
          status,
          settings,
          metadata,
          templateId,
          createdAt,
          updatedAt);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Project project = (Project) o;
    return Objects.equals(projectId, project.projectId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(projectId);
  }

  @Override
  public String toString() {
    return "Project{"
        + "projectId='"
        + projectId
        + '\''
        + ", name='"
        + name
        + '\''
        + ", status="
        + status
        + '}';
  }
}