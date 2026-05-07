package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmSeoToolConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing SEO tool connector lifecycle.
 *
 * @doc.type class
 * @doc.purpose Defines SEO tool connector management contract (P3-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmSeoToolConnectorService {

    Promise<DmSeoToolConnector> register(DmOperationContext ctx, RegisterSeoToolConnectorRequest request);

    Promise<DmSeoToolConnector> activate(DmOperationContext ctx, String id);

    Promise<DmSeoToolConnector> suspend(DmOperationContext ctx, String id);

    Promise<DmSeoToolConnector> reactivate(DmOperationContext ctx, String id);

    Promise<DmSeoToolConnector> markAuthFailed(DmOperationContext ctx, String id, String reason);

    Promise<DmSeoToolConnector> disable(DmOperationContext ctx, String id);

    Promise<Optional<DmSeoToolConnector>> findById(DmOperationContext ctx, String id);

    Promise<List<DmSeoToolConnector>> listActive(DmOperationContext ctx, int limit);

    /**
     * Request to register a new SEO tool connector configuration.
     *
     * @param displayName human-readable connector name
     * @param seoToolType SEO tool platform type (e.g., SEMrush, Ahrefs, Moz)
     * @param apiUrl API base URL for the SEO tool
     * @param accessToken OAuth access token
     * @param configuration additional SEO tool-specific configuration
     */
    record RegisterSeoToolConnectorRequest(
        String displayName,
        String seoToolType,
        String apiUrl,
        String accessToken,
        Map<String, String> configuration
    ) {
        public RegisterSeoToolConnectorRequest {
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (seoToolType == null || seoToolType.isBlank()) throw new IllegalArgumentException("seoToolType must not be blank");
            if (apiUrl == null || apiUrl.isBlank()) throw new IllegalArgumentException("apiUrl must not be blank");
            configuration = configuration != null ? configuration : Map.of();
        }
    }
}
