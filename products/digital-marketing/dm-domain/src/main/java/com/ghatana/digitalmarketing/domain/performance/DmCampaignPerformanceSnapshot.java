package com.ghatana.digitalmarketing.domain.performance;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable value object representing a Google Ads performance snapshot.
 *
 * @doc.type class
 * @doc.purpose Captures campaign performance metrics for a period (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DmCampaignPerformanceSnapshot {

    private final String id;
    private final String tenantId;
    private final String externalCampaignId;
    private final long impressions;
    private final long clicks;
    private final long conversions;
    private final long costMicros;
    private final double ctr;
    private final double cpc;
    private final double conversionRate;
    private final Instant periodStart;
    private final Instant periodEnd;
    private final Instant capturedAt;

    private DmCampaignPerformanceSnapshot(Builder b) {
        this.id                 = b.id;
        this.tenantId           = b.tenantId;
        this.externalCampaignId = b.externalCampaignId;
        this.impressions        = b.impressions;
        this.clicks             = b.clicks;
        this.conversions        = b.conversions;
        this.costMicros         = b.costMicros;
        this.ctr                = b.ctr;
        this.cpc                = b.cpc;
        this.conversionRate     = b.conversionRate;
        this.periodStart        = b.periodStart;
        this.periodEnd          = b.periodEnd;
        this.capturedAt         = b.capturedAt;
    }

    public String getId()                  { return id; }
    public String getTenantId()            { return tenantId; }
    public String getExternalCampaignId()  { return externalCampaignId; }
    public long getImpressions()           { return impressions; }
    public long getClicks()                { return clicks; }
    public long getConversions()           { return conversions; }
    public long getCostMicros()            { return costMicros; }
    public double getCtr()                 { return ctr; }
    public double getCpc()                 { return cpc; }
    public double getConversionRate()      { return conversionRate; }
    public Instant getPeriodStart()        { return periodStart; }
    public Instant getPeriodEnd()          { return periodEnd; }
    public Instant getCapturedAt()         { return capturedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmCampaignPerformanceSnapshot)) return false;
        return id.equals(((DmCampaignPerformanceSnapshot) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmCampaignPerformanceSnapshot{id='" + id + "', campaign='" + externalCampaignId + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, externalCampaignId;
        private long impressions, clicks, conversions, costMicros;
        private double ctr, cpc, conversionRate;
        private Instant periodStart, periodEnd, capturedAt;

        public Builder id(String v)                    { this.id = v; return this; }
        public Builder tenantId(String v)              { this.tenantId = v; return this; }
        public Builder externalCampaignId(String v)    { this.externalCampaignId = v; return this; }
        public Builder impressions(long v)             { this.impressions = v; return this; }
        public Builder clicks(long v)                  { this.clicks = v; return this; }
        public Builder conversions(long v)             { this.conversions = v; return this; }
        public Builder costMicros(long v)              { this.costMicros = v; return this; }
        public Builder ctr(double v)                   { this.ctr = v; return this; }
        public Builder cpc(double v)                   { this.cpc = v; return this; }
        public Builder conversionRate(double v)        { this.conversionRate = v; return this; }
        public Builder periodStart(Instant v)          { this.periodStart = v; return this; }
        public Builder periodEnd(Instant v)            { this.periodEnd = v; return this; }
        public Builder capturedAt(Instant v)           { this.capturedAt = v; return this; }

        public DmCampaignPerformanceSnapshot build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(externalCampaignId, "externalCampaignId must not be null");
            Objects.requireNonNull(capturedAt, "capturedAt must not be null");
            return new DmCampaignPerformanceSnapshot(this);
        }
    }
}
