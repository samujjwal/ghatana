package com.ghatana.digitalmarketing.connector.googleads;

import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient;
import io.activej.promise.Promise;

/**
 * In-memory no-op implementation of DmGoogleAdsCampaignApiClient for dev/test.
 *
 * @doc.type class
 * @doc.purpose Provides no-op implementation for development and testing when Google Ads credentials are not configured
 * @doc.layer product
 * @doc.pattern InMemory
 */
public final class InMemoryDmGoogleAdsCampaignApiClient implements DmGoogleAdsCampaignApiClient {

    @Override
    public Promise<String> createSearchCampaign(String accessToken, CreateGoogleSearchCampaignRequest request) {
        return Promise.of("PENDING:" + request.campaignName());
    }

    @Override
    public Promise<String> pauseCampaign(String accessToken, String externalCampaignId) {
        return Promise.of("PAUSED:" + externalCampaignId);
    }
}
