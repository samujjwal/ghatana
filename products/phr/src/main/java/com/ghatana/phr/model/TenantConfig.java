package com.ghatana.phr.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Configuration and setup for Tenant
 *
 * @doc.type class
 * @doc.purpose Configuration and setup for Tenant
 * @doc.layer product
 * @doc.pattern Configuration
 */
public class TenantConfig {
    private String tenantId;
    private String tenantName;
    private Set<String> allowedRegions = new HashSet<>();
    private boolean hipaaCompliant;
    private boolean mfaRequired;

    public TenantConfig() {
    }

    public TenantConfig(String tenantId, String tenantName) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.hipaaCompliant = true;
        this.mfaRequired = true;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public Set<String> getAllowedRegions() {
        return allowedRegions;
    }

    public void setAllowedRegions(Set<String> allowedRegions) {
        this.allowedRegions = allowedRegions;
    }

    public void addAllowedRegion(String region) {
        this.allowedRegions.add(region);
    }

    public boolean isHipaaCompliant() {
        return hipaaCompliant;
    }

    public void setHipaaCompliant(boolean hipaaCompliant) {
        this.hipaaCompliant = hipaaCompliant;
    }

    public boolean isMfaRequired() {
        return mfaRequired;
    }

    public void setMfaRequired(boolean mfaRequired) {
        this.mfaRequired = mfaRequired;
    }
}
