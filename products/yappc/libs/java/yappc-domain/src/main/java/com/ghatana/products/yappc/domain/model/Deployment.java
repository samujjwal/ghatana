package com.ghatana.products.yappc.domain.model;

import com.ghatana.products.yappc.domain.enums.DeploymentStatus;
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
 * Deployment entity representing application deployments to environments.
 *
 * <p>
 * <b>Purpose</b><br>
 * Deployment tracks application releases to target environments (dev, staging,
 * prod). Core entity for DORA metrics and deployment frequency tracking.
 *
 * <p>
 * <b>Environment Types</b><br>
 * - development: Dev/feature environments - staging: Pre-production testing -
 * production: Live production environment - qa: QA/testing environment
 *
 * <p>
 * <b>DORA Metrics</b><br>
 * Deployments enable calculation of key DevOps metrics: - Deployment frequency:
 * Count of prod deployments per time period - Lead time: Time from commit to
 * production deployment - Change failure rate: % of deployments causing
 * incidents - Mean time to recovery (MTTR): Time to recover from failures
 *
 * <p>
 * <b>Rollback Support</b><br>
 * previous_version enables rollback to last known good version. rolled_back_at
 * tracks when rollback was executed.
 *
 * @see PipelineRun
 * @see Release
 * @doc.type class
 * @doc.purpose Deployment tracking entity
 * @doc.layer product
 * @doc.pattern Entity
 */
@Entity
@Table(name = "deployment", indexes = {
    @Index(name = "idx_deployment_workspace_env_deployed",
            columnList = "workspace_id, environment, deployed_at"),
    @Index(name = "idx_deployment_pipeline_run_id", columnList = "pipeline_run_id"),
    @Index(name = "idx_deployment_release_id", columnList = "release_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "pipeline_run_id")
    private UUID pipelineRunId;

    @Column(name = "release_id")
    private UUID releaseId;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "version", nullable = false, length = 100)
    private String version;

    @Column(name = "previous_version", length = 100)
    private String previousVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeploymentStatus status;

    @Column(name = "deployed_by")
    private UUID deployedBy;

    @Column(name = "deployed_at")
    private Instant deployedAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

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
            status = DeploymentStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment)
                || "prod".equalsIgnoreCase(environment);
    }

    public boolean isSuccessful() {
        return status == DeploymentStatus.DEPLOYED || status == DeploymentStatus.VERIFIED;
    }

    public boolean wasRolledBack() {
        return rolledBackAt != null || status == DeploymentStatus.ROLLED_BACK;
    }
}
