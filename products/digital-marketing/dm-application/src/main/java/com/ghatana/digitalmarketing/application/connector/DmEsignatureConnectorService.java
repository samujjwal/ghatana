package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmEsignatureConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing e-signature connector lifecycle.
 *
 * @doc.type class
 * @doc.purpose Defines e-signature connector management contract (P3-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmEsignatureConnectorService {

    Promise<DmEsignatureConnector> register(DmOperationContext ctx, RegisterEsignatureConnectorRequest request);

    Promise<DmEsignatureConnector> activate(DmOperationContext ctx, String id);

    Promise<DmEsignatureConnector> suspend(DmOperationContext ctx, String id);

    Promise<DmEsignatureConnector> reactivate(DmOperationContext ctx, String id);

    Promise<DmEsignatureConnector> markAuthFailed(DmOperationContext ctx, String id, String reason);

    Promise<DmEsignatureConnector> disable(DmOperationContext ctx, String id);

    Promise<Optional<DmEsignatureConnector>> findById(DmOperationContext ctx, String id);

    Promise<List<DmEsignatureConnector>> listActive(DmOperationContext ctx, int limit);

    /**
     * Request to register a new e-signature connector configuration.
     *
     * @param displayName human-readable connector name
     * @param esignatureProvider e-signature provider type (e.g., DocuSign, HelloSign, AdobeSign)
     * @param apiUrl API base URL for the e-signature provider
     * @param accessToken OAuth access token
     * @param configuration additional e-signature-specific configuration
     */
    record RegisterEsignatureConnectorRequest(
        String displayName,
        String esignatureProvider,
        String apiUrl,
        String accessToken,
        Map<String, String> configuration
    ) {
        public RegisterEsignatureConnectorRequest {
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (esignatureProvider == null || esignatureProvider.isBlank()) throw new IllegalArgumentException("esignatureProvider must not be blank");
            if (apiUrl == null || apiUrl.isBlank()) throw new IllegalArgumentException("apiUrl must not be blank");
            configuration = configuration != null ? configuration : Map.of();
        }
    }
}
