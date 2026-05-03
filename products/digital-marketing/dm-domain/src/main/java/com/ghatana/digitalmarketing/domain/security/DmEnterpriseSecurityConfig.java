package com.ghatana.digitalmarketing.domain.security;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entity representing enterprise security configuration for a tenant.
 *
 * @doc.type class
 * @doc.purpose Domain entity for enterprise security features (DMOS-F4-005)
 * @doc.layer product
 * @doc.pattern Entity
 */
public final class DmEnterpriseSecurityConfig {

    private final String id;
    private final String tenantId;
    private final boolean mfaRequired;
    private final boolean ipAllowlistEnabled;
    private final List<String> allowedIpCidrs;
    private final boolean auditLogEnabled;
    private final int sessionTimeoutMinutes;
    private final String ssoProvider;
    private final String ssoMetadataUrl;
    private final Instant createdAt;
    private final Instant updatedAt;

    private DmEnterpriseSecurityConfig(Builder b) {
        this.id                    = b.id;
        this.tenantId              = b.tenantId;
        this.mfaRequired           = b.mfaRequired;
        this.ipAllowlistEnabled    = b.ipAllowlistEnabled;
        this.allowedIpCidrs        = List.copyOf(b.allowedIpCidrs);
        this.auditLogEnabled       = b.auditLogEnabled;
        this.sessionTimeoutMinutes = b.sessionTimeoutMinutes;
        this.ssoProvider           = b.ssoProvider;
        this.ssoMetadataUrl        = b.ssoMetadataUrl;
        this.createdAt             = b.createdAt;
        this.updatedAt             = b.updatedAt;
    }

    public String getId()                  { return id; }
    public String getTenantId()            { return tenantId; }
    public boolean isMfaRequired()         { return mfaRequired; }
    public boolean isIpAllowlistEnabled()  { return ipAllowlistEnabled; }
    public List<String> getAllowedIpCidrs() { return allowedIpCidrs; }
    public boolean isAuditLogEnabled()     { return auditLogEnabled; }
    public int getSessionTimeoutMinutes()  { return sessionTimeoutMinutes; }
    public String getSsoProvider()         { return ssoProvider; }
    public String getSsoMetadataUrl()      { return ssoMetadataUrl; }
    public Instant getCreatedAt()          { return createdAt; }
    public Instant getUpdatedAt()          { return updatedAt; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DmEnterpriseSecurityConfig)) return false;
        return id.equals(((DmEnterpriseSecurityConfig) o).id);
    }
    @Override public int hashCode() { return id.hashCode(); }
    @Override public String toString() {
        return "DmEnterpriseSecurityConfig{id='" + id + "', tenantId='" + tenantId + "'}";
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id, tenantId, ssoProvider, ssoMetadataUrl;
        private boolean mfaRequired;
        private boolean ipAllowlistEnabled;
        private List<String> allowedIpCidrs = List.of();
        private boolean auditLogEnabled = true;
        private int sessionTimeoutMinutes = 60;
        private Instant createdAt, updatedAt;

        public Builder id(String v)                   { this.id = v; return this; }
        public Builder tenantId(String v)             { this.tenantId = v; return this; }
        public Builder mfaRequired(boolean v)         { this.mfaRequired = v; return this; }
        public Builder ipAllowlistEnabled(boolean v)  { this.ipAllowlistEnabled = v; return this; }
        public Builder allowedIpCidrs(List<String> v) { this.allowedIpCidrs = v; return this; }
        public Builder auditLogEnabled(boolean v)     { this.auditLogEnabled = v; return this; }
        public Builder sessionTimeoutMinutes(int v)   { this.sessionTimeoutMinutes = v; return this; }
        public Builder ssoProvider(String v)          { this.ssoProvider = v; return this; }
        public Builder ssoMetadataUrl(String v)       { this.ssoMetadataUrl = v; return this; }
        public Builder createdAt(Instant v)           { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v)           { this.updatedAt = v; return this; }

        public DmEnterpriseSecurityConfig build() {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
            Objects.requireNonNull(allowedIpCidrs, "allowedIpCidrs must not be null");
            return new DmEnterpriseSecurityConfig(this);
        }
    }
}
