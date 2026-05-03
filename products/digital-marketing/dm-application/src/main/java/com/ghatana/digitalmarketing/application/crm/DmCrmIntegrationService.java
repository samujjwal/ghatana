package com.ghatana.digitalmarketing.application.crm;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.crm.DmCrmIntegration;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing external CRM integrations.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for CRM integration management (DMOS-F4-002)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmCrmIntegrationService {

    Promise<DmCrmIntegration> create(DmOperationContext ctx, CreateCrmIntegrationCommand cmd);

    Promise<DmCrmIntegration> activate(DmOperationContext ctx, String integrationId);

    Promise<DmCrmIntegration> recordSync(DmOperationContext ctx, String integrationId);

    Promise<DmCrmIntegration> markFailed(DmOperationContext ctx, String integrationId, String reason);

    Promise<Optional<DmCrmIntegration>> findById(DmOperationContext ctx, String integrationId);

    Promise<List<DmCrmIntegration>> listByTenant(DmOperationContext ctx);

    record CreateCrmIntegrationCommand(
            String crmProvider,
            String displayName,
            String apiEndpoint,
            String credentialRef
    ) {
        public CreateCrmIntegrationCommand {
            if (crmProvider == null || crmProvider.isBlank()) throw new IllegalArgumentException("crmProvider must not be blank");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (apiEndpoint == null || apiEndpoint.isBlank()) throw new IllegalArgumentException("apiEndpoint must not be blank");
            if (credentialRef == null || credentialRef.isBlank()) throw new IllegalArgumentException("credentialRef must not be blank");
        }
    }
}
