package com.ghatana.digitalmarketing.domain.googleads;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable mapping between an internal DMOS campaign and an external Google Ads campaign.
 *
 * @doc.type class
 * @doc.purpose Persists DMOS-to-Google campaign linkage for connector execution (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmGoogleAdsCampaignLink {

    private final String id;
    private final String tenantId;
    private final String connectorId;
    private final String internalCampaignId;
    private final String externalCampaignId;
    private final Instant createdAt;

    private DmGoogleAdsCampaignLink(Builder builder) {
        this.id = builder.id;
        this.tenantId = builder.tenantId;
        this.connectorId = builder.connectorId;
        this.internalCampaignId = builder.internalCampaignId;
        this.externalCampaignId = builder.externalCampaignId;
        this.createdAt = builder.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getConnectorId() {
        return connectorId;
    }

    public String getInternalCampaignId() {
        return internalCampaignId;
    }

    public String getExternalCampaignId() {
        return externalCampaignId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String tenantId;
        private String connectorId;
        private String internalCampaignId;
        private String externalCampaignId;
        private Instant createdAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder connectorId(String connectorId) {
            this.connectorId = connectorId;
            return this;
        }

        public Builder internalCampaignId(String internalCampaignId) {
            this.internalCampaignId = internalCampaignId;
            return this;
        }

        public Builder externalCampaignId(String externalCampaignId) {
            this.externalCampaignId = externalCampaignId;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public DmGoogleAdsCampaignLink build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            if (connectorId == null || connectorId.isBlank()) throw new IllegalArgumentException("connectorId must not be blank");
            if (internalCampaignId == null || internalCampaignId.isBlank()) {
                throw new IllegalArgumentException("internalCampaignId must not be blank");
            }
            if (externalCampaignId == null || externalCampaignId.isBlank()) {
                throw new IllegalArgumentException("externalCampaignId must not be blank");
            }
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            return new DmGoogleAdsCampaignLink(this);
        }
    }
}
