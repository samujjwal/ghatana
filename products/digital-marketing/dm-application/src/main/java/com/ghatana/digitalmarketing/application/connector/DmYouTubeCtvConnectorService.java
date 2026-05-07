package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmYouTubeCtvConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing YouTube/CTV connector lifecycle.
 *
 * @doc.type class
 * @doc.purpose Defines YouTube/CTV connector management contract (P3-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmYouTubeCtvConnectorService {

    Promise<DmYouTubeCtvConnector> register(DmOperationContext ctx, RegisterYouTubeCtvConnectorRequest request);

    Promise<DmYouTubeCtvConnector> activate(DmOperationContext ctx, String id);

    Promise<DmYouTubeCtvConnector> suspend(DmOperationContext ctx, String id);

    Promise<DmYouTubeCtvConnector> reactivate(DmOperationContext ctx, String id);

    Promise<DmYouTubeCtvConnector> markAuthFailed(DmOperationContext ctx, String id, String reason);

    Promise<DmYouTubeCtvConnector> disable(DmOperationContext ctx, String id);

    Promise<Optional<DmYouTubeCtvConnector>> findById(DmOperationContext ctx, String id);

    Promise<List<DmYouTubeCtvConnector>> listActive(DmOperationContext ctx, int limit);

    /**
     * Request to register a new YouTube/CTV connector configuration.
     *
     * @param displayName human-readable connector name
     * @param channelId YouTube channel ID
     * @param accessToken OAuth access token
     */
    record RegisterYouTubeCtvConnectorRequest(
        String displayName,
        String channelId,
        String accessToken
    ) {
        public RegisterYouTubeCtvConnectorRequest {
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (channelId == null || channelId.isBlank()) throw new IllegalArgumentException("channelId must not be blank");
        }
    }
}
