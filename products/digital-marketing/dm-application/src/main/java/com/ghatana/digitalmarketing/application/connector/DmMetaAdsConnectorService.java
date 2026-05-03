package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmMetaAdsConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application service for managing Meta Ads connectors.
 *
 * @doc.type interface
 * @doc.purpose Use-case boundary for Meta Ads connector management (DMOS-F4-001)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmMetaAdsConnectorService {

    /**
     * Register (create) a new Meta Ads connector in PENDING status.
     */
    Promise<DmMetaAdsConnector> register(DmOperationContext ctx, RegisterMetaAdsConnectorCommand cmd);

    /**
     * Activate a PENDING connector.
     */
    Promise<DmMetaAdsConnector> activate(DmOperationContext ctx, String connectorId);

    /**
     * Mark a connector as failed.
     */
    Promise<DmMetaAdsConnector> markFailed(DmOperationContext ctx, String connectorId, String reason);

    /**
     * Disconnect (deactivate) a connector.
     */
    Promise<DmMetaAdsConnector> disconnect(DmOperationContext ctx, String connectorId);

    Promise<Optional<DmMetaAdsConnector>> findById(DmOperationContext ctx, String connectorId);

    Promise<List<DmMetaAdsConnector>> listByTenant(DmOperationContext ctx);

    /**
     * Value object carrying registration inputs.
     */
    record RegisterMetaAdsConnectorCommand(
            String displayName,
            String appId,
            String accountId,
            String accessToken
    ) {
        public RegisterMetaAdsConnectorCommand {
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (appId == null || appId.isBlank()) throw new IllegalArgumentException("appId must not be blank");
            if (accountId == null || accountId.isBlank()) throw new IllegalArgumentException("accountId must not be blank");
            if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("accessToken must not be blank");
        }
    }
}
