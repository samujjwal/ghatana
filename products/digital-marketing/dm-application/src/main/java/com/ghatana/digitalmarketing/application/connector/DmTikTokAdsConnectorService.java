package com.ghatana.digitalmarketing.application.connector;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.connector.DmTikTokAdsConnector;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Service interface for managing TikTok Ads connector lifecycle.
 *
 * @doc.type class
 * @doc.purpose Defines TikTok Ads connector management contract (P3-003)
 * @doc.layer product
 * @doc.pattern Service
 */
public interface DmTikTokAdsConnectorService {

    Promise<DmTikTokAdsConnector> register(DmOperationContext ctx, RegisterTikTokAdsConnectorRequest request);

    Promise<DmTikTokAdsConnector> activate(DmOperationContext ctx, String id);

    Promise<DmTikTokAdsConnector> suspend(DmOperationContext ctx, String id);

    Promise<DmTikTokAdsConnector> reactivate(DmOperationContext ctx, String id);

    Promise<DmTikTokAdsConnector> markAuthFailed(DmOperationContext ctx, String id, String reason);

    Promise<DmTikTokAdsConnector> disable(DmOperationContext ctx, String id);

    Promise<Optional<DmTikTokAdsConnector>> findById(DmOperationContext ctx, String id);

    Promise<List<DmTikTokAdsConnector>> listActive(DmOperationContext ctx, int limit);

    /**
     * Request to register a new TikTok Ads connector configuration.
     *
     * @param displayName human-readable connector name
     * @param advertiserId TikTok advertiser ID
     * @param accessToken OAuth access token
     */
    record RegisterTikTokAdsConnectorRequest(
        String displayName,
        String advertiserId,
        String accessToken
    ) {
        public RegisterTikTokAdsConnectorRequest {
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
            if (advertiserId == null || advertiserId.isBlank()) throw new IllegalArgumentException("advertiserId must not be blank");
        }
    }
}
