/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    static void startWireMock() { // GH-90000
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort()); // GH-90000
        wireMock.start(); // GH-90000
        baseUrl = "http://localhost:" + wireMock.port(); // GH-90000
    }

    @AfterAll
    static void stopWireMock() { // GH-90000
        if (wireMock != null) { // GH-90000
            wireMock.stop(); // GH-90000
        }
    }

    @BeforeEach
    void setUp() { // GH-90000
        wireMock.resetAll(); // GH-90000

        // Use manual config (no discoveryUri) so no HTTP call is made in the constructor // GH-90000
        config = OAuth2Config.builder() // GH-90000
                .clientId("test-client")
                .clientSecret("test-secret")
                .tokenEndpoint(URI.create(baseUrl + "/oauth/token")) // GH-90000
                .authorizationEndpoint(URI.create(baseUrl + "/oauth/authorize")) // GH-90000
                .redirectUri(URI.create("http://localhost:8080/callback"))
                .issuerUri(URI.create(baseUrl)) // GH-90000
                .jwksUri(URI.create(baseUrl + "/.well-known/jwks.json")) // GH-90000
                .scopes("openid", "profile", "email") // GH-90000
                .build(); // GH-90000

        provider = new OAuth2Provider(config); // GH-90000
    }

    @Test
    @DisplayName("generateAuthorizationUrl returns URL with expected parameters")
    void generateAuthorizationUrl_returnsUrlWithClientId() { // GH-90000
        OAuth2Provider.AuthResponse response =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "test-nonce"); // GH-90000

        assertThat(response.getAuthorizationUrl()) // GH-90000
                .contains("client_id=test-client")
                .contains("response_type=code");
        assertThat(response.getState()).isNotBlank(); // GH-90000
        assertThat(response.getNonce()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("generateAuthorizationUrl includes nonce in URL")
    void generateAuthorizationUrl_includesNonceParameter() { // GH-90000
        OAuth2Provider.AuthResponse response =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "my-nonce"); // GH-90000

        assertThat(response.getAuthorizationUrl()).contains("nonce=");
        assertThat(response.getNonce()).isEqualTo("my-nonce");
    }

    @Test
    @DisplayName("generateAuthorizationUrl generates unique state on each call")
    void generateAuthorizationUrl_uniqueStatePerCall() { // GH-90000
        OAuth2Provider.AuthResponse first =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce-1"); // GH-90000
        OAuth2Provider.AuthResponse second =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce-2"); // GH-90000

        assertThat(first.getState()).isNotEqualTo(second.getState()); // GH-90000
    }

    @Test
    @DisplayName("authenticate throws OAuth2Exception when state is invalid")
    void authenticate_invalidState_throwsOAuth2Exception() { // GH-90000
        // Generate a valid state so the stateStore has an entry
        OAuth2Provider.AuthResponse authResponse =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce"); // GH-90000

        assertThatThrownBy(() -> // GH-90000
                provider.authenticate("auth-code", "wrong-state", authResponse.getState(), // GH-90000
                        "http://localhost:8080/callback"))
                .isInstanceOf(OAuth2Exception.class) // GH-90000
                .hasMessageContaining("state");
    }

    @Test
    @DisplayName("authenticate throws OAuth2Exception when token endpoint returns error")
    void authenticate_tokenEndpointError_throwsOAuth2Exception() { // GH-90000
        wireMock.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse() // GH-90000
                        .withStatus(400) // GH-90000
                        .withHeader("Content-Type", "application/json") // GH-90000
                        .withBody("{\"error\":\"invalid_grant\",\"error_description\":\"Bad credentials\"}"))); // GH-90000

        OAuth2Provider.AuthResponse authResponse =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce"); // GH-90000

        assertThatThrownBy(() -> // GH-90000
                provider.authenticate("bad-code", authResponse.getState(), authResponse.getState(), // GH-90000
                        "http://localhost:8080/callback"))
                .isInstanceOf(OAuth2Exception.class); // GH-90000
    }

    @Test
    @DisplayName("authenticate succeeds when token endpoint returns valid access token")
    void authenticate_validTokenResponse_returnsUser() throws OAuth2Exception { // GH-90000
        String tokenJson = """
                {
                  "access_token": "test-access-token-12345",
                  "token_type": "Bearer",
                  "expires_in": 3600,
                  "scope": "openid profile email"
                }
                """;

        wireMock.stubFor(post(urlEqualTo("/oauth/token"))
                .willReturn(aResponse() // GH-90000
                        .withStatus(200) // GH-90000
                        .withHeader("Content-Type", "application/json") // GH-90000
                        .withBody(tokenJson))); // GH-90000

        // Stub userinfo endpoint (no userinfo configured in this test, but provider may attempt it) // GH-90000
        // Provider will skip userinfo if endpoint is not configured

        OAuth2Provider.AuthResponse authResponse =
                provider.generateAuthorizationUrl("http://localhost:8080/callback", "nonce"); // GH-90000

        // Authenticate should succeed and return a User (possibly with minimal info) // GH-90000
        var user = provider.authenticate("valid-code", authResponse.getState(), // GH-90000
                authResponse.getState(), "http://localhost:8080/callback"); // GH-90000

        assertThat(user).isNotNull(); // GH-90000
        assertThat(user.getUserId()).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("authenticate with null state throws OAuth2Exception")
    void authenticate_nullState_throwsOAuth2Exception() { // GH-90000
        assertThatThrownBy(() -> // GH-90000
                provider.authenticate("some-code", null, null, "http://localhost:8080/callback")) // GH-90000
                .isInstanceOf(OAuth2Exception.class); // GH-90000
    }
}
