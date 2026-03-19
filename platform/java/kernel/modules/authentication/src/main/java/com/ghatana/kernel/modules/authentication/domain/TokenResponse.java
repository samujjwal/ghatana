/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.authentication.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Generic token response.
 *
 * <p>Contains token response information in a product-agnostic format.
 * Used for token generation and refresh responses.</p>
 *
 * @doc.type class
 * @doc.purpose Generic token response - access token, refresh token, token type, expires in, scopes
 * @doc.layer kernel
 * @doc.pattern ValueObject
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class TokenResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final Set<String> scopes;

    private TokenResponse(Builder builder) {
        this.accessToken = Objects.requireNonNull(builder.accessToken, "accessToken");
        this.refreshToken = Objects.requireNonNull(builder.refreshToken, "refreshToken");
        this.tokenType = Objects.requireNonNullElse(builder.tokenType, "Bearer");
        this.expiresIn = Objects.requireNonNull(builder.expiresIn, "expiresIn");
        this.scopes = Set.copyOf(Objects.requireNonNullElse(builder.scopes, Set.of()));
    }

    /**
     * Gets the access token.
     *
     * @return the access token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Gets the refresh token.
     *
     * @return the refresh token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Gets the token type.
     *
     * @return the token type
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Gets the expiration time in seconds.
     *
     * @return the expiration time in seconds
     */
    public long getExpiresIn() {
        return expiresIn;
    }

    /**
     * Gets the scopes.
     *
     * @return immutable set of scopes
     */
    public Set<String> getScopes() {
        return scopes;
    }

    /**
     * Checks if the token has a specific scope.
     *
     * @param scope the scope to check
     * @return true if the token has the scope
     */
    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }

    /**
     * Creates a new builder for TokenResponse.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for TokenResponse.
     */
    public static final class Builder {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Long expiresIn;
        private Set<String> scopes;

        private Builder() {}

        public Builder accessToken(String accessToken) {
            this.accessToken = accessToken;
            return this;
        }

        public Builder refreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
            return this;
        }

        public Builder tokenType(String tokenType) {
            this.tokenType = tokenType;
            return this;
        }

        public Builder expiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public TokenResponse build() {
            return new TokenResponse(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenResponse that = (TokenResponse) o;
        return Objects.equals(accessToken, that.accessToken) &&
               Objects.equals(refreshToken, that.refreshToken) &&
               Objects.equals(tokenType, that.tokenType) &&
               expiresIn == that.expiresIn &&
               Objects.equals(scopes, that.scopes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessToken, refreshToken, tokenType, expiresIn, scopes);
    }

    @Override
    public String toString() {
        return String.format("TokenResponse{tokenType='%s', expiresIn=%d, scopes=%s}",
            tokenType, expiresIn, scopes);
    }
}
