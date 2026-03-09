package com.ghatana.refactorer.server.auth;

import com.ghatana.refactorer.server.config.ServerConfig;

/**
 * Lightweight access policy abstraction. Enforces tenancy requirements and offers helpers for

 * role/budget checks. Concrete budget enforcement happens in the job orchestration layer.

 *

 * @doc.type class

 * @doc.purpose Describe how authentication/authorization is applied across incoming requests.

 * @doc.layer product

 * @doc.pattern Policy

 */

public final class AccessPolicy {
    private final ServerConfig.TenancyConfig tenancyConfig;

    public AccessPolicy(ServerConfig.TenancyConfig tenancyConfig) {
        this.tenancyConfig = tenancyConfig;
    }

    public boolean isAuthRequired() {
        return tenancyConfig.authRequired();
    }

    public void ensureAuthenticated(TenantContext context) {
        if (!isAuthRequired()) {
            return;
        }
        if (context == null) {
            throw new SecurityException("Tenant context is required when auth is enabled");
        }
    }

    public boolean tenantHasRole(TenantContext context, String role) {
        return context != null && context.roles().contains(role);
    }

    public int maxConcurrentJobsPerTenant() {
        return tenancyConfig.maxConcurrentJobsPerTenant();
    }

    public int maxEditsPerFile() {
        return tenancyConfig.maxEditsPerFile();
    }
}
