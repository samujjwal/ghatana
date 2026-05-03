package com.ghatana.digitalmarketing.connector.googleads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsPerformanceApiClient.CampaignPerformanceResponse;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsPerformanceApiClient.FetchCampaignPerformanceRequest;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("HttpDmGoogleAdsPerformanceApiClientAdapter")
class HttpDmGoogleAdsPerformanceApiClientAdapterTest extends EventloopTestBase {

    private MockWebServer server;
    private HttpDmGoogleAdsPerformanceApiClientAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new HttpDmGoogleAdsPerformanceApiClientAdapter(
            new OkHttpClient(), objectMapper,
            "dev-token", "cust-456",
            server.url("/").toString(),
            Executors.newSingleThreadExecutor()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("fetchCampaignPerformance returns correctly mapped response")
    void shouldReturnMappedPerformanceResponse() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "impressions": 10000,
                    "clicks": 500,
                    "conversions": 25,
                    "costMicros": 1500000,
                    "ctr": 0.05,
                    "cpc": 3.00,
                    "conversionRate": 0.05
                }
                """));

        CampaignPerformanceResponse response = runPromise(() ->
            adapter.fetchCampaignPerformance("acc-token", validRequest()));

        assertThat(response.impressions()).isEqualTo(10000);
        assertThat(response.clicks()).isEqualTo(500);
        assertThat(response.conversions()).isEqualTo(25);
        assertThat(response.costMicros()).isEqualTo(1500000);
        assertThat(response.ctr()).isEqualTo(0.05);
        assertThat(response.cpc()).isEqualTo(3.00);
        assertThat(response.conversionRate()).isEqualTo(0.05);
    }

    @Test
    @DisplayName("fetchCampaignPerformance sends Authorization and developer-token headers")
    void shouldSendRequiredHeaders() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"impressions":0,"clicks":0,"conversions":0,"costMicros":0,"ctr":0,"cpc":0,"conversionRate":0}
                """));

        runPromise(() -> adapter.fetchCampaignPerformance("my-token", validRequest()));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer my-token");
        assertThat(request.getHeader("developer-token")).isEqualTo("dev-token");
    }

    @Test
    @DisplayName("fetchCampaignPerformance includes campaignId in request path")
    void shouldIncludeCampaignIdInPath() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {"impressions":0,"clicks":0,"conversions":0,"costMicros":0,"ctr":0,"cpc":0,"conversionRate":0}
                """));

        runPromise(() -> adapter.fetchCampaignPerformance("token", validRequest()));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("campaign-ext-id-999");
    }

    @Test
    @DisplayName("fetchCampaignPerformance throws GoogleAdsConnectorException on HTTP 5xx")
    void shouldThrowOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("{\"error\":\"unavailable\"}"));

        assertThatExceptionOfType(GoogleAdsConnectorException.class).isThrownBy(() ->
            runPromise(() -> adapter.fetchCampaignPerformance("token", validRequest())));
    }

    private static FetchCampaignPerformanceRequest validRequest() {
        return new FetchCampaignPerformanceRequest(
            "campaign-ext-id-999",
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-31T23:59:59Z")
        );
    }

}
