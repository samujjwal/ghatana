/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.oauth2;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import com.ghatana.platform.security.oauth2.OAuth2Provider.OAuth2Exception;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link OAuth2Provider} using WireMock to stub the token endpoint.
 *
 * @doc.type class
 * @doc.purpose Integration tests for OAuth2Provider with WireMock-stubbed endpoints
 * @doc.layer core
 * @doc.pattern Test
 */
@Tag("integration")
@DisplayName("OAuth2Provider — integration tests with WireMock")
class OAuth2ProviderIT {

    private static WireMockServer wireMock;
    private static String baseUrl;

    private OAuth2Config config;
    private OAuth2Provider provider;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        baseUrl = "http://localhost:" + wireMock.port();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();

        // Use manual config (no discoveryUri) so no HTTP call is made in the constructor
        config = OAuth2Config.builder()
                .clientId("test-client")
                .clientSecret("test-secret")
                .tokenEndpoint(URI.create(baseUrl + "/oauth/token"))
                .authorizationEndpoint(URI.create(baseUrl + "/oauth/authorize"))
                .redirectUri(URI.create("http://localhost:8080/callback"))
                .issuerUri(URI.create(baseUrl))
                .jwksUri(URI.create(baseUrl + "/.well-known/jwks.json"))
                .scopes("openid", "profile", "email")
                .build();

        provider = new OAuth2Provider(config);
    }

    @Test
    @DisplayName("generateAuthorizationUrl returns URL with expected parameters")
    void generateAuthorizationUrl_returnsUrlWithClientId() {
        OAuth2Provider.AuthResponse response =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "test-nonce");

        assertThat(response.getAuthorizationUrl())
                .contains("client_id=test-client")
                .contains("response_type=code");
        assertThat(response.getState()).isNotBlank();
        assertThat(response.getNonce()).isNotBlank();
    }

    @Test
    @DisplayName("generateAuthorizationUrl includes nonce in URL")
    void generateAuthorizationUrl_includesNonceParameter() {
        OAuth2Provider.AuthResponse response =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "my-nonce");

        assertThat(response.getAuthorizationUrl()).contains("nonce=");
        assertThat(response.getNonce()).isEqualTo("my-nonce");
    }

    @Test
    @DisplayName("generateAuthorizationUrl generates unique state on each call")
    void generateAuthorizationUrl_uniqueStatePerCall() {
        OAuth2Provider.AuthResponse first =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce-1");
        OAuth2Provider.AuthResponse second =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce-2");

        assertThat(first.getState()).isNotEqualTo(second.getState());
    }

    @Test
    @DisplayName("authenticate throws OAuth2Exception when state is invalid")
    void authenticate_invalidState_throwsOAuth2Exception() {
        // Generate a valid state so the stateStore has an entry
        OAuth2Provider.AuthResponse authResponse =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce");

        assertThatThrownBy(() ->
                provider.authenticate("auth-code", "wrong-state", authResponse.getState(),
                        "http://localhost:8080/callback"))
                .isInstanceOf(OAuth2Exception.class)
                .hasMessageContaining("state");
    }

    @Test
    @DisplayName("authenticate throws OAuth2Exception when token endpoint returns error")
    void authenticate_tokenEndpointError_throwsOAuth2Exception() {
        wireMock.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Bad credentials\"}")));

        OAuth2Provider.AuthResponse authResponse =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce");

        assertThatThrownBy(() ->
                provider.authenticate("bad-code", authResponse.getState(), authResponse.getState(),
                        "http://localhost:8080/callback"))
                .isInstanceOf(OAuth2Exception.class);
    }

    @Test
    @DisplayName("authenticate succeeds when token endpoint returns valid access token")
    void authenticate_validTokenResponse_returnsUser() throws OAuth2Exception {
        String tokenJson = """
                {
                  "access_token": "test-access-token-12345",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "scope": "openid profile email"
                }
                """;

        wireMock.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenJson)));

        // Stub userinfo endpoint (no userinfo configured in this test, but provider may attempt it)
        // Provider will skip userinfo if endpoint is not configured

        OAuth2Provider.AuthResponse authResponse =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce");

        // Authenticate should succeed and return a User (possibly with minimal info)
        var user = provider.authenticate("valid-code", authResponse.getState(),
                authResponse.getState(), "http://localhost:8080/callback");

        assertThat(user).isNotNull();
        assertThat(user.getUserId()).isNotBlank();
    }

    @Test
    @DisplayName("authenticate with null state throws OAuth2Exception")
    void authenticate_nullState_throwsOAuth2Exception() {
        assertThatThrownBy(() ->
                provider.authenticate("some-code", null, null, "http://localhost:8080/callback"))
                .isInstanceOf(OAuth2Exception.class);
    }
}
