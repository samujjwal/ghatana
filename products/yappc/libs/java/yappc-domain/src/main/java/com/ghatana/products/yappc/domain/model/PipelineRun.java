package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.PipelineStatus;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Pipeline run entity representing CI/CD pipeline execution instances.
 *
 * <p>
 * <b>Purpose</b><br>
 * PipelineRun tracks individual executions of CI/CD pipelines. Each run
 * represents one triggered execution from commit to completion.
 *
 * <p>
 * <b>Trigger Sources</b><br>
 * - Git commit/push (most common) - Pull request/merge request - Manual trigger
 * by user - Scheduled/cron trigger - API trigger (external system)
 *
 * <p>
 * <b>Duration Tracking</b><br>
 * Duration calculated as: completed_at - started_at Used for DORA metrics: -
 * Deployment frequency - Lead time for changes - Mean time to recovery (MTTR)
 *
 * <p>
 * <b>JSONB Storage</b><br>
 * - stage_results: Per-stage execution results (build, test, deploy) -
 * artifacts: Build artifacts produced (URLs, checksums)
 *
 * @see Pipeline
 * @see Deployment
 * @doc.type class
 * @doc.purpose CI/CD pipeline run tracking entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "pipeline_run", indexes = {
    @Index(name = "idx_pipeline_run_pipeline_started",
            columnList = "pipeline_id, started_at"),
    @Index(name = "idx_pipeline_run_workspace_status",
            columnList = "workspace_id, status, started_at"),
    @Index(name = "idx_pipeline_run_triggered_by", columnList = "triggered_by")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "pipeline_id", nullable = false)
    private UUID pipelineId;

    @Column(name = "run_number", nullable = false)
    private Integer runNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PipelineStatus status;

    @Column(name = "commit_sha", length = 100)
    private String commitSha;

    @Column(name = "branch", length = 200)
    private String branch;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "trigger_source", length = 50)
    private String triggerSource;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Type(JsonBinaryType.class)
    @Column(name = "stage_results", columnDefinition = "jsonb")
    private Map<String, Object> stageResults;

    @Type(JsonBinaryType.class)
    @Column(name = "artifacts", columnDefinition = "jsonb")
    private Map<String, Object> artifacts;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = PipelineStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();

        // Calculate duration if completed
        if (completedAt != null && startedAt != null && durationSeconds == null) {
            durationSeconds = (int) (completedAt.getEpochSecond() - startedAt.getEpochSecond());
        }
    }

    public boolean isCompleted() {
        return status == PipelineStatus.SUCCESS
                || status == PipelineStatus.FAILED
                || status == PipelineStatus.CANCELLED;
    }

    public boolean isSuccessful() {
        return status == PipelineStatus.SUCCESS;
    }
}
