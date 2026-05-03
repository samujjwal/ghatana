package com.ghatana.digitalmarketing.application.security;

import com.ghatana.digitalmarketing.domain.security.DmEnterpriseSecurityConfig;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository port for enterprise security config persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for enterprise security config storage (DMOS-F4-005)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmEnterpriseSecurityConfigRepository {

    Promise<DmEnterpriseSecurityConfig> save(DmEnterpriseSecurityConfig config);

    Promise<DmEnterpriseSecurityConfig> update(DmEnterpriseSecurityConfig config);

    Promise<Optional<DmEnterpriseSecurityConfig>> findById(String id);

    Promise<Optional<DmEnterpriseSecurityConfig>> findByTenantId(String tenantId);
}
