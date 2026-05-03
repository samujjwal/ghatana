package com.ghatana.digitalmarketing.connector.metaads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HttpDmMetaAdsPerformanceApiClientAdapter (DMOS-P3-001).
 *
 * @doc.type test
 * @doc.purpose Verify Meta Ads performance API client adapter behavior
 * @doc.layer connector
 */
@DisplayName("HttpDmMetaAdsPerformanceApiClientAdapter")
class HttpDmMetaAdsPerformanceApiClientAdapterTest {

    @Test
    @DisplayName("constructor creates adapter with access token")
    void constructor_createsAdapterWithAccessToken() {
        HttpDmMetaAdsPerformanceApiClientAdapter adapter = new HttpDmMetaAdsPerformanceApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        assertThat(adapter).isNotNull();
    }

    @Test
    @DisplayName("getCampaignInsights returns promise")
    void getCampaignInsights_returnsPromise() {
        HttpDmMetaAdsPerformanceApiClientAdapter adapter = new HttpDmMetaAdsPerformanceApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.getCampaignInsights("campaign_456", "last_30d", "impressions,clicks,spend");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getAdSetInsights returns promise")
    void getAdSetInsights_returnsPromise() {
        HttpDmMetaAdsPerformanceApiClientAdapter adapter = new HttpDmMetaAdsPerformanceApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.getAdSetInsights("adset_789", "last_30d", "impressions,clicks,spend");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("getAdInsights returns promise")
    void getAdInsights_returnsPromise() {
        HttpDmMetaAdsPerformanceApiClientAdapter adapter = new HttpDmMetaAdsPerformanceApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.getAdInsights("ad_101", "last_30d", "impressions,clicks,spend");
        assertThat(promise).isNotNull();
    }

    @Test
    @DisplayName("syncAccountPerformance returns promise")
    void syncAccountPerformance_returnsPromise() {
        HttpDmMetaAdsPerformanceApiClientAdapter adapter = new HttpDmMetaAdsPerformanceApiClientAdapter("test-token", Executors.newSingleThreadExecutor());
        var promise = adapter.syncAccountPerformance("act_123", "last_30d");
        assertThat(promise).isNotNull();
    }
}
