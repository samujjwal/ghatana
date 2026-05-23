package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.ExternalConnector;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of ConnectorService with in-memory storage.
 *
 * @doc.type class
 * @doc.purpose Provides connector management operations
 * @doc.layer product
 * @doc.pattern Service Implementation
 */
public class ConnectorServiceImpl implements ConnectorService {

    private final ConcurrentMap<String, ExternalConnector> connectors = new ConcurrentHashMap<>();

    @Override
    public Promise<ExternalConnector> registerConnector(DmOperationContext ctx, RegisterConnectorRequest request) {
        String connectorId = ExternalConnector.generateConnectorId();
        ExternalConnector connector = ExternalConnector.builder()
            .connectorId(connectorId)
            .tenantId(ctx.tenantId())
            .name(request.name())
            .connectorType(ExternalConnector.ConnectorType.valueOf(request.connectorType()))
            .config(request.config())
            .status(ExternalConnector.ConnectorStatus.PENDING)
            .createdBy(ctx.userId())
            .build();

        connectors.put(connectorId, connector);
        return Promise.complete(connector);
    }

    @Override
    public Promise<Optional<ExternalConnector>> getConnector(DmOperationContext ctx, String connectorId) {
        return Promise.complete(Optional.ofNullable(connectors.get(connectorId)));
    }

    @Override
    public Promise<ExternalConnector> updateConnector(DmOperationContext ctx, String connectorId, ConnectorConfig config) {
        ExternalConnector connector = connectors.get(connectorId);
        if (connector == null) {
            return Promise.ofException(new IllegalArgumentException("Connector not found: " + connectorId));
        }
        
        ExternalConnector updated = ExternalConnector.builder()
            .connectorId(connector.connectorId())
            .tenantId(connector.tenantId())
            .name(connector.name())
            .connectorType(connector.connectorType())
            .config(config)
            .status(ExternalConnector.ConnectorStatus.PENDING)
            .googleAdsAccountId(connector.googleAdsAccountId())
            .createdAt(connector.createdAt())
            .createdBy(connector.createdBy())
            .build();
        
        connectors.put(connectorId, updated);
        return Promise.complete(updated);
    }

    @Override
    public Promise<ConnectorValidationResult> validateConnector(DmOperationContext ctx, String connectorId) {
        ExternalConnector connector = connectors.get(connectorId);
        if (connector == null) {
            return Promise.ofException(new IllegalArgumentException("Connector not found: " + connectorId));
        }

        boolean isValid = connector.validate();
        ExternalConnector.ConnectorStatus status = isValid ? ExternalConnector.ConnectorStatus.VALID : ExternalConnector.ConnectorStatus.INVALID;
        
        // Update connector status
        ExternalConnector updated = ExternalConnector.builder()
            .connectorId(connector.connectorId())
            .tenantId(connector.tenantId())
            .name(connector.name())
            .connectorType(connector.connectorType())
            .config(connector.config())
            .status(status)
            .googleAdsAccountId(connector.googleAdsAccountId())
            .createdAt(connector.createdAt())
            .lastValidatedAt(Instant.now())
            .createdBy(connector.createdBy())
            .build();
        
        connectors.put(connectorId, updated);

        ConnectorValidationResult result = new ConnectorValidationResult(
            isValid,
            isValid ? List.of() : List.of("Connector validation failed"),
            List.of(),
            Instant.now().toString()
        );

        return Promise.complete(result);
    }

    @Override
    public Promise<ExternalConnector> linkGoogleAdsAccount(DmOperationContext ctx, String connectorId, String googleAdsId) {
        ExternalConnector connector = connectors.get(connectorId);
        if (connector == null) {
            return Promise.ofException(new IllegalArgumentException("Connector not found: " + connectorId));
        }

        connector.linkGoogleAdsAccount(googleAdsId);

        ExternalConnector updated = ExternalConnector.builder()
            .connectorId(connector.connectorId())
            .tenantId(connector.tenantId())
            .name(connector.name())
            .connectorType(connector.connectorType())
            .config(connector.config())
            .status(connector.status())
            .googleAdsAccountId(googleAdsId)
            .createdAt(connector.createdAt())
            .lastValidatedAt(connector.lastValidatedAt())
            .createdBy(connector.createdBy())
            .build();

        connectors.put(connectorId, updated);
        return Promise.complete(updated);
    }

    @Override
    public Promise<GoogleAdsSyncHealth> checkGoogleAdsHealth(DmOperationContext ctx, String connectorId) {
        ExternalConnector connector = connectors.get(connectorId);
        if (connector == null) {
            return Promise.ofException(new IllegalArgumentException("Connector not found: " + connectorId));
        }

        // In a real implementation, this would check actual sync health
        GoogleAdsSyncHealth health = new GoogleAdsSyncHealth(
            true,
            Instant.now().toString(),
            Instant.now().plusMinutes(15).toString(),
            0,
            0,
            "within_limits"
        );

        return Promise.complete(health);
    }

    @Override
    public Promise<List<ExternalConnector>> listConnectors(DmOperationContext ctx, int limit) {
        return Promise.complete(connectors.values().stream().limit(limit).toList());
    }
}
