package com.ghatana.yappc.api.auth;

import com.ghatana.yappc.api.auth.dto.UserProfile;

/**
 * Internal result object for authentication operations.
 * 
 * @doc.type class
 * @doc.purpose Wraps authentication outcome with tokens or error message
 * @doc.layer product
 * @doc.pattern Result
 */
public class AuthenticationResult {
    private final boolean success;
    private final String message;
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final UserProfile userProfile;

    private AuthenticationResult(boolean success, String message, String accessToken, 
                                 String refreshToken, String tokenType, long expiresIn, UserProfile userProfile) {
        this.success = success;
        this.message = message;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.userProfile = userProfile;
    }

    /**
     * Creates a successful authentication result.
     * 
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token
     * @param expiresIn seconds until expiration
     * @param userProfile authenticated user profile
     * @return success result
     */
    public static AuthenticationResult success(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            UserProfile userProfile) {
        return new AuthenticationResult(true, null, accessToken, refreshToken, tokenType, expiresIn, userProfile);
    }

    /**
     * Creates a successful authentication result (token-only).
     *
     * @param accessToken JWT access token
     * @param refreshToken JWT refresh token
     * @param tokenType token type (e.g. Bearer)
     * @param expiresIn seconds until expiration
     * @return success result
     */
    public static AuthenticationResult successTokenOnly(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn) {
        return new AuthenticationResult(true, null, accessToken, refreshToken, tokenType, expiresIn, null);
    }

    /**
     * Creates a failed authentication result.
     * 
     * @param message error message
     * @return failure result
     */
    public static AuthenticationResult failure(String message) {
        return new AuthenticationResult(false, message, null, null, null, 0, null);
    }

    /** Convenience alias used by legacy callers. */
    public boolean success() {
        return success;
    }

    public boolean isSuccess() {
        return success;
    }

    /** Convenience alias used by legacy callers. */
    public String error() {
        return message;
    }

    public String getMessage() {
        return message;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public UserProfile getUserProfile() {
        return userProfile;
    }

    /** Record-style getters used by some controllers. */
    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public String tokenType() {
        return tokenType;
    }

    public long expiresIn() {
        return expiresIn;
    }

    public UserProfile user() {
        return userProfile;
    }
}
