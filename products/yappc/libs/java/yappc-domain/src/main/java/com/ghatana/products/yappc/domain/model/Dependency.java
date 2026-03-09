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
 * Domain model representing a software dependency in the YAPPC platform.
 *
 * <p>A Dependency represents a third-party library or package used by a
 * project. It tracks version information, vulnerability status, and
 * license compliance.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a software dependency for SCA (Software Composition Analysis)
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "dependencies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Dependency {

    /**
     * Unique identifier for the dependency.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this dependency belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The project this dependency belongs to.
     */
    @NotNull(message = "Project ID is required")
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * Package ecosystem (e.g., npm, maven, pip, nuget).
     */
    @NotBlank(message = "Ecosystem is required")
    @Size(max = 50, message = "Ecosystem must not exceed 50 characters")
    @Column(name = "ecosystem", nullable = false)
    private String ecosystem;

    /**
     * Package name.
     */
    @NotBlank(message = "Package name is required")
    @Size(max = 255, message = "Package name must not exceed 255 characters")
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Current version used in the project.
     */
    @NotBlank(message = "Version is required")
    @Size(max = 100, message = "Version must not exceed 100 characters")
    @Column(name = "version", nullable = false)
    private String version;

    /**
     * Latest available version.
     */
    @Size(max = 100, message = "Latest version must not exceed 100 characters")
    @Column(name = "latest_version")
    private String latestVersion;

    /**
     * Whether this is a direct or transitive dependency.
     */
    @Column(name = "is_direct")
    @Builder.Default
    private boolean isDirect = true;

    /**
     * License type (e.g., MIT, Apache-2.0, GPL-3.0).
     */
    @Size(max = 100, message = "License must not exceed 100 characters")
    @Column(name = "license")
    private String license;

    /**
     * Number of known vulnerabilities.
     */
    @Column(name = "vulnerability_count")
    @Builder.Default
    private int vulnerabilityCount = 0;

    /**
     * Highest severity of known vulnerabilities.
     */
    @Size(max = 50, message = "Max severity must not exceed 50 characters")
    @Column(name = "max_severity")
    private String maxSeverity;

    /**
     * Whether the dependency is outdated.
     */
    @Column(name = "is_outdated")
    @Builder.Default
    private boolean isOutdated = false;

    /**
     * Timestamp when the dependency was discovered.
     */
    @Column(name = "discovered_at", nullable = false, updatable = false)
    private Instant discoveredAt;

    /**
     * Timestamp when the dependency was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     */
    @Column(name = "version_lock")
    @Builder.Default
    private int versionLock = 0;

    /**
     * Creates a new Dependency with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     * @param ecosystem   the package ecosystem
     * @param name        the package name
     * @param version     the current version
     * @return a new Dependency instance
     */
    public static Dependency of(UUID workspaceId, UUID projectId, String ecosystem, String name, String version) {
        Instant now = Instant.now();
        return Dependency.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .projectId(Objects.requireNonNull(projectId, "projectId must not be null"))
                .ecosystem(Objects.requireNonNull(ecosystem, "ecosystem must not be null"))
                .name(Objects.requireNonNull(name, "name must not be null"))
                .version(Objects.requireNonNull(version, "version must not be null"))
                .discoveredAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Checks if the dependency has vulnerabilities.
     *
     * @return true if there are known vulnerabilities
     */
    public boolean hasVulnerabilities() {
        return vulnerabilityCount > 0;
    }

    /**
     * Checks if an update is available.
     *
     * @return true if latest version differs from current version
     */
    public boolean hasUpdate() {
        return latestVersion != null && !latestVersion.equals(version);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dependency that = (Dependency) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
