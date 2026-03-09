package com.ghatana.products.yappc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a project in the YAPPC platform.
 *
 * <p>A Project is a container for source code, configuration, and scan
 * results. It belongs to a workspace and can have multiple scan jobs
 * executed against it.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a codebase or application being monitored for security
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "projects")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Project {

    /**
     * Unique identifier for the project.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this project belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * Human-readable name for the project.
     */
    @NotBlank(message = "Project name is required")
    @Size(max = 255, message = "Project name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Optional description of the project.
     */
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description")
    private String description;

    /**
     * Repository URL (e.g., GitHub, GitLab).
     */
    @Size(max = 500, message = "Repository URL must not exceed 500 characters")
    @Column(name = "repository_url")
    private String repositoryUrl;

    /**
     * Default branch to scan.
     */
    @Size(max = 100, message = "Default branch must not exceed 100 characters")
    @Column(name = "default_branch")
    @Builder.Default
    private String defaultBranch = "main";

    /**
     * Primary programming language of the project.
     */
    @Size(max = 50, message = "Language must not exceed 50 characters")
    @Column(name = "language")
    private String language;

    /**
     * Short identifier for the project (e.g., "APP", "SVC").
     */
    @Size(max = 20, message = "Key must not exceed 20 characters")
    @Column(name = "key", length = 20)
    private String key;

    /**
     * Whether the project is archived.
     */
    @Column(name = "archived")
    @Builder.Default
    private boolean archived = false;

    /**
     * Timestamp when the project was archived (null if not archived).
     */
    @Column(name = "archived_at")
    private Instant archivedAt;

    /**
     * JSON-serialized settings for the project.
     */
    @Column(name = "settings", columnDefinition = "jsonb")
    private String settings;

    /**
     * Timestamp when the project was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the project was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Timestamp of the last successful scan.
     */
    @Column(name = "last_scan_at")
    private Instant lastScanAt;

    /**
     * Version for optimistic locking.
     */
    @Column(name = "version")
    @Builder.Default
    private int version = 0;

    /**
     * Creates a new Project with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param name        the project name
     * @return a new Project instance
     */
    public static Project of(UUID workspaceId, String name) {
        Instant now = Instant.now();
        return Project.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .name(Objects.requireNonNull(name, "name must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Archives the project.
     *
     * @return this Project for fluent chaining
     */
    public Project archive() {
        this.archived = true;
        this.archivedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Unarchives the project.
     *
     * @return this Project for fluent chaining
     */
    public Project unarchive() {
        this.archived = false;
        this.archivedAt = null;
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Records a successful scan.
     *
     * @return this Project for fluent chaining
     */
    public Project recordScan() {
        this.lastScanAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Project project = (Project) o;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
