package com.ghatana.digitalmarketing.domain.recommendation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing a machine-generated campaign recommendation.
 *
 * @doc.type class
 * @doc.purpose Domain entity for the recommendation engine output (DMOS-F3-001)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmEngineRecommendation {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String recommendationType;
    private final String rationale;
    private final double confidenceScore;
    private final List<String> supportingMetricKeys;
    private final List<String> suggestedActions;
    private final DmEngineRecommendationStatus status;
    private final Instant createdAt;
    private final Instant expiresAt;

    private DmEngineRecommendation(Builder b) {
        this.id                    = b.id;
        this.tenantId              = b.tenantId;
        this.workspaceId           = b.workspaceId;
        this.recommendationType    = b.recommendationType;
        this.rationale             = b.rationale;
        this.confidenceScore       = b.confidenceScore;
        this.supportingMetricKeys  = List.copyOf(b.supportingMetricKeys);
        this.suggestedActions      = List.copyOf(b.suggestedActions);
        this.status                = b.status;
        this.createdAt             = b.createdAt;
        this.expiresAt             = b.expiresAt;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public String getId()                    { return id; }
    public String getTenantId()              { return tenantId; }
    public String getWorkspaceId()           { return workspaceId; }
    public String getRecommendationType()    { return recommendationType; }
    public String getRationale()             { return rationale; }
    public double getConfidenceScore()       { return confidenceScore; }
    public List<String> getSupportingMetricKeys() { return supportingMetricKeys; }
    public List<String> getSuggestedActions() { return suggestedActions; }
    public DmEngineRecommendationStatus getStatus() { return status; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getExpiresAt()            { return expiresAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmEngineRecommendation)) return false;
        return id.equals(((DmEngineRecommendation) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmEngineRecommendation{id='" + id + "', type='" + recommendationType + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, recommendationType, rationale;
        private double confidenceScore;
        private List<String> supportingMetricKeys = List.of();
        private List<String> suggestedActions = List.of();
        private DmEngineRecommendationStatus status;
        private Instant createdAt, expiresAt;

        public Builder id(String v)                       { this.id = v; return this; }
        public Builder tenantId(String v)                 { this.tenantId = v; return this; }
        public Builder workspaceId(String v)              { this.workspaceId = v; return this; }
        public Builder recommendationType(String v)       { this.recommendationType = v; return this; }
        public Builder rationale(String v)                { this.rationale = v; return this; }
        public Builder confidenceScore(double v)          { this.confidenceScore = v; return this; }
        public Builder supportingMetricKeys(List<String> v) { this.supportingMetricKeys = v; return this; }
        public Builder suggestedActions(List<String> v)   { this.suggestedActions = v; return this; }
        public Builder status(DmEngineRecommendationStatus v) { this.status = v; return this; }
        public Builder createdAt(Instant v)               { this.createdAt = v; return this; }
        public Builder expiresAt(Instant v)               { this.expiresAt = v; return this; }

        public DmEngineRecommendation build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (recommendationType == null || recommendationType.isBlank()) throw new IllegalArgumentException("recommendationType must not be blank");
            if (confidenceScore < 0.0 || confidenceScore > 1.0) throw new IllegalArgumentException("confidenceScore must be between 0 and 1");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmEngineRecommendation(this);
        }
    }
}
