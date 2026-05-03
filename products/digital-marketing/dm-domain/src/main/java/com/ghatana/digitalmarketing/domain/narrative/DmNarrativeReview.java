package com.ghatana.digitalmarketing.domain.narrative;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a weekly/monthly narrative performance review.
 *
 * @doc.type class
 * @doc.purpose Domain entity for AI-generated narrative performance reviews (DMOS-F3-006)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmNarrativeReview {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final DmNarrativePeriodType periodType;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final String narrativeText;
    private final String keyInsights;
    private final String recommendations;
    private final DmNarrativeReviewStatus status;
    private final Instant generatedAt;
    private final Instant createdAt;

    private DmNarrativeReview(Builder b) {
        this.id              = b.id;
        this.tenantId        = b.tenantId;
        this.workspaceId     = b.workspaceId;
        this.periodType      = b.periodType;
        this.periodStart     = b.periodStart;
        this.periodEnd       = b.periodEnd;
        this.narrativeText   = b.narrativeText;
        this.keyInsights     = b.keyInsights;
        this.recommendations = b.recommendations;
        this.status          = b.status;
        this.generatedAt     = b.generatedAt;
        this.createdAt       = b.createdAt;
    }

    public String getId()                 { return id; }
    public String getTenantId()           { return tenantId; }
    public String getWorkspaceId()        { return workspaceId; }
    public DmNarrativePeriodType getPeriodType() { return periodType; }
    public Instant getPeriodStart()       { return periodStart; }
    public Instant getPeriodEnd()         { return periodEnd; }
    public String getNarrativeText()      { return narrativeText; }
    public String getKeyInsights()        { return keyInsights; }
    public String getRecommendations()    { return recommendations; }
    public DmNarrativeReviewStatus getStatus() { return status; }
    public Instant getGeneratedAt()       { return generatedAt; }
    public Instant getCreatedAt()         { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmNarrativeReview)) return false;
        return id.equals(((DmNarrativeReview) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmNarrativeReview{id='" + id + "', type=" + periodType + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, narrativeText, keyInsights, recommendations;
        private DmNarrativePeriodType periodType;
        private Instant periodStart, periodEnd, generatedAt, createdAt;
        private DmNarrativeReviewStatus status;

        public Builder id(String v)                     { this.id = v; return this; }
        public Builder tenantId(String v)               { this.tenantId = v; return this; }
        public Builder workspaceId(String v)            { this.workspaceId = v; return this; }
        public Builder periodType(DmNarrativePeriodType v) { this.periodType = v; return this; }
        public Builder periodStart(Instant v)           { this.periodStart = v; return this; }
        public Builder periodEnd(Instant v)             { this.periodEnd = v; return this; }
        public Builder narrativeText(String v)          { this.narrativeText = v; return this; }
        public Builder keyInsights(String v)            { this.keyInsights = v; return this; }
        public Builder recommendations(String v)        { this.recommendations = v; return this; }
        public Builder status(DmNarrativeReviewStatus v) { this.status = v; return this; }
        public Builder generatedAt(Instant v)           { this.generatedAt = v; return this; }
        public Builder createdAt(Instant v)             { this.createdAt = v; return this; }

        public DmNarrativeReview build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(periodType, "periodType must not be null");
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmNarrativeReview(this);
        }
    }
}
