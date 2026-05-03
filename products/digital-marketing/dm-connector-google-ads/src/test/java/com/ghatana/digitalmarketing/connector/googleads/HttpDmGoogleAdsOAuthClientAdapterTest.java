package com.ghatana.digitalmarketing.connector.googleads;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.digitalmarketing.application.googleads.DmGoogleAdsOAuthClient.OAuthTokenResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DisplayName("HttpDmGoogleAdsOAuthClientAdapter")
class HttpDmGoogleAdsOAuthClientAdapterTest extends EventloopTestBase {

    private MockWebServer server;
    private HttpDmGoogleAdsOAuthClientAdapter adapter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new HttpDmGoogleAdsOAuthClientAdapter(
            new OkHttpClient(), objectMapper,
            "test-client-id", "test-client-secret",
            server.url("/").toString()
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("buildAuthorizationUrl constructs correct URL with expected params")
    void shouldBuildCorrectAuthorizationUrl() {
        String url = adapter.buildAuthorizationUrl("https://example.com/callback", "state-xyz");

        assertThat(url).contains("client_id=test-client-id");
        assertThat(url).contains("response_type=code");
        assertThat(url).contains("state=state-xyz");
        assertThat(url).contains("access_type=offline");
    }

    @Test
    @DisplayName("exchangeAuthorizationCode parses token response correctly")
    void shouldExchangeCodeForTokens() {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "access_token": "acc-token-1",
                    "refresh_token": "ref-token-1",
                    "expires_in": 3600,
                    "scope": "https://www.googleapis.com/auth/adwords"
                }
                """));

        OAuthTokenResponse response = runPromise(() ->
            adapter.exchangeAuthorizationCode("auth-code-abc", "https://example.com/callback"));

        assertThat(response.accessToken()).isEqualTo("acc-token-1");
        assertThat(response.refreshToken()).isEqualTo("ref-token-1");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(response.scopes()).hasSize(1);
    }

    @Test
    @DisplayName("exchangeAuthorizationCode sends correct form fields")
    void shouldSendCorrectFormFieldsForCodeExchange() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "access_token": "acc",
                    "refresh_token": "ref",
                    "expires_in": 3600,
                    "scope": "ads"
                }
                """));

        runPromise(() ->
            adapter.exchangeAuthorizationCode("my-code", "https://example.com/cb"));

        RecordedRequest request = server.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody).contains("grant_type=authorization_code");
        assertThat(requestBody).contains("code=my-code");
        assertThat(requestBody).contains("client_id=test-client-id");
    }

    @Test
    @DisplayName("exchangeAuthorizationCode throws GoogleAdsConnectorException on HTTP error")
    void shouldThrowOnHttpError() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid_grant\"}"));

        assertThatExceptionOfType(GoogleAdsConnectorException.class).isThrownBy(() ->
            runPromise(() -> adapter.exchangeAuthorizationCode("bad-code", "https://example.com/cb")));
    }

    @Test
    @DisplayName("refreshAccessToken sends correct grant_type")
    void shouldRefreshTokenWithCorrectGrantType() throws InterruptedException {
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("""
                {
                    "access_token": "new-acc",
                    "refresh_token": "same-ref",
                    "expires_in": 3600,
                    "scope": "ads"
                }
                """));

        OAuthTokenResponse response = runPromise(() ->
            adapter.refreshAccessToken("old-refresh-token"));

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("grant_type=refresh_token");
        assertThat(body).contains("refresh_token=old-refresh-token");
        assertThat(response.accessToken()).isEqualTo("new-acc");
    }

    @Test
    @DisplayName("revokeAccessToken sends token as query param and succeeds on 200")
    void shouldRevokeAccessToken() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(200));

        runPromise(() -> adapter.revokeAccessToken("some-access-token"));

        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).contains("token=some-access-token");
    }

    @Test
    @DisplayName("revokeAccessToken throws GoogleAdsConnectorException on HTTP error")
    void shouldThrowOnRevokeError() {
        server.enqueue(new MockResponse().setResponseCode(400));

        assertThatExceptionOfType(GoogleAdsConnectorException.class).isThrownBy(() ->
            runPromise(() -> adapter.revokeAccessToken("bad-token")));
    }
}
