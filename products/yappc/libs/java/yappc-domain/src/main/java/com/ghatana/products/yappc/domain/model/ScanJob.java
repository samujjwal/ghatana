package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a security scan job in the YAPPC platform.
 *
 * <p>A ScanJob tracks the execution of a security scan against a project.
 * It maintains status, timing information, and configuration details for
 * the scan run.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a security scan execution against a project
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "scan_jobs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ScanJob {

    /**
     * Unique identifier for the scan job.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this scan job belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The project being scanned.
     */
    @NotNull(message = "Project ID is required")
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /**
     * The type of scan being performed.
     */
    @NotNull(message = "Scan type is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "scan_type", nullable = false)
    private ScanType scanType;

    /**
     * Current status of the scan job.
     */
    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ScanStatus status = ScanStatus.PENDING;

    /**
     * Optional description or notes for this scan.
     */
    @Column(name = "description")
    private String description;

    /**
     * JSON-serialized scan configuration.
     */
    @Column(name = "config", columnDefinition = "jsonb")
    private String config;

    /**
     * Error message if the scan failed.
     */
    @Column(name = "error_message")
    private String errorMessage;

    /**
     * Number of findings discovered.
     */
    @Column(name = "findings_count")
    @Builder.Default
    private int findingsCount = 0;

    /**
     * Number of critical severity findings.
     */
    @Column(name = "critical_count")
    @Builder.Default
    private int criticalCount = 0;

    /**
     * Number of high severity findings.
     */
    @Column(name = "high_count")
    @Builder.Default
    private int highCount = 0;

    /**
     * Number of medium severity findings.
     */
    @Column(name = "medium_count")
    @Builder.Default
    private int mediumCount = 0;

    /**
     * Number of low severity findings.
     */
    @Column(name = "low_count")
    @Builder.Default
    private int lowCount = 0;

    /**
     * Name of the scanner used for this job.
     */
    @Column(name = "scanner_name")
    private String scannerName;

    /**
     * Version of the scanner.
     */
    @Column(name = "scanner_version")
    private String scannerVersion;

    /**
     * Target resource or path being scanned.
     */
    @Column(name = "target")
    private String target;

    /**
     * Number of informational findings.
     */
    @Column(name = "info_count")
    @Builder.Default
    private int infoCount = 0;

    /**
     * Timestamp when the scan was queued.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the scan started executing.
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Timestamp when the scan completed.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Timestamp when the record was last updated.
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
     * Creates a new pending ScanJob with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param projectId   the project ID
     * @param scanType    the type of scan
     * @return a new ScanJob instance
     */
    public static ScanJob pending(UUID workspaceId, UUID projectId, ScanType scanType) {
        Instant now = Instant.now();
        return ScanJob.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .projectId(Objects.requireNonNull(projectId, "projectId must not be null"))
                .scanType(Objects.requireNonNull(scanType, "scanType must not be null"))
                .status(ScanStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Transitions the scan job to running status.
     *
     * @return this ScanJob for fluent chaining
     */
    public ScanJob start() {
        this.status = ScanStatus.RUNNING;
        this.startedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Transitions the scan job to completed status.
     *
     * @return this ScanJob for fluent chaining
     */
    public ScanJob complete() {
        this.status = ScanStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Transitions the scan job to failed status.
     *
     * @param errorMessage the error message
     * @return this ScanJob for fluent chaining
     */
    public ScanJob fail(String errorMessage) {
        this.status = ScanStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Returns the total duration of the scan in milliseconds.
     *
     * @return duration in milliseconds, or -1 if not completed
     */
    public long getDurationMs() {
        if (startedAt == null || completedAt == null) {
            return -1;
        }
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanJob scanJob = (ScanJob) o;
        return Objects.equals(id, scanJob.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
