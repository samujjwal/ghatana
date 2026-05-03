package com.ghatana.digitalmarketing.application.tenant;

import com.ghatana.digitalmarketing.domain.tenant.DmSelfMarketingTenantProfile;
import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Repository port for tenant profile persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for self-marketing tenant profile storage (DMOS-F2-020)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmTenantProfileRepository {

    Promise<DmSelfMarketingTenantProfile> save(DmSelfMarketingTenantProfile profile);

    Promise<DmSelfMarketingTenantProfile> update(DmSelfMarketingTenantProfile profile);

    Promise<Optional<DmSelfMarketingTenantProfile>> findByTenantId(String tenantId);
}
