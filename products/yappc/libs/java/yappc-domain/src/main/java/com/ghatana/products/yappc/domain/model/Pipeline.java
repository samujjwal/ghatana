package com.ghatana.products.yappc.domain.model;

import com.vladmihalcea.hibernate.type.array.StringArrayType;
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
 * Pipeline entity representing CI/CD pipeline definitions.
 *
 * <p>
 * <b>Purpose</b><br>
 * Pipeline defines CI/CD automation workflows for build, test, and deployment.
 * Parent entity for PipelineRuns tracking individual executions.
 *
 * <p>
 * <b>CI/CD Platform Support</b><br>
 * - GitHub Actions - GitLab CI/CD - Jenkins - CircleCI - Azure DevOps Pipelines
 * - AWS CodePipeline - Custom pipelines
 *
 * <p>
 * <b>Pipeline Configuration</b><br>
 * config JSONB stores platform-specific configuration: - triggers:
 * Webhook/schedule/manual triggers - stages: Build → Test → Deploy stages -
 * environment variables - quality gates and approval gates
 *
 * <p>
 * <b>Metrics Tracking</b><br>
 * - average_duration_seconds: Average execution time - success_rate: Percentage
 * of successful runs - Used for DORA metrics (deployment frequency, lead time)
 *
 * @see PipelineRun
 * @see Deployment
 * @doc.type class
 * @doc.purpose CI/CD pipeline definition entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "pipeline", indexes = {
    @Index(name = "idx_pipeline_workspace_name", columnList = "workspace_id, name"),
    @Index(name = "idx_pipeline_project_active", columnList = "project_id, is_active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pipeline {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "platform", length = 50)
    private String platform;

    @Column(name = "repository_url", length = 1000)
    private String repositoryUrl;

    @Column(name = "branch", length = 200)
    private String branch;

    @Type(JsonBinaryType.class)
    @Column(name = "config", columnDefinition = "jsonb")
    private Map<String, Object> config;

    @Type(StringArrayType.class)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "average_duration_seconds")
    private Integer averageDurationSeconds;

    @Column(name = "success_rate", precision = 5, scale = 2)
    private Double successRate;

    @Column(name = "last_run_at")
    private Instant lastRunAt;

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
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
