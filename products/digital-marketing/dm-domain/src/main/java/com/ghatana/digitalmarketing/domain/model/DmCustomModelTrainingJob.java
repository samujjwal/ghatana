package com.ghatana.digitalmarketing.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a custom AI model training job.
 *
 * @doc.type class
 * @doc.purpose Domain entity for custom model training controls (DMOS-F5-004)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmCustomModelTrainingJob {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String modelName;
    private final String baseModelId;
    private final String trainingDataRef;
    private final DmCustomModelTrainingStatus status;
    private final double bestEvalScore;
    private final String failureReason;
    private final String artifactRef;
    private final Instant startedAt;
    private final Instant completedAt;
    private final Instant createdAt;

    private DmCustomModelTrainingJob(Builder b) {
        this.id              = b.id;
        this.tenantId        = b.tenantId;
        this.workspaceId     = b.workspaceId;
        this.modelName       = b.modelName;
        this.baseModelId     = b.baseModelId;
        this.trainingDataRef = b.trainingDataRef;
        this.status          = b.status;
        this.bestEvalScore   = b.bestEvalScore;
        this.failureReason   = b.failureReason;
        this.artifactRef     = b.artifactRef;
        this.startedAt       = b.startedAt;
        this.completedAt     = b.completedAt;
        this.createdAt       = b.createdAt;
    }

    public String getId()                  { return id; }
    public String getTenantId()            { return tenantId; }
    public String getWorkspaceId()         { return workspaceId; }
    public String getModelName()           { return modelName; }
    public String getBaseModelId()         { return baseModelId; }
    public String getTrainingDataRef()     { return trainingDataRef; }
    public DmCustomModelTrainingStatus getStatus() { return status; }
    public double getBestEvalScore()       { return bestEvalScore; }
    public String getFailureReason()       { return failureReason; }
    public String getArtifactRef()         { return artifactRef; }
    public Instant getStartedAt()          { return startedAt; }
    public Instant getCompletedAt()        { return completedAt; }
    public Instant getCreatedAt()          { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmCustomModelTrainingJob)) return false;
        return id.equals(((DmCustomModelTrainingJob) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmCustomModelTrainingJob{id='" + id + "', model='" + modelName + "', status=" + status + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, modelName, baseModelId, trainingDataRef, failureReason, artifactRef;
        private DmCustomModelTrainingStatus status;
        private double bestEvalScore;
        private Instant startedAt, completedAt, createdAt;

        public Builder id(String v)                         { this.id = v; return this; }
        public Builder tenantId(String v)                   { this.tenantId = v; return this; }
        public Builder workspaceId(String v)                { this.workspaceId = v; return this; }
        public Builder modelName(String v)                  { this.modelName = v; return this; }
        public Builder baseModelId(String v)                { this.baseModelId = v; return this; }
        public Builder trainingDataRef(String v)            { this.trainingDataRef = v; return this; }
        public Builder status(DmCustomModelTrainingStatus v) { this.status = v; return this; }
        public Builder bestEvalScore(double v)              { this.bestEvalScore = v; return this; }
        public Builder failureReason(String v)              { this.failureReason = v; return this; }
        public Builder artifactRef(String v)                { this.artifactRef = v; return this; }
        public Builder startedAt(Instant v)                 { this.startedAt = v; return this; }
        public Builder completedAt(Instant v)               { this.completedAt = v; return this; }
        public Builder createdAt(Instant v)                 { this.createdAt = v; return this; }

        public DmCustomModelTrainingJob build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName must not be blank");
            if (trainingDataRef == null || trainingDataRef.isBlank()) throw new IllegalArgumentException("trainingDataRef must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmCustomModelTrainingJob(this);
        }
    }
}
