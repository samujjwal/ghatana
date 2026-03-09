package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for token refresh.
 * 
 * @doc.type record
 * @doc.purpose New JWT tokens after refresh
 * @doc.layer product
 * @doc.pattern DTO
 */
public record RefreshTokenResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("tokenType") String tokenType,
        @JsonProperty("expiresIn") long expiresIn) {

    /**
     * Creates a refresh response with Bearer tokens.
     * 
     * @param accessToken new JWT access token
     * @param refreshToken new JWT refresh token
     * @param expiresIn seconds until access token expires
     * @return refresh response
     */
    public static RefreshTokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new RefreshTokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
