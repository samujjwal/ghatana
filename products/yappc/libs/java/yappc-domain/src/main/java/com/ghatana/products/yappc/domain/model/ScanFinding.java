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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing a security finding discovered during a scan.
 *
 * <p>A ScanFinding represents a specific vulnerability, code smell, or security
 * issue discovered during a scan job. It includes severity, description,
 * location information, and remediation guidance.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a security finding from a scan job
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "scan_findings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ScanFinding {

    /**
     * Unique identifier for the finding.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this finding belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The scan job that discovered this finding.
     */
    @NotNull(message = "Scan job ID is required")
    @Column(name = "scan_job_id", nullable = false)
    private UUID scanJobId;

    /**
     * The type of finding (e.g., VULNERABILITY, CODE_SMELL, BUG).
     */
    @NotBlank(message = "Finding type is required")
    @Size(max = 100, message = "Finding type must not exceed 100 characters")
    @Column(name = "finding_type", nullable = false)
    private String findingType;

    /**
     * The severity level (e.g., CRITICAL, HIGH, MEDIUM, LOW, INFO).
     */
    @NotBlank(message = "Severity is required")
    @Size(max = 50, message = "Severity must not exceed 50 characters")
    @Column(name = "severity", nullable = false)
    private String severity;

    /**
     * A short title describing the finding.
     */
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Detailed description of the finding.
     */
    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    @Column(name = "description", columnDefinition = "text")
    private String description;

    /**
     * Location information as a JSON map (file path, line numbers, etc.).
     */
    @Column(name = "location", columnDefinition = "jsonb")
    private Map<String, Object> location;

    /**
     * Suggested remediation steps.
     */
    @Size(max = 4000, message = "Remediation must not exceed 4000 characters")
    @Column(name = "remediation", columnDefinition = "text")
    private String remediation;

    /**
     * Current status of the finding (e.g., OPEN, RESOLVED, IGNORED).
     */
    @Size(max = 50, message = "Status must not exceed 50 characters")
    @Column(name = "status")
    @Builder.Default
    private String status = "OPEN";

    /**
     * Flag indicating if this finding has been marked as a false positive.
     */
    @Column(name = "false_positive")
    @Builder.Default
    private boolean falsePositive = false;

    /**
     * CWE (Common Weakness Enumeration) identifier if applicable.
     */
    @Column(name = "cwe_id")
    private String cweId;

    /**
     * CVE (Common Vulnerabilities and Exposures) identifier if applicable.
     */
    @Column(name = "cve_id")
    private String cveId;

    /**
     * CVSS (Common Vulnerability Scoring System) score if applicable.
     */
    @Column(name = "cvss_score")
    private Double cvssScore;

    /**
     * Timestamp when the finding was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the finding was last updated.
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
     * Creates a new ScanFinding with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param scanJobId   the scan job ID
     * @param findingType the type of finding
     * @param severity    the severity level
     * @param title       the finding title
     * @return a new ScanFinding instance
     */
    public static ScanFinding of(UUID workspaceId, UUID scanJobId, String findingType, String severity, String title) {
        Instant now = Instant.now();
        return ScanFinding.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .scanJobId(Objects.requireNonNull(scanJobId, "scanJobId must not be null"))
                .findingType(Objects.requireNonNull(findingType, "findingType must not be null"))
                .severity(Objects.requireNonNull(severity, "severity must not be null"))
                .title(Objects.requireNonNull(title, "title must not be null"))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Marks this finding as resolved.
     *
     * @return this ScanFinding for fluent chaining
     */
    public ScanFinding resolve() {
        this.status = "RESOLVED";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Marks this finding as a false positive.
     *
     * @return this ScanFinding for fluent chaining
     */
    public ScanFinding markFalsePositive() {
        this.falsePositive = true;
        this.status = "FALSE_POSITIVE";
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Checks if this is a critical or high severity finding.
     *
     * @return true if severity is CRITICAL or HIGH
     */
    public boolean isCriticalOrHigh() {
        return "CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanFinding that = (ScanFinding) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
