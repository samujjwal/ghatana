/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.services.auth.oauth;

import com.ghatana.platform.security.oauth2.OAuth2Config;
import com.ghatana.platform.security.oauth2.OAuth2Provider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose OAuth2/OIDC flow contract coverage using platform OAuth2Provider
 * @doc.layer shared-services
 * @doc.pattern Test
 */
@DisplayName("OAuth2Provider Contract Tests")
class OAuth2ProviderContractTest {

    @Test
    @DisplayName("authorization URL includes OIDC nonce and generated state")
    void shouldGenerateAuthorizationUrlWithNonceAndState() {
        OAuth2Provider provider = new OAuth2Provider(buildConfig("https://127.0.0.1:9/.well-known/openid-configuration"));

        OAuth2Provider.AuthResponse response = provider.generateAuthorizationUrl(
            "https://app.example.com/callback",
            "nonce-123"
        );

        assertThat(response.getAuthorizationUrl()).contains("response_type=code");
        assertThat(response.getAuthorizationUrl()).contains("client_id=gateway-client");
        assertThat(response.getAuthorizationUrl()).contains("nonce=nonce-123");
        assertThat(response.getState()).isNotBlank();
        assertThat(response.getNonce()).isEqualTo("nonce-123");
    }

    @Test
    @DisplayName("unknown OAuth state is rejected before token exchange")
    void shouldRejectUnknownState() {
        OAuth2Provider provider = new OAuth2Provider(buildConfig("https://127.0.0.1:9/.well-known/openid-configuration"));

        assertThatThrownBy(() -> provider.authenticate(
            "dummy-code",
            "missing-state",
            "missing-state",
            "https://app.example.com/callback"
        ))
            .isInstanceOf(OAuth2Provider.OAuth2Exception.class)
            .hasMessageContaining("Invalid state parameter");
    }

    @Test
    @DisplayName("state is one-time use even when first token exchange fails")
    void shouldInvalidateStateAfterFirstUse() {
        OAuth2Provider provider = new OAuth2Provider(buildConfig("https://127.0.0.1:9/.well-known/openid-configuration"));
        provider.generateAuthorizationUrl("state-once", "nonce-once");

        assertThatThrownBy(() -> provider.authenticate(
            "dummy-code",
            "state-once",
            "state-once",
            "https://app.example.com/callback"
        )).isInstanceOf(OAuth2Provider.OAuth2Exception.class);

        assertThatThrownBy(() -> provider.authenticate(
            "dummy-code",
            "state-once",
            "state-once",
            "https://app.example.com/callback"
        ))
            .isInstanceOf(OAuth2Provider.OAuth2Exception.class)
            .hasMessageContaining("Invalid state parameter");
    }

    private static OAuth2Config buildConfig(String discoveryUri) {
        return OAuth2Config.builder()
            .clientId("gateway-client")
            .clientSecret("gateway-secret")
            .authorizationEndpoint(URI.create("https://idp.example.com/oauth2/authorize"))
            .tokenEndpoint(URI.create("https://idp.example.com/oauth2/token"))
            .userInfoEndpoint(URI.create("https://idp.example.com/oauth2/userinfo"))
            .jwksUri(URI.create("https://idp.example.com/.well-known/jwks.json"))
            .issuerUri(URI.create("https://idp.example.com"))
            .redirectUri(URI.create("https://app.example.com/callback"))
            .discoveryUri(URI.create(discoveryUri))
            .scopes("openid", "profile", "email")
            .build();
    }
}
