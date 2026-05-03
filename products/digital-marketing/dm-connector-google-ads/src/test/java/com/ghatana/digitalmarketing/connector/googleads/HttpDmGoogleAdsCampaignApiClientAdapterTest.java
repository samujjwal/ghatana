package com.ghatana.digitalmarketing.connector.googleads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsCampaignApiClient.CreateGoogleSearchCampaignRequest;
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
import java.math.BigDecimal;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("HttpDmGoogleAdsCampaignApiClientAdapter")
class HttpDmGoogleAdsCampaignApiClientAdapterTest extends EventloopTestBase {

    private MockWebServer server;
    private HttpDmGoogleAdsCampaignApiClientAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new HttpDmGoogleAdsCampaignApiClientAdapter(
            new OkHttpClient(), objectMapper,
            "dev-token", "cust-123",
            server.url("/").toString(),
            Executors.newSingleThreadExecutor()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("createSearchCampaign returns resourceName from API response")
    void shouldReturnResourceNameOnSuccess() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"resourceName\": \"customers/123/campaigns/456\"}"));

        String resourceName = runPromise(() ->
            adapter.createSearchCampaign("acc-token", validRequest()));

        assertThat(resourceName).isEqualTo("customers/123/campaigns/456");
    }

    @Test
    @DisplayName("createSearchCampaign sends Authorization and developer-token headers")
    void shouldSendRequiredHeaders() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"resourceName\": \"customers/123/campaigns/1\"}"));

        runPromise(() -> adapter.createSearchCampaign("bearer-token", validRequest()));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer bearer-token");
        assertThat(request.getHeader("developer-token")).isEqualTo("dev-token");
    }

    @Test
    @DisplayName("createSearchCampaign throws GoogleAdsConnectorException on HTTP 4xx")
    void shouldThrowOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":\"forbidden\"}"));

        assertThatExceptionOfType(GoogleAdsConnectorException.class).isThrownBy(() ->
            runPromise(() -> adapter.createSearchCampaign("bad-token", validRequest())));
    }

    @Test
    @DisplayName("createSearchCampaign sends dailyBudgetMicros converted from USD — 50 USD = 50000000 micros")
    void shouldSendBudgetAsMicros() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"resourceName\": \"customers/123/campaigns/999\"}"));

        runPromise(() -> adapter.createSearchCampaign("token",
            new CreateGoogleSearchCampaignRequest(
                "Budget Test", new BigDecimal("50.00"), "Austin TX", "hvac repair")));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"dailyBudgetMicros\":\"50000000\"");
    }

    @Test
    @DisplayName("toMicrosString — converts fractional USD correctly (1.50 USD = 1500000 micros)")
    void toMicrosString_fractionConvertsCorrectly() {
        assertThat(HttpDmGoogleAdsCampaignApiClientAdapter.toMicrosString(new BigDecimal("1.50")))
            .isEqualTo("1500000");
    }

    @Test
    @DisplayName("toMicrosString — converts whole USD correctly (100 USD = 100000000 micros)")
    void toMicrosString_wholeNumberConvertsCorrectly() {
        assertThat(HttpDmGoogleAdsCampaignApiClientAdapter.toMicrosString(new BigDecimal("100")))
            .isEqualTo("100000000");
    }

    @Test
    @DisplayName("createSearchCampaign sends campaigns:mutate path")
    void shouldSendCampaignsMutatePath() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"resourceName\": \"customers/123/campaigns/1\"}"));

        runPromise(() -> adapter.createSearchCampaign("token", validRequest()));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("campaigns:mutate");
    }

    @Test
    @DisplayName("createSearchCampaign sends name in request payload")
    void shouldSendCampaignNameInPayload() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"resourceName\": \"customers/123/campaigns/1\"}"));

        runPromise(() -> adapter.createSearchCampaign("token", validRequest()));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getBody().readUtf8()).contains("My Campaign");
    }

    @Test
    @DisplayName("createSearchCampaign throws GoogleAdsConnectorException on empty resourceName")
    void shouldThrowOnEmptyResourceName() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"resourceName\": \"\"}"));

        assertThatExceptionOfType(GoogleAdsConnectorException.class).isThrownBy(() ->
            runPromise(() -> adapter.createSearchCampaign("token", validRequest())));
    }

    private static CreateGoogleSearchCampaignRequest validRequest() {
        return new CreateGoogleSearchCampaignRequest(
            "My Campaign",
            new BigDecimal("50.00"),
            "New York",
            "plumbing services"
        );
    }
}
