package com.ghatana.digitalmarketing.application.googleads;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("DmGoogleAdsOAuthClient.OAuthTokenResponse")
class DmGoogleAdsOAuthClientTest {

    @Test
    @DisplayName("creates valid token response")
    void validResponse() {
        DmGoogleAdsOAuthClient.OAuthTokenResponse response =
            new DmGoogleAdsOAuthClient.OAuthTokenResponse("access", "refresh", 3600, List.of("scope"));

        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.expiresInSeconds()).isEqualTo(3600);
        assertThat(response.scopes()).containsExactly("scope");
    }

    @Test
    @DisplayName("rejects null and blank access token")
    void rejectsInvalidAccessToken() {
        assertThatNullPointerException().isThrownBy(() ->
            new DmGoogleAdsOAuthClient.OAuthTokenResponse(null, "refresh", 3600, List.of("scope")));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            new DmGoogleAdsOAuthClient.OAuthTokenResponse(" ", "refresh", 3600, List.of("scope")));
    }

    @Test
    @DisplayName("rejects null and blank refresh token")
    void rejectsInvalidRefreshToken() {
        assertThatNullPointerException().isThrownBy(() ->
            new DmGoogleAdsOAuthClient.OAuthTokenResponse("access", null, 3600, List.of("scope")));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            new DmGoogleAdsOAuthClient.OAuthTokenResponse("access", "", 3600, List.of("scope")));
    }

    @Test
    @DisplayName("rejects invalid scope and expiry values")
    void rejectsInvalidScopeAndExpiry() {
        assertThatNullPointerException().isThrownBy(() ->
            new DmGoogleAdsOAuthClient.OAuthTokenResponse("access", "refresh", 3600, null));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() ->
            new DmGoogleAdsOAuthClient.OAuthTokenResponse("access", "refresh", 0, List.of("scope")));
    }
}