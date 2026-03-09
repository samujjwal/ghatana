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
 * Domain model representing a security incident in the YAPPC platform.
 *
 * <p>An Incident tracks a security event that requires investigation and
 * response. It can be linked to alerts, findings, and other security
 * artifacts, and follows a defined lifecycle from creation to resolution.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a security incident requiring investigation and response
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "incidents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class Incident {

    /**
     * Unique identifier for the incident.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this incident belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * Short title describing the incident.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Detailed description of the incident.
     */
    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Severity level of the incident (e.g., CRITICAL, HIGH, MEDIUM, LOW).
     */
    @NotBlank(message = "Severity is required")
    @Size(max = 50, message = "Severity must not exceed 50 characters")
    @Column(name = "severity", nullable = false)
    private String severity;

    /**
     * Current status of the incident.
     */
    @NotBlank(message = "Status is required")
    @Size(max = 50, message = "Status must not exceed 50 characters")
    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "OPEN";

    /**
     * Priority level for response (1-5, 1 being highest).
     */
    @Column(name = "priority")
    @Builder.Default
    private int priority = 3;

    /**
     * User assigned to investigate the incident.
     */
    @Column(name = "assignee_id")
    private UUID assigneeId;

    /**
     * User who reported the incident.
     */
    @Column(name = "reporter_id")
    private UUID reporterId;

    /**
     * The project this incident is associated with.
     */
    @Column(name = "project_id")
    private UUID projectId;

    /**
     * User responsible for the incident (owner).
     */
    @Column(name = "owner_id")
    private UUID ownerId;

    /**
     * Category of the incident (e.g., DATA_BREACH, MALWARE, UNAUTHORIZED_ACCESS).
     */
    @Size(max = 100, message = "Category must not exceed 100 characters")
    @Column(name = "category")
    private String category;

    /**
     * Tags associated with the incident.
     */
    @Column(name = "tags", columnDefinition = "jsonb")
    private String tags;

    /**
     * Root cause analysis once determined.
     */
    @Size(max = 4000, message = "Root cause must not exceed 4000 characters")
    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    /**
     * Resolution notes describing how the incident was resolved.
     */
    @Size(max = 4000, message = "Resolution must not exceed 4000 characters")
    @Column(name = "resolution", columnDefinition = "text")
    private String resolution;

    /**
     * Time when the incident was detected.
     */
    @Column(name = "detected_at")
    private Instant detectedAt;

    /**
     * Time when investigation started.
     */
    @Column(name = "investigation_started_at")
    private Instant investigationStartedAt;

    /**
     * Time when the incident was resolved.
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Timestamp when the incident was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the incident was last updated.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version for optimistic locking.
     */
    @Column(name = "version")
    @Builder.Default
    private int version = 0;

    /**
     * Creates a new Incident with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param title       the incident title
     * @param severity    the severity level
     * @return a new Incident instance
     */
    public static Incident of(UUID workspaceId, String title, String severity) {
        Instant now = Instant.now();
        return Incident.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .title(Objects.requireNonNull(title, "title must not be null"))
                .severity(Objects.requireNonNull(severity, "severity must not be null"))
                .detectedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Assigns the incident to a user.
     *
     * @param userId the user to assign
     * @return this Incident for fluent chaining
     */
    public Incident assignTo(UUID userId) {
        this.assigneeId = userId;
        this.status = "ASSIGNED";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Starts investigation of the incident.
     *
     * @return this Incident for fluent chaining
     */
    public Incident startInvestigation() {
        this.status = "INVESTIGATING";
        this.investigationStartedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Resolves the incident with the given resolution notes.
     *
     * @param resolution the resolution description
     * @return this Incident for fluent chaining
     */
    public Incident resolve(String resolution) {
        this.status = "RESOLVED";
        this.resolution = resolution;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Closes the incident.
     *
     * @return this Incident for fluent chaining
     */
    public Incident close() {
        this.status = "CLOSED";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Checks if the incident is still open.
     *
     * @return true if status is OPEN or ASSIGNED
     */
    public boolean isOpen() {
        return "OPEN".equalsIgnoreCase(status) || "ASSIGNED".equalsIgnoreCase(status);
    }

    /**
     * Calculates the time to resolution in milliseconds.
     *
     * @return time to resolution in ms, or -1 if not resolved
     */
    public long getTimeToResolutionMs() {
        if (detectedAt == null || resolvedAt == null) {
            return -1;
        }
        return resolvedAt.toEpochMilli() - detectedAt.toEpochMilli();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Incident incident = (Incident) o;
        return Objects.equals(id, incident.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
