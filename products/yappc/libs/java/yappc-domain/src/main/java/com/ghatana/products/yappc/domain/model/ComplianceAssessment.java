package com.ghatana.products.yappc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Domain model representing a compliance assessment in the YAPPC platform.
 *
 * <p>A ComplianceAssessment evaluates a workspace against a specific
 * compliance framework, tracking the pass/fail status of individual
 * controls and calculating an overall compliance score.</p>
 *
 * @doc.type class
 * @doc.purpose Represents a compliance assessment result against a framework
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "compliance_assessments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class ComplianceAssessment {

    /**
     * Unique identifier for the assessment.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The workspace this assessment belongs to.
     */
    @NotNull(message = "Workspace ID is required")
    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    /**
     * The compliance framework being assessed.
     */
    @NotNull(message = "Framework ID is required")
    @Column(name = "framework_id", nullable = false)
    private UUID frameworkId;

    /**
     * Associated project identifier.
     */
    @Column(name = "project_id")
    private UUID projectId;

    /**
     * Date the assessment was performed.
     */
    @Column(name = "assessment_date")
    private java.time.LocalDate assessmentDate;

    /**
     * Due date for the assessment.
     */
    @Column(name = "due_date")
    private java.time.LocalDate dueDate;

    /**
     * Name of the assessor.
     */
    @Size(max = 200, message = "Assessor name must not exceed 200 characters")
    @Column(name = "assessor_name")
    private String assessorName;

    /**
     * Type of assessment (e.g., INTERNAL, EXTERNAL, AUTOMATED).
     */
    @Size(max = 50, message = "Assessment type must not exceed 50 characters")
    @Column(name = "assessment_type")
    private String assessmentType;

    /**
     * Free-text notes about the assessment.
     */
    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    /**
     * Overall compliance score (0-100).
     */
    @Min(0)
    @Max(100)
    @Column(name = "score")
    @Builder.Default
    private int score = 0;

    /**
     * Number of controls that passed.
     */
    @Column(name = "passed_controls")
    @Builder.Default
    private int passedControls = 0;

    /**
     * Number of controls that failed.
     */
    @Column(name = "failed_controls")
    @Builder.Default
    private int failedControls = 0;

    /**
     * Number of controls not applicable.
     */
    @Column(name = "na_controls")
    @Builder.Default
    private int naControls = 0;

    /**
     * Total number of controls assessed.
     */
    @Column(name = "total_controls")
    @Builder.Default
    private int totalControls = 0;

    /**
     * Current status of the assessment.
     */
    @Size(max = 50, message = "Status must not exceed 50 characters")
    @Column(name = "status")
    @Builder.Default
    private String status = "IN_PROGRESS";

    /**
     * JSON-serialized assessment details.
     */
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    /**
     * Timestamp when the assessment was started.
     */
    @Column(name = "started_at")
    private Instant startedAt;

    /**
     * Timestamp when the assessment was completed.
     */
    @Column(name = "assessed_at")
    private Instant assessedAt;

    /**
     * Timestamp when the assessment was created.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Timestamp when the assessment was last updated.
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
     * Creates a new ComplianceAssessment with the minimum required fields.
     *
     * @param workspaceId the workspace ID
     * @param frameworkId the framework ID
     * @return a new ComplianceAssessment instance
     */
    public static ComplianceAssessment of(UUID workspaceId, UUID frameworkId) {
        Instant now = Instant.now();
        return ComplianceAssessment.builder()
                .workspaceId(Objects.requireNonNull(workspaceId, "workspaceId must not be null"))
                .frameworkId(Objects.requireNonNull(frameworkId, "frameworkId must not be null"))
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Calculates and updates the compliance score.
     *
     * @return this ComplianceAssessment for fluent chaining
     */
    public ComplianceAssessment calculateScore() {
        int applicableControls = totalControls - naControls;
        if (applicableControls > 0) {
            this.score = (int) Math.round((double) passedControls / applicableControls * 100);
        }
        this.updatedAt = Instant.now();
        return this;
    }

    /**
     * Completes the assessment.
     *
     * @return this ComplianceAssessment for fluent chaining
     */
    public ComplianceAssessment complete() {
        this.status = "COMPLETED";
        this.assessedAt = Instant.now();
        this.updatedAt = Instant.now();
        return calculateScore();
    }

    /**
     * Checks if the assessment is passing (score >= 80).
     *
     * @return true if passing
     */
    public boolean isPassing() {
        return score >= 80;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ComplianceAssessment that = (ComplianceAssessment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
