package com.ghatana.digitalmarketing.application.googleads;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Objects;

/**
 * Provider-facing API client for Google Ads performance reads.
 *
 * @doc.type class
 * @doc.purpose Fetches Google Ads campaign performance metrics for sync workflows (DMOS-F2-009)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DmGoogleAdsPerformanceApiClient {

    Promise<CampaignPerformanceResponse> fetchCampaignPerformance(
        String accessToken,
        FetchCampaignPerformanceRequest request
    );

    /**
     * Provider request to fetch campaign performance in a time window.
     */
    record FetchCampaignPerformanceRequest(
        String externalCampaignId,
        Instant periodStart,
        Instant periodEnd
    ) {
        public FetchCampaignPerformanceRequest {
            Objects.requireNonNull(externalCampaignId, "externalCampaignId must not be null");
            Objects.requireNonNull(periodStart, "periodStart must not be null");
            Objects.requireNonNull(periodEnd, "periodEnd must not be null");
            if (externalCampaignId.isBlank()) {
                throw new IllegalArgumentException("externalCampaignId must not be blank");
            }
            if (periodEnd.isBefore(periodStart)) {
                throw new IllegalArgumentException("periodEnd must be >= periodStart");
            }
        }
    }

    /**
     * Normalized campaign performance metrics.
     */
    record CampaignPerformanceResponse(
        long impressions,
        long clicks,
        long conversions,
        long costMicros,
        double ctr,
        double cpc,
        double conversionRate
    ) {
        public CampaignPerformanceResponse {
            if (impressions < 0 || clicks < 0 || conversions < 0 || costMicros < 0) {
                throw new IllegalArgumentException("metrics must be non-negative");
            }
            if (ctr < 0 || cpc < 0 || conversionRate < 0) {
                throw new IllegalArgumentException("rate metrics must be non-negative");
            }
        }
    }
}
