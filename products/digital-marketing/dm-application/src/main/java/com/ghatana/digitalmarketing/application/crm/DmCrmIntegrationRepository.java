package com.ghatana.digitalmarketing.application.crm;

import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegration;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegrationStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for CRM integration persistence.
 *
 * @doc.type interface
 * @doc.purpose Port for CRM integration storage (DMOS-F4-002)
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface DmCrmIntegrationRepository {

    Promise<DmCrmIntegration> save(DmCrmIntegration integration);

    Promise<DmCrmIntegration> update(DmCrmIntegration integration);

    Promise<Optional<DmCrmIntegration>> findById(String id);

    Promise<List<DmCrmIntegration>> listByTenant(String tenantId);

    Promise<List<DmCrmIntegration>> listByStatus(String tenantId, DmCrmIntegrationStatus status);
}
