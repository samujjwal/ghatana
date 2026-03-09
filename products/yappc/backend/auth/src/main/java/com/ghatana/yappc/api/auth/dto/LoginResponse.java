package com.ghatana.yappc.api.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for successful authentication.
 * 
 * @doc.type record
 * @doc.purpose JWT tokens and user profile after login
 * @doc.layer product
 * @doc.pattern DTO
 */
public record LoginResponse(
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("tokenType") String tokenType,
        @JsonProperty("expiresIn") long expiresIn,
        @JsonProperty("user") UserProfile user) {

    /**
     * Creates a login response with Bearer tokens.
     * 
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token
     * @param expiresIn seconds until access token expires
     * @param user authenticated user profile
     * @return login response
     */
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, UserProfile user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
