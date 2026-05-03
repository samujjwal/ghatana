package com.ghatana.digitalmarketing.domain.campaign;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable entity representing a Google Search campaign creation request.
 *
 * @doc.type class
 * @doc.purpose Domain entity for Google Search campaign configuration (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmCampaignCreationRequest {

    private final String id;
    private final String tenantId;
    private final String workspaceId;
    private final String connectorId;
    private final String campaignName;
    private final long dailyBudgetMicros;
    private final String targetLocationCode;
    private final String bidStrategy;
    private final Map<String, String> adGroupConfig;
    private final DmCampaignCreationStatus status;
    private final String externalCampaignId;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmCampaignCreationRequest(Builder b) {
        this.id                 = b.id;
        this.tenantId           = b.tenantId;
        this.workspaceId        = b.workspaceId;
        this.connectorId        = b.connectorId;
        this.campaignName       = b.campaignName;
        this.dailyBudgetMicros  = b.dailyBudgetMicros;
        this.targetLocationCode = b.targetLocationCode;
        this.bidStrategy        = b.bidStrategy;
        this.adGroupConfig      = Map.copyOf(b.adGroupConfig);
        this.status             = b.status;
        this.externalCampaignId = b.externalCampaignId;
        this.failureReason      = b.failureReason;
        this.createdAt          = b.createdAt;
        this.updatedAt          = b.updatedAt;
    }

    public DmCampaignCreationRequest markSubmitted(String externalCampaignId) {
        Objects.requireNonNull(externalCampaignId, "externalCampaignId must not be null");
        if (status != DmCampaignCreationStatus.PENDING) {
            throw new IllegalStateException("markSubmitted requires PENDING status, was: " + status);
        }
        return toBuilder().status(DmCampaignCreationStatus.SUBMITTED)
            .externalCampaignId(externalCampaignId).updatedAt(Instant.now()).build();
    }

    public DmCampaignCreationRequest markFailed(String reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return toBuilder().status(DmCampaignCreationStatus.FAILED)
            .failureReason(reason).updatedAt(Instant.now()).build();
    }

    public String getId()                  { return id; }
    public String getTenantId()            { return tenantId; }
    public String getWorkspaceId()         { return workspaceId; }
    public String getConnectorId()         { return connectorId; }
    public String getCampaignName()        { return campaignName; }
    public long getDailyBudgetMicros()     { return dailyBudgetMicros; }
    public String getTargetLocationCode()  { return targetLocationCode; }
    public String getBidStrategy()         { return bidStrategy; }
    public Map<String, String> getAdGroupConfig() { return adGroupConfig; }
    public DmCampaignCreationStatus getStatus() { return status; }
    public String getExternalCampaignId()  { return externalCampaignId; }
    public String getFailureReason()       { return failureReason; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmCampaignCreationRequest)) return false;
        return id.equals(((DmCampaignCreationRequest) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmCampaignCreationRequest{id='" + id + "', status=" + status + '}';
    }

    public Builder toBuilder() {
        return new Builder().id(id).tenantId(tenantId).workspaceId(workspaceId)
            .connectorId(connectorId).campaignName(campaignName)
            .dailyBudgetMicros(dailyBudgetMicros).targetLocationCode(targetLocationCode)
            .bidStrategy(bidStrategy).adGroupConfig(adGroupConfig).status(status)
            .externalCampaignId(externalCampaignId).failureReason(failureReason)
            .createdAt(createdAt).updatedAt(updatedAt);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, workspaceId, connectorId, campaignName;
        private long dailyBudgetMicros;
        private String targetLocationCode, bidStrategy;
        private Map<String, String> adGroupConfig = Map.of();
        private DmCampaignCreationStatus status;
        private String externalCampaignId, failureReason;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                          { this.id = v; return this; }
        public Builder tenantId(String v)                    { this.tenantId = v; return this; }
        public Builder workspaceId(String v)                 { this.workspaceId = v; return this; }
        public Builder connectorId(String v)                 { this.connectorId = v; return this; }
        public Builder campaignName(String v)                { this.campaignName = v; return this; }
        public Builder dailyBudgetMicros(long v)             { this.dailyBudgetMicros = v; return this; }
        public Builder targetLocationCode(String v)          { this.targetLocationCode = v; return this; }
        public Builder bidStrategy(String v)                 { this.bidStrategy = v; return this; }
        public Builder adGroupConfig(Map<String, String> v)  { this.adGroupConfig = v; return this; }
        public Builder status(DmCampaignCreationStatus v)    { this.status = v; return this; }
        public Builder externalCampaignId(String v)          { this.externalCampaignId = v; return this; }
        public Builder failureReason(String v)               { this.failureReason = v; return this; }
        public Builder createdAt(Instant v)                  { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)                  { this.updatedAt = v; return this; }

        public DmCampaignCreationRequest build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (connectorId == null || connectorId.isBlank()) throw new IllegalArgumentException("connectorId must not be blank");
            if (campaignName == null || campaignName.isBlank()) throw new IllegalArgumentException("campaignName must not be blank");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(adGroupConfig, "adGroupConfig must not be null");
            return new DmCampaignCreationRequest(this);
        }
    }
}
