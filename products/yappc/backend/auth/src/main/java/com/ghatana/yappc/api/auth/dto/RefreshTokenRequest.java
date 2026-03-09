package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request payload for token refresh.
 * 
 * @doc.type record
 * @doc.purpose Refresh token to obtain new access token
 * @doc.layer product
 * @doc.pattern DTO
 */
public record RefreshTokenRequest(
        @JsonProperty("refreshToken") String refreshToken) {

    /**
     * Validates refresh token request.
     * 
     * @return true if refresh token is non-empty
     */
    public boolean isValid() {
        return refreshToken != null && !refreshToken.isBlank();
    }
}
