package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.ExternalConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for connector configuration and Google Ads readiness.
 *
 * @doc.type class
 * @doc.purpose Defines operations for managing external connectors (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ConnectorService {

    /**
     * Register a new external connector.
     *
     * @param ctx     operation context
     * @param request connector registration request
     * @return the newly registered connector
     */
    Promise<ExternalConnector> registerConnector(DmOperationContext ctx, RegisterConnectorRequest request);

    /**
     * Fetch a connector by ID.
     *
     * @param ctx          operation context
     * @param connectorId connector ID
     * @return optional connector
     */
    Promise<Optional<ExternalConnector>> getConnector(DmOperationContext ctx, String connectorId);

    /**
     * Update connector configuration.
     *
     * @param ctx          operation context
     * @param connectorId connector ID
     * @param config       new configuration
     * @return updated connector
     */
    Promise<ExternalConnector> updateConnector(DmOperationContext ctx, String connectorId, ConnectorConfig config);

    /**
     * Validate connector configuration.
     *
     * @param ctx          operation context
     * @param connectorId connector ID
     * @return validation result
     */
    Promise<ConnectorValidationResult> validateConnector(DmOperationContext ctx, String connectorId);

    /**
     * Link Google Ads account.
     *
     * @param ctx          operation context
     * @param connectorId connector ID
     * @param googleAdsId  Google Ads account ID
     * @return updated connector
     */
    Promise<ExternalConnector> linkGoogleAdsAccount(DmOperationContext ctx, String connectorId, String googleAdsId);

    /**
     * Check Google Ads sync health.
     *
     * @param ctx          operation context
     * @param connectorId connector ID
     * @return sync health status
     */
    Promise<GoogleAdsSyncHealth> checkGoogleAdsHealth(DmOperationContext ctx, String connectorId);

    /**
     * List connectors for the tenant.
     *
     * @param ctx   operation context
     * @param limit max results
     * @return list of connectors
     */
    Promise<List<ExternalConnector>> listConnectors(DmOperationContext ctx, int limit);

    // ── Request types ─────────────────────────────────────────────────────────

    record RegisterConnectorRequest(
        String name,
        String connectorType,
        ConnectorConfig config
    ) {
        public RegisterConnectorRequest {
            // Validation logic
        }
    }

    record ConnectorConfig(
        String endpoint,
        String apiKey,
        String apiSecret,
        String region,
        java.util.Map<String, String> additionalConfig
    ) {}

    record ConnectorValidationResult(
        boolean isValid,
        List<String> errors,
        List<String> warnings,
        String validatedAt
    ) {}

    record GoogleAdsSyncHealth(
        boolean isHealthy,
        String lastSyncAt,
        String nextSyncAt,
        int pendingSyncs,
        int failedSyncs,
        String rateLimitStatus
    ) {}
}
