package com.ghatana.digitalmarketing.connector.metaads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HttpDmMetaAdsCampaignApiClientAdapter (DMOS-P3-001).
 *
 * @doc.type test
 * @doc.purpose Verify Meta Ads campaign API client adapter behavior
 * @doc.layer connector
 */
@DisplayName("HttpDmMetaAdsCampaignApiClientAdapter")
class HttpDmMetaAdsCampaignApiClientAdapterTest {

    @Test
    @DisplayName("constructor creates adapter with access token")
    void constructor_createsAdapterWithAccessToken() {
        HttpDmMetaAdsCampaignApiClientAdapter adapter = new HttpDmMetaAdsCampaignApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("createCampaign returns promise")
    void createCampaign_returnsPromise() {
        HttpDmMetaAdsCampaignApiClientAdapter adapter = new HttpDmMetaAdsCampaignApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.createCampaign("act_123", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getCampaign returns promise")
    void getCampaign_returnsPromise() {
        HttpDmMetaAdsCampaignApiClientAdapter adapter = new HttpDmMetaAdsCampaignApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.getCampaign("act_123", "campaign_456");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("listCampaigns returns promise")
    void listCampaigns_returnsPromise() {
        HttpDmMetaAdsCampaignApiClientAdapter adapter = new HttpDmMetaAdsCampaignApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.listCampaigns("act_123");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("updateCampaign returns promise")
    void updateCampaign_returnsPromise() {
        HttpDmMetaAdsCampaignApiClientAdapter adapter = new HttpDmMetaAdsCampaignApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.updateCampaign("campaign_456", "{}");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("deleteCampaign returns promise")
    void deleteCampaign_returnsPromise() {
        HttpDmMetaAdsCampaignApiClientAdapter adapter = new HttpDmMetaAdsCampaignApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.deleteCampaign("campaign_456");
        assertThat(promise).isNotNull();
    }
}
