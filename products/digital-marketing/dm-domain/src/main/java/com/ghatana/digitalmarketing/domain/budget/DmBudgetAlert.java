package com.ghatana.digitalmarketing.domain.budget;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing a budget pacing alert.
 *
 * @doc.type class
 * @doc.purpose Domain entity for budget pacing and alerting (DMOS-F3-002)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmBudgetAlert {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String campaignId;
    private final long totalBudgetMicros;
    private final long spentMicros;
    private final double pacingRatio;
    private final DmBudgetAlertLevel level;
    private final String message;
    private final boolean acknowledged;
    private final Instant firedAt;
    private final Instant acknowledgedAt;

    private DmBudgetAlert(Builder b) {
        this.id                = b.id;
        this.tenantId          = b.tenantId;
        this.workspaceId       = b.workspaceId;
        this.campaignId        = b.campaignId;
        this.totalBudgetMicros = b.totalBudgetMicros;
        this.spentMicros       = b.spentMicros;
        this.pacingRatio       = b.pacingRatio;
        this.level             = b.level;
        this.message           = b.message;
        this.acknowledged      = b.acknowledged;
        this.firedAt           = b.firedAt;
        this.acknowledgedAt    = b.acknowledgedAt;
    }

    public DmBudgetAlert acknowledge() {
        if (acknowledged) throw new IllegalStateException("Alert already acknowledged");
        return toBuilder().acknowledged(true).acknowledgedAt(Instant.now()).build();
    }

    /** Returns remaining budget in micros. */
    public long getRemainingMicros() {
        return Math.max(0, totalBudgetMicros - spentMicros);
    }

    public String getId()                  { return id; }
    public String getTenantId()            { return tenantId; }
    public String getWorkspaceId()         { return workspaceId; }
    public String getCampaignId()          { return campaignId; }
    public long getTotalBudgetMicros()     { return totalBudgetMicros; }
    public long getSpentMicros()           { return spentMicros; }
    public double getPacingRatio()         { return pacingRatio; }
    public DmBudgetAlertLevel getLevel()   { return level; }
    public String getMessage()             { return message; }
    public boolean isAcknowledged()        { return acknowledged; }
    public Instant getFiredAt()            { return firedAt; }
    public Instant getAcknowledgedAt()     { return acknowledgedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmBudgetAlert)) return false;
        return id.equals(((DmBudgetAlert) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmBudgetAlert{id='" + id + "', level=" + level + ", acknowledged=" + acknowledged + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .campaignId(campaignId).totalBudgetMicros(totalBudgetMicros)
            .spentMicros(spentMicros).pacingRatio(pacingRatio).level(level)
            .message(message).acknowledged(acknowledged).firedAt(firedAt)
            .acknowledgedAt(acknowledgedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, campaignId, message;
        private long totalBudgetMicros, spentMicros;
        private double pacingRatio;
        private DmBudgetAlertLevel level;
        private boolean acknowledged;
        private Instant firedAt, acknowledgedAt;

        public Builder id(String v)                  { this.id = v; return this; }
        public Builder tenantId(String v)            { this.tenantId = v; return this; }
        public Builder workspaceId(String v)         { this.workspaceId = v; return this; }
        public Builder campaignId(String v)          { this.campaignId = v; return this; }
        public Builder totalBudgetMicros(long v)     { this.totalBudgetMicros = v; return this; }
        public Builder spentMicros(long v)           { this.spentMicros = v; return this; }
        public Builder pacingRatio(double v)         { this.pacingRatio = v; return this; }
        public Builder level(DmBudgetAlertLevel v)   { this.level = v; return this; }
        public Builder message(String v)             { this.message = v; return this; }
        public Builder acknowledged(boolean v)       { this.acknowledged = v; return this; }
        public Builder firedAt(Instant v)            { this.firedAt = v; return this; }
        public Builder acknowledgedAt(Instant v)     { this.acknowledgedAt = v; return this; }

        public DmBudgetAlert build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(level, "level must not be null");
            Objects.requireNonNull(firedAt, "firedAt must not be null");
            return new DmBudgetAlert(this);
        }
    }
}
