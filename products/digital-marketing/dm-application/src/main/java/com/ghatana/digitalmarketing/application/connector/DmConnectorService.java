package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorConfig;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorStatus;
import com.ghatana.digitalmarketing.domain.connector.DmConnectorType;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing connector runtime configuration lifecycle.
 *
 * @doc.type class
 * @doc.purpose Defines the connector lifecycle management contract (DMOS-F2-006)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmConnectorService {

    Promise<DmConnectorConfig> register(DmOperationContext ctx, RegisterConnectorRequest request);

    Promise<DmConnectorConfig> activate(DmOperationContext ctx, String id);

    Promise<DmConnectorConfig> suspend(DmOperationContext ctx, String id);

    Promise<DmConnectorConfig> reactivate(DmOperationContext ctx, String id);

    Promise<DmConnectorConfig> markAuthFailed(DmOperationContext ctx, String id, String reason);

    Promise<DmConnectorConfig> disable(DmOperationContext ctx, String id);

    Promise<Optional<DmConnectorConfig>> findById(DmOperationContext ctx, String id);

    Promise<List<DmConnectorConfig>> findByType(DmOperationContext ctx, DmConnectorType type, int limit);

    Promise<List<DmConnectorConfig>> listActive(DmOperationContext ctx, int limit);

    Promise<Long> countByStatus(DmOperationContext ctx, DmConnectorStatus status);

    /**
     * Request to register a new connector configuration.
     *
     * @param name              human-readable connector name
     * @param connectorType     type of external system
     * @param settings          configuration key-value pairs (credentials, endpoints)
     * @param externalAccountId external platform account identifier
     */
    record RegisterConnectorRequest(
        String name,
        DmConnectorType connectorType,
        Map<String, String> settings,
        String externalAccountId
    ) {
        public RegisterConnectorRequest {
            Objects.requireNonNull(connectorType, "connectorType must not be null");
            Objects.requireNonNull(settings, "settings must not be null");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        }
    }
}
