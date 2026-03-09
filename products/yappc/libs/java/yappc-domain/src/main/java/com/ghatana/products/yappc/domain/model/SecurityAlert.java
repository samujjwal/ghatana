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
 * Domain model representing a security alert in the YAPPC platform.
 *
 * <p>A SecurityAlert is a real-time notification triggered by security
 * events such as critical vulnerabilities, policy violations, or
 * suspicious activity detected in the monitored infrastructure.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a security alert requiring attention
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "security_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SecurityAlert {

    /**
     * Unique identifier for the alert.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this alert belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * Type of alert (e.g., VULNERABILITY, POLICY_VIOLATION, ANOMALY).
     */
    @NotBlank(message = "Alert type is required")
    @Size(max = 100, message = "Alert type must not exceed 100 characters")
    @Column(name = "alert_type", nullable = false)
    private String alertType;

    /**
     * The severity level (e.g., CRITICAL, HIGH, MEDIUM, LOW).
     */
    @NotBlank(message = "Severity is required")
    @Size(max = 50, message = "Severity must not exceed 50 characters")
    @Column(name = "severity", nullable = false)
    private String severity;

    /**
     * Short title describing the alert.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Detailed description of the alert.
     */
    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Source of the alert (e.g., SCAN, CLOUD_TRAIL, WAF).
     */
    @Size(max = 100, message = "Source must not exceed 100 characters")
    @Column(name = "source")
    private String source;

    /**
     * Related resource identifier.
     */
    @Column(name = "resource_id")
    private UUID resourceId;

    /**
     * Associated project identifier.
     */
    @Column(name = "project_id")
    private UUID projectId;

    /**
     * Associated incident identifier.
     */
    @Column(name = "incident_id")
    private UUID incidentId;

    /**
     * Detection rule ID that triggered this alert.
     */
    @Size(max = 200, message = "Rule ID must not exceed 200 characters")
    @Column(name = "rule_id")
    private String ruleId;

    /**
     * Human-readable name of the detection rule.
     */
    @Size(max = 500, message = "Rule name must not exceed 500 characters")
    @Column(name = "rule_name")
    private String ruleName;

    /**
     * Timestamp when the alert was first detected.
     */
    @Column(name = "detected_at")
    private Instant detectedAt;

    /**
     * User assigned to investigate this alert.
     */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    /**
     * JSON-serialized metadata about the alert.
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * JSON-serialized list of affected resources.
     */
    @Column(name = "affected_resources", columnDefinition = "jsonb")
    private String affectedResources;

    /**
     * Current status of the alert (e.g., OPEN, ACKNOWLEDGED, RESOLVED).
     */
    @Size(max = 50, message = "Status must not exceed 50 characters")
    @Column(name = "status")
    @Builder.Default
    private String status = "OPEN";

    /**
     * User who acknowledged the alert.
     */
    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    /**
     * Timestamp when the alert was acknowledged.
     */
    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    /**
     * User who resolved the alert.
     */
    @Column(name = "resolved_by")
    private UUID resolvedBy;

    /**
     * Timestamp when the alert was resolved.
     */
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Timestamp when the alert was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the alert was last updated.
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
     * Creates a new SecurityAlert with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param alertType   the type of alert
     * @param severity    the severity level
     * @param title       the alert title
     * @return a new SecurityAlert instance
     */
    public static SecurityAlert of(UUID workspaceId, String alertType, String severity, String title) {
        Instant now = Instant.now();
        return SecurityAlert.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .alertType(Objects.requireNonNull(alertType, "alertType must not be null"))
                .severity(Objects.requireNonNull(severity, "severity must not be null"))
                .title(Objects.requireNonNull(title, "title must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Acknowledges the alert.
     *
     * @param userId the user acknowledging the alert
     * @return this SecurityAlert for fluent chaining
     */
    public SecurityAlert acknowledge(UUID userId) {
        this.status = "ACKNOWLEDGED";
        this.acknowledgedBy = userId;
        this.acknowledgedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Resolves the alert.
     *
     * @param userId the user resolving the alert
     * @return this SecurityAlert for fluent chaining
     */
    public SecurityAlert resolve(UUID userId) {
        this.status = "RESOLVED";
        this.resolvedBy = userId;
        this.resolvedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Checks if this is a critical alert.
     *
     * @return true if severity is CRITICAL
     */
    public boolean isCritical() {
        return "CRITICAL".equalsIgnoreCase(severity);
    }

    /**
     * Checks if the alert is still open.
     *
     * @return true if status is OPEN
     */
    public boolean isOpen() {
        return "OPEN".equalsIgnoreCase(status);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecurityAlert that = (SecurityAlert) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
