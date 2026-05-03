package com.ghatana.digitalmarketing.domain.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an agent evaluation result.
 *
 * @doc.type class
 * @doc.purpose Domain entity for agent evaluation suite results (DMOS-F3-005)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmAgentEvaluation {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String agentId;
    private final String agentType;
    private final List<DmEvalMetric> metrics;
    private final double overallScore;
    private final String verdict;
    private final String evaluatedBy;
    private final Instant evaluatedAt;
    private final Instant createdAt;

    private DmAgentEvaluation(Builder b) {
        this.id           = b.id;
        this.tenantId     = b.tenantId;
        this.workspaceId  = b.workspaceId;
        this.agentId      = b.agentId;
        this.agentType    = b.agentType;
        this.metrics      = List.copyOf(b.metrics);
        this.overallScore = b.overallScore;
        this.verdict      = b.verdict;
        this.evaluatedBy  = b.evaluatedBy;
        this.evaluatedAt  = b.evaluatedAt;
        this.createdAt    = b.createdAt;
    }

    public String getId()            { return id; }
    public String getTenantId()      { return tenantId; }
    public String getWorkspaceId()   { return workspaceId; }
    public String getAgentId()       { return agentId; }
    public String getAgentType()     { return agentType; }
    public List<DmEvalMetric> getMetrics() { return metrics; }
    public double getOverallScore()  { return overallScore; }
    public String getVerdict()       { return verdict; }
    public String getEvaluatedBy()   { return evaluatedBy; }
    public Instant getEvaluatedAt()  { return evaluatedAt; }
    public Instant getCreatedAt()    { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmAgentEvaluation)) return false;
        return id.equals(((DmAgentEvaluation) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmAgentEvaluation{id='" + id + "', agentId='" + agentId + "', score=" + overallScore + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, agentId, agentType, verdict, evaluatedBy;
        private List<DmEvalMetric> metrics = List.of();
        private double overallScore;
        private Instant evaluatedAt, createdAt;

        public Builder id(String v)             { this.id = v; return this; }
        public Builder tenantId(String v)       { this.tenantId = v; return this; }
        public Builder workspaceId(String v)    { this.workspaceId = v; return this; }
        public Builder agentId(String v)        { this.agentId = v; return this; }
        public Builder agentType(String v)      { this.agentType = v; return this; }
        public Builder metrics(List<DmEvalMetric> v) { this.metrics = v; return this; }
        public Builder overallScore(double v)   { this.overallScore = v; return this; }
        public Builder verdict(String v)        { this.verdict = v; return this; }
        public Builder evaluatedBy(String v)    { this.evaluatedBy = v; return this; }
        public Builder evaluatedAt(Instant v)   { this.evaluatedAt = v; return this; }
        public Builder createdAt(Instant v)     { this.createdAt = v; return this; }

        public DmAgentEvaluation build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId must not be blank");
            if (overallScore < 0.0 || overallScore > 1.0) throw new IllegalArgumentException("overallScore must be between 0 and 1");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(metrics, "metrics must not be null");
            return new DmAgentEvaluation(this);
        }
    }

    /** A single evaluation metric. */
    public record DmEvalMetric(String name, double score, String explanation) {
        public DmEvalMetric {
            Objects.requireNonNull(name, "name must not be null");
            if (score < 0.0 || score > 1.0) throw new IllegalArgumentException("score must be between 0 and 1");
        }
    }
}
