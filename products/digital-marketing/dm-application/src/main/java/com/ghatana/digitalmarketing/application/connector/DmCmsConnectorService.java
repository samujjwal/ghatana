package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmCmsConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing CMS connector lifecycle.
 *
 * @doc.type class
 * @doc.purpose Defines CMS connector management contract (P3-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmCmsConnectorService {

    Promise<DmCmsConnector> register(DmOperationContext ctx, RegisterCmsConnectorRequest request);

    Promise<DmCmsConnector> activate(DmOperationContext ctx, String id);

    Promise<DmCmsConnector> suspend(DmOperationContext ctx, String id);

    Promise<DmCmsConnector> reactivate(DmOperationContext ctx, String id);

    Promise<DmCmsConnector> markAuthFailed(DmOperationContext ctx, String id, String reason);

    Promise<DmCmsConnector> disable(DmOperationContext ctx, String id);

    Promise<Optional<DmCmsConnector>> findById(DmOperationContext ctx, String id);

    Promise<List<DmCmsConnector>> listActive(DmOperationContext ctx, int limit);

    /**
     * Request to register a new CMS connector configuration.
     *
     * @param displayName human-readable connector name
     * @param cmsType CMS platform type (e.g., WordPress, Drupal, Contentful)
     * @param apiUrl API base URL for the CMS
     * @param accessToken OAuth access token
     * @param configuration additional CMS-specific configuration
     */
    record RegisterCmsConnectorRequest(
        String displayName,
        String cmsType,
        String apiUrl,
        String accessToken,
        Map<String, String> configuration
    ) {
        public RegisterCmsConnectorRequest {
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (cmsType == null || cmsType.isBlank()) throw new IllegalArgumentException("cmsType must not be blank");
            if (apiUrl == null || apiUrl.isBlank()) throw new IllegalArgumentException("apiUrl must not be blank");
            configuration = configuration != null ? configuration : Map.of();
        }
    }
}
