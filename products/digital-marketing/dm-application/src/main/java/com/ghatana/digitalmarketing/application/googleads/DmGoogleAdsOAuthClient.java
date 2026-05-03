package com.ghatana.digitalmarketing.application.googleads;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Objects;

/**
 * Port for Google Ads OAuth HTTP interactions.
 *
 * @doc.type class
 * @doc.purpose Defines provider-facing OAuth calls for Google Ads connection flow (DMOS-F2-007)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface DmGoogleAdsOAuthClient {

    /**
     * Builds an authorization URL for an interactive OAuth consent flow.
     */
    String buildAuthorizationUrl(String redirectUri, String state);

    /**
     * Exchanges an authorization code for access and refresh tokens.
     */
    Promise<OAuthTokenResponse> exchangeAuthorizationCode(String code, String redirectUri);

    /**
     * Refreshes an access token using a refresh token.
     */
    Promise<OAuthTokenResponse> refreshAccessToken(String refreshToken);

    /**
     * Revokes the active access token at the provider.
     */
    Promise<Void> revokeAccessToken(String accessToken);

    /**
     * OAuth token exchange result.
     */
    record OAuthTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        List<String> scopes
    ) {
        public OAuthTokenResponse {
            Objects.requireNonNull(accessToken, "accessToken must not be null");
            if (accessToken.isBlank()) throw new IllegalArgumentException("accessToken must not be blank");
            Objects.requireNonNull(refreshToken, "refreshToken must not be null");
            if (refreshToken.isBlank()) throw new IllegalArgumentException("refreshToken must not be blank");
            Objects.requireNonNull(scopes, "scopes must not be null");
            if (expiresInSeconds <= 0) throw new IllegalArgumentException("expiresInSeconds must be > 0");
        }
    }
}