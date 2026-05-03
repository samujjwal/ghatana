package com.ghatana.digitalmarketing.application.security;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.security.DmEnterpriseSecurityConfig;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing enterprise security configurations.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for enterprise security config management (DMOS-F4-005)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmEnterpriseSecurityConfigService {

    Promise<DmEnterpriseSecurityConfig> provision(DmOperationContext ctx, ProvisionSecurityConfigCommand cmd);

    Promise<DmEnterpriseSecurityConfig> update(DmOperationContext ctx, String configId, UpdateSecurityConfigCommand cmd);

    Promise<Optional<DmEnterpriseSecurityConfig>> findByTenant(DmOperationContext ctx);

    record ProvisionSecurityConfigCommand(
            boolean mfaRequired,
            boolean ipAllowlistEnabled,
            List<String> allowedIpCidrs,
            boolean auditLogEnabled,
            int sessionTimeoutMinutes,
            String ssoProvider,
            String ssoMetadataUrl
    ) {
        public ProvisionSecurityConfigCommand {
            if (sessionTimeoutMinutes <= 0) throw new IllegalArgumentException("sessionTimeoutMinutes must be positive");
            if (allowedIpCidrs == null) throw new IllegalArgumentException("allowedIpCidrs must not be null");
        }
    }

    record UpdateSecurityConfigCommand(
            boolean mfaRequired,
            boolean ipAllowlistEnabled,
            List<String> allowedIpCidrs,
            boolean auditLogEnabled,
            int sessionTimeoutMinutes,
            String ssoProvider,
            String ssoMetadataUrl
    ) {
        public UpdateSecurityConfigCommand {
            if (sessionTimeoutMinutes <= 0) throw new IllegalArgumentException("sessionTimeoutMinutes must be positive");
            if (allowedIpCidrs == null) throw new IllegalArgumentException("allowedIpCidrs must not be null");
        }
    }
}
