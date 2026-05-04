package com.ghatana.digitalmarketing.application.googleads;

import io.activej.promise.Promise;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Provider-facing API client for Google Ads campaign creation.
 *
 * @doc.type class
 * @doc.purpose Abstracts Google Ads campaign creation API calls from application flow (DMOS-F2-008)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DmGoogleAdsCampaignApiClient {

    Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request);

    /**
     * Pauses a Google Ads campaign by external resource name.
     *
     * @param accessToken          valid OAuth2 access token for the Google Ads API
     * @param externalCampaignId   Google Ads resource name (e.g. customers/{customerId}/campaigns/{campaignId})
     * @return Promise of the paused campaign resource name
     */
    Promise<String> pauseCampaign(String accessToken, String externalCampaignId);

    /**
     * Payload used to create a Google Search campaign.
     */
    record CreateGoogleSearchCampaignRequest(
        String campaignName,
        BigDecimal dailyBudget,
        String serviceArea,
        String keywordTheme
    ) {
        public CreateGoogleSearchCampaignRequest {
            Objects.requireNonNull(campaignName, "campaignName must not be null");
            Objects.requireNonNull(dailyBudget, "dailyBudget must not be null");
            Objects.requireNonNull(serviceArea, "serviceArea must not be null");
            Objects.requireNonNull(keywordTheme, "keywordTheme must not be null");
            if (campaignName.isBlank()) throw new IllegalArgumentException("campaignName must not be blank");
            if (dailyBudget.signum() <= 0) throw new IllegalArgumentException("dailyBudget must be > 0");
            if (serviceArea.isBlank()) throw new IllegalArgumentException("serviceArea must not be blank");
            if (keywordTheme.isBlank()) throw new IllegalArgumentException("keywordTheme must not be blank");
        }
    }
}
