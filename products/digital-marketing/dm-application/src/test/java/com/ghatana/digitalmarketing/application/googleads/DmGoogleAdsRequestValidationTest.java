package com.ghatana.digitalmarketing.application.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("DmGoogleAds Request Validation Tests")
class DmGoogleAdsRequestValidationTest {

    @Test
    @DisplayName("ExchangeCodeRequest rejects blank connector id")
    void exchangeCodeRequestRejectsBlankConnectorId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsAuthService.ExchangeCodeRequest(" ", "code", "https://cb"));
    }

    @Test
    @DisplayName("ExchangeCodeRequest rejects blank code")
    void exchangeCodeRequestRejectsBlankCode() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsAuthService.ExchangeCodeRequest("conn", " ", "https://cb"));
    }

    @Test
    @DisplayName("ExchangeCodeRequest rejects null redirect URI")
    void exchangeCodeRequestRejectsNullRedirectUri() {
        assertThatExceptionOfType(NullPointerException.class)
            .isThrownBy(() -> new DmGoogleAdsAuthService.ExchangeCodeRequest("conn", "code", null));
    }

    @Test
    @DisplayName("CreateGoogleSearchCampaignRequest rejects non-positive budget")
    void createGoogleSearchCampaignRequestRejectsNonPositiveBudget() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest(
                "campaign",
                BigDecimal.ZERO,
                "Mumbai",
                "dentist"
            ));
    }

    @Test
    @DisplayName("CreateGoogleSearchCampaignRequest rejects blank keyword theme")
    void createGoogleSearchCampaignRequestRejectsBlankKeywordTheme() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest(
                "campaign",
                new BigDecimal("10"),
                "Mumbai",
                " "
            ));
    }

    @Test
    @DisplayName("CreateSearchCampaignRequest rejects blank service area")
    void createSearchCampaignRequestRejectsBlankServiceArea() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                "conn",
                "campaign",
                new BigDecimal("5"),
                " ",
                "theme"
            ));
    }

    @Test
    @DisplayName("CreateSearchCampaignRequest rejects blank connector id")
    void createSearchCampaignRequestRejectsBlankConnectorId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                " ",
                "campaign",
                new BigDecimal("5"),
                "Mumbai",
                "theme"
            ));
    }

    @Test
    @DisplayName("CreateSearchCampaignRequest rejects blank keyword theme")
    void createSearchCampaignRequestRejectsBlankKeywordTheme() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsCampaignConnectorService.CreateSearchCampaignRequest(
                "conn",
                "campaign",
                new BigDecimal("5"),
                "Mumbai",
                " "
            ));
    }

    @Test
    @DisplayName("FetchCampaignPerformanceRequest rejects blank external id")
    void fetchCampaignPerformanceRequestRejectsBlankExternalId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceApiClient.FetchCampaignPerformanceRequest(
                " ",
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T00:00:00Z")
            ));
    }

    @Test
    @DisplayName("FetchCampaignPerformanceRequest rejects inverted period")
    void fetchCampaignPerformanceRequestRejectsInvertedPeriod() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceApiClient.FetchCampaignPerformanceRequest(
                "ext-1",
                Instant.parse("2026-01-31T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z")
            ));
    }

    @Test
    @DisplayName("CampaignPerformanceResponse rejects negative counters")
    void campaignPerformanceResponseRejectsNegativeCounters() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceApiClient.CampaignPerformanceResponse(
                -1,
                0,
                0,
                0,
                0.0,
                0.0,
                0.0
            ));
    }

    @Test
    @DisplayName("CampaignPerformanceResponse rejects negative rates")
    void campaignPerformanceResponseRejectsNegativeRates() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceApiClient.CampaignPerformanceResponse(
                1,
                0,
                0,
                0,
                -0.1,
                0.0,
                0.0
            ));
    }

    @Test
    @DisplayName("CampaignPerformanceResponse rejects negative CPC")
    void campaignPerformanceResponseRejectsNegativeCpc() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceApiClient.CampaignPerformanceResponse(
                1,
                1,
                0,
                1000,
                0.1,
                -0.5,
                0.0
            ));
    }

    @Test
    @DisplayName("CampaignPerformanceResponse rejects negative conversion rate")
    void campaignPerformanceResponseRejectsNegativeConversionRate() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceApiClient.CampaignPerformanceResponse(
                1,
                1,
                0,
                1000,
                0.1,
                0.5,
                -0.1
            ));
    }

    @Test
    @DisplayName("SyncCampaignPerformanceRequest rejects blank campaign id")
    void syncCampaignPerformanceRequestRejectsBlankCampaignId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                "conn",
                " ",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-28T23:59:59Z")
            ));
    }

    @Test
    @DisplayName("SyncCampaignPerformanceRequest rejects blank connector id")
    void syncCampaignPerformanceRequestRejectsBlankConnectorId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                " ",
                "camp-1",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-28T23:59:59Z")
            ));
    }

    @Test
    @DisplayName("SyncCampaignPerformanceRequest allows valid payload")
    void syncCampaignPerformanceRequestAllowsValidPayload() {
        DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest request =
            new DmGoogleAdsPerformanceSyncService.SyncCampaignPerformanceRequest(
                "conn",
                "camp-1",
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-02-28T23:59:59Z")
            );

        assertThat(request.connectorId()).isEqualTo("conn");
        assertThat(request.internalCampaignId()).isEqualTo("camp-1");
    }
}
