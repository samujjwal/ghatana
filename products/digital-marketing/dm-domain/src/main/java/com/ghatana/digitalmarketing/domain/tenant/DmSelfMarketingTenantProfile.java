package com.ghatana.digitalmarketing.domain.tenant;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable entity representing self-marketing tenant isolation configuration.
 *
 * @doc.type class
 * @doc.purpose Enforces tenant boundaries for self-marketing use (DMOS-F2-020)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmSelfMarketingTenantProfile {

    private final String id;
    private final String tenantId;
    private final String displayName;
    private final String industry;
    private final String timezone;
    private final String defaultCurrency;
    private final int maxActiveConnectors;
    private final int maxCampaignsPerMonth;
    private final long maxMonthlyBudgetMicros;
    private final boolean killSwitchEnabled;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmSelfMarketingTenantProfile(Builder b) {
        this.id                    = b.id;
        this.tenantId              = b.tenantId;
        this.displayName           = b.displayName;
        this.industry              = b.industry;
        this.timezone              = b.timezone;
        this.defaultCurrency       = b.defaultCurrency;
        this.maxActiveConnectors   = b.maxActiveConnectors;
        this.maxCampaignsPerMonth  = b.maxCampaignsPerMonth;
        this.maxMonthlyBudgetMicros = b.maxMonthlyBudgetMicros;
        this.killSwitchEnabled     = b.killSwitchEnabled;
        this.createdAt             = b.createdAt;
        this.updatedAt             = b.updatedAt;
    }

    public String getId()                   { return id; }
    public String getTenantId()             { return tenantId; }
    public String getDisplayName()          { return displayName; }
    public String getIndustry()             { return industry; }
    public String getTimezone()             { return timezone; }
    public String getDefaultCurrency()      { return defaultCurrency; }
    public int getMaxActiveConnectors()     { return maxActiveConnectors; }
    public int getMaxCampaignsPerMonth()    { return maxCampaignsPerMonth; }
    public long getMaxMonthlyBudgetMicros() { return maxMonthlyBudgetMicros; }
    public boolean isKillSwitchEnabled()    { return killSwitchEnabled; }
    public Instant getCreatedAt()           { return createdAt; }
    public Instant getUpdatedAt()           { return updatedAt; }

    /** Returns true if the given budget is within this tenant's limit. */
    public boolean isBudgetAllowed(long budgetMicros) {
        return budgetMicros <= maxMonthlyBudgetMicros;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmSelfMarketingTenantProfile)) return false;
        return id.equals(((DmSelfMarketingTenantProfile) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmSelfMarketingTenantProfile{id='" + id + "', tenantId='" + tenantId + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, displayName, industry, timezone, defaultCurrency;
        private int maxActiveConnectors = 5;
        private int maxCampaignsPerMonth = 10;
        private long maxMonthlyBudgetMicros = 10_000_000_000L; // 10,000 USD
        private boolean killSwitchEnabled = false;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                      { this.id = v; return this; }
        public Builder tenantId(String v)                { this.tenantId = v; return this; }
        public Builder displayName(String v)             { this.displayName = v; return this; }
        public Builder industry(String v)                { this.industry = v; return this; }
        public Builder timezone(String v)                { this.timezone = v; return this; }
        public Builder defaultCurrency(String v)         { this.defaultCurrency = v; return this; }
        public Builder maxActiveConnectors(int v)        { this.maxActiveConnectors = v; return this; }
        public Builder maxCampaignsPerMonth(int v)       { this.maxCampaignsPerMonth = v; return this; }
        public Builder maxMonthlyBudgetMicros(long v)    { this.maxMonthlyBudgetMicros = v; return this; }
        public Builder killSwitchEnabled(boolean v)      { this.killSwitchEnabled = v; return this; }
        public Builder createdAt(Instant v)              { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)              { this.updatedAt = v; return this; }

        public DmSelfMarketingTenantProfile build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmSelfMarketingTenantProfile(this);
        }
    }
}
