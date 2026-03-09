/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.domain.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Represents an OAuth/OIDC token with claims, lifecycle, and revocation status.
 *
 * @doc.type class
 * @doc.purpose Immutable OAuth/OIDC token with claims, lifecycle, and revocation status
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@JsonDeserialize(builder = Token.Builder.class)
public final class Token {

    private final TenantId tenantId;
    private final TokenId tokenId;
    private final TokenType tokenType;
    private final UserId userId;
    private final ClientId clientId;
    private final Set<Scope> scopes;
    private final String tokenValue;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant notBefore;
    private final boolean revoked;
    private final Instant revokedAt;
    private final Map<String, Object> claims;

    private Token(Builder builder) {
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.tokenId = Objects.requireNonNull(builder.tokenId, "tokenId");
        this.tokenType = Objects.requireNonNull(builder.tokenType, "tokenType");
        this.userId = Objects.requireNonNull(builder.userId, "userId");
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId");
        this.scopes = Set.copyOf(builder.scopes);
        this.tokenValue = Objects.requireNonNull(builder.tokenValue, "tokenValue");
        this.issuedAt = builder.issuedAt != null ? builder.issuedAt : Instant.now();
        this.expiresAt = Objects.requireNonNull(builder.expiresAt, "expiresAt");
        this.notBefore = builder.notBefore;
        this.revoked = builder.revoked;
        this.revokedAt = builder.revokedAt;
        this.claims = Map.copyOf(builder.claims);
    }

    public TenantId getTenantId() { return tenantId; }
    public TokenId getTokenId() { return tokenId; }
    public TokenType getTokenType() { return tokenType; }
    public UserId getUserId() { return userId; }
    public ClientId getClientId() { return clientId; }
    public Set<Scope> getScopes() { return scopes; }
    public String getTokenValue() { return tokenValue; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Optional<Instant> getNotBefore() { return Optional.ofNullable(notBefore); }
    public boolean isRevoked() { return revoked; }
    public Optional<Instant> getRevokedAt() { return Optional.ofNullable(revokedAt); }
    public Map<String, Object> getClaims() { return claims; }

    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isNotYetValid() { return notBefore != null && Instant.now().isBefore(notBefore); }
    public boolean isValid() { return !revoked && !isExpired() && !isNotYetValid(); }
    public boolean hasScope(Scope scope) { return scopes.contains(scope); }

    public Duration getRemainingTime() {
        Instant now = Instant.now();
        return now.isAfter(expiresAt) ? Duration.ZERO : Duration.between(now, expiresAt);
    }

    public Token withRevoked() {
        return Token.builder()
                .tenantId(tenantId).tokenId(tokenId).tokenType(tokenType)
                .userId(userId).clientId(clientId).scopes(scopes).tokenValue(tokenValue)
                .issuedAt(issuedAt).expiresAt(expiresAt).notBefore(notBefore)
                .revoked(true).revokedAt(Instant.now()).claims(claims)
                .build();
    }

    public static Builder builder() { return new Builder(); }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private TenantId tenantId;
        private TokenId tokenId;
        private TokenType tokenType;
        private UserId userId;
        private ClientId clientId;
        private Set<Scope> scopes = new HashSet<>();
        private String tokenValue;
        private Instant issuedAt;
        private Instant expiresAt;
        private Instant notBefore;
        private boolean revoked = false;
        private Instant revokedAt;
        private Map<String, Object> claims = new HashMap<>();

        public Builder tenantId(TenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder tokenId(TokenId tokenId) { this.tokenId = tokenId; return this; }
        public Builder tokenType(TokenType tokenType) { this.tokenType = tokenType; return this; }
        public Builder userId(UserId userId) { this.userId = userId; return this; }
        public Builder clientId(ClientId clientId) { this.clientId = clientId; return this; }
        public Builder scopes(Set<Scope> scopes) { this.scopes = new HashSet<>(scopes); return this; }
        public Builder addScope(Scope scope) { this.scopes.add(scope); return this; }
        public Builder tokenValue(String tokenValue) { this.tokenValue = tokenValue; return this; }
        public Builder issuedAt(Instant issuedAt) { this.issuedAt = issuedAt; return this; }
        public Builder expiresAt(Instant expiresAt) { this.expiresAt = expiresAt; return this; }
        public Builder ttl(Duration ttl) {
            Instant now = this.issuedAt != null ? this.issuedAt : Instant.now();
            this.expiresAt = now.plus(ttl);
            return this;
        }
        public Builder notBefore(Instant notBefore) { this.notBefore = notBefore; return this; }
        public Builder revoked(boolean revoked) { this.revoked = revoked; return this; }
        public Builder revokedAt(Instant revokedAt) { this.revokedAt = revokedAt; return this; }
        public Builder claims(Map<String, Object> claims) { this.claims = new HashMap<>(claims); return this; }
        public Builder addClaim(String key, Object value) { this.claims.put(key, value); return this; }
        public Token build() { return new Token(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(tenantId, token.tenantId) && Objects.equals(tokenId, token.tokenId);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, tokenId); }

    @Override
    public String toString() {
        return "Token{tenantId=" + tenantId + ", tokenId=" + tokenId + ", tokenType=" + tokenType +
                ", expiresAt=" + expiresAt + ", revoked=" + revoked + '}';
    }
}
