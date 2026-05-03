package com.ghatana.digitalmarketing.domain.attribution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing an advanced media mix modeling run.
 *
 * @doc.type class
 * @doc.purpose Domain entity for advanced attribution and media mix modeling (DMOS-F5-003)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmMediaMixModel {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String modelName;
    private final List<String> channelIds;
    private final List<DmChannelContribution> contributions;
    private final Instant dataFrom;
    private final Instant dataTo;
    private final DmMediaMixModelStatus status;
    private final double rSquared;
    private final String failureReason;
    private final Instant fittedAt;
    private final Instant createdAt;

    private DmMediaMixModel(Builder b) {
        this.id            = b.id;
        this.tenantId      = b.tenantId;
        this.workspaceId   = b.workspaceId;
        this.modelName     = b.modelName;
        this.channelIds    = List.copyOf(b.channelIds);
        this.contributions = List.copyOf(b.contributions);
        this.dataFrom      = b.dataFrom;
        this.dataTo        = b.dataTo;
        this.status        = b.status;
        this.rSquared      = b.rSquared;
        this.failureReason = b.failureReason;
        this.fittedAt      = b.fittedAt;
        this.createdAt     = b.createdAt;
    }

    public String getId()               { return id; }
    public String getTenantId()         { return tenantId; }
    public String getWorkspaceId()      { return workspaceId; }
    public String getModelName()        { return modelName; }
    public List<String> getChannelIds() { return channelIds; }
    public List<DmChannelContribution> getContributions() { return contributions; }
    public Instant getDataFrom()        { return dataFrom; }
    public Instant getDataTo()          { return dataTo; }
    public DmMediaMixModelStatus getStatus() { return status; }
    public double getRSquared()         { return rSquared; }
    public String getFailureReason()    { return failureReason; }
    public Instant getFittedAt()        { return fittedAt; }
    public Instant getCreatedAt()       { return createdAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmMediaMixModel)) return false;
        return id.equals(((DmMediaMixModel) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmMediaMixModel{id='" + id + "', status=" + status + '}';
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, modelName, failureReason;
        private List<String> channelIds = List.of();
        private List<DmChannelContribution> contributions = List.of();
        private Instant dataFrom, dataTo, fittedAt, createdAt;
        private DmMediaMixModelStatus status;
        private double rSquared;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder workspaceId(String v)             { this.workspaceId = v; return this; }
        public Builder modelName(String v)               { this.modelName = v; return this; }
        public Builder channelIds(List<String> v)        { this.channelIds = v; return this; }
        public Builder contributions(List<DmChannelContribution> v) { this.contributions = v; return this; }
        public Builder dataFrom(Instant v)               { this.dataFrom = v; return this; }
        public Builder dataTo(Instant v)                 { this.dataTo = v; return this; }
        public Builder status(DmMediaMixModelStatus v)   { this.status = v; return this; }
        public Builder rSquared(double v)                { this.rSquared = v; return this; }
        public Builder failureReason(String v)           { this.failureReason = v; return this; }
        public Builder fittedAt(Instant v)               { this.fittedAt = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }

        public DmMediaMixModel build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (modelName == null || modelName.isBlank()) throw new IllegalArgumentException("modelName must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmMediaMixModel(this);
        }
    }

    /** Attribution contribution for a single channel. */
    public record DmChannelContribution(String channelId, double contributionFraction, double roiEstimate) {
        public DmChannelContribution {
            Objects.requireNonNull(channelId, "channelId must not be null");
            if (contributionFraction < 0.0 || contributionFraction > 1.0) {
                throw new IllegalArgumentException("contributionFraction must be between 0 and 1");
            }
        }
    }
}
