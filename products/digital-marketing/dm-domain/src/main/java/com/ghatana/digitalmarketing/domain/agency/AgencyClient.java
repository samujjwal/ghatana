package com.ghatana.digitalmarketing.domain.agency;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;

/**
 * Domain entity representing an agency client (DMOS-P3-003).
 *
 * @doc.type class
 * @doc.purpose Represents an agency client in agency mode
 * @doc.layer domain
 */
public final class AgencyClient {

    private final String clientId;
    private final DmTenantId tenantId;
    private final DmWorkspaceId workspaceId;
    private final String clientName;
    private final String contactEmail;
    private final String contactPhone;
    private final String brandingTheme;
    private final boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;

    private AgencyClient(Builder builder) {
        this.clientId = builder.clientId;
        this.tenantId = builder.tenantId;
        this.workspaceId = builder.workspaceId;
        this.clientName = builder.clientName;
        this.contactEmail = builder.contactEmail;
        this.contactPhone = builder.contactPhone;
        this.brandingTheme = builder.brandingTheme;
        this.active = builder.active;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public String getClientId() {
        return clientId;
    }

    public DmTenantId getTenantId() {
        return tenantId;
    }

    public DmWorkspaceId getWorkspaceId() {
        return workspaceId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getBrandingTheme() {
        return brandingTheme;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String clientId;
        private DmTenantId tenantId;
        private DmWorkspaceId workspaceId;
        private String clientName;
        private String contactEmail;
        private String contactPhone;
        private String brandingTheme;
        private boolean active = true;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder clientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public Builder tenantId(DmTenantId tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder workspaceId(DmWorkspaceId workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = clientName;
            return this;
        }

        public Builder contactEmail(String contactEmail) {
            this.contactEmail = contactEmail;
            return this;
        }

        public Builder contactPhone(String contactPhone) {
            this.contactPhone = contactPhone;
            return this;
        }

        public Builder brandingTheme(String brandingTheme) {
            this.brandingTheme = brandingTheme;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public AgencyClient build() {
            return new AgencyClient(this);
        }
    }
}
