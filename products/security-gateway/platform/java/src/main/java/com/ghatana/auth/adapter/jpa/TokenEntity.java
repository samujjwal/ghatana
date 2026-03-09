package com.ghatana.auth.adapter.jpa;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.Scope;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.TokenType;
import com.ghatana.platform.domain.auth.UserId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * JPA entity mapping for the tokens table, converting between
 * the {@link Token} domain object and the database representation.
 *
 * @doc.type class
 * @doc.purpose JPA entity for token persistence
 * @doc.layer product
 * @doc.pattern Entity, Adapter
 */
@Entity
@Table(name = "tokens")
public class TokenEntity {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "token_id", nullable = false)
    private String tokenId;

    @Column(name = "token_value", nullable = false, columnDefinition = "TEXT")
    private String tokenValue;

    @Column(name = "token_type", nullable = false)
    private String tokenType;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "scopes", columnDefinition = "TEXT")
    private String scopes;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Default constructor required by JPA.
     */
    protected TokenEntity() {
        // JPA requires no-arg constructor
    }

    /**
     * Creates a TokenEntity from a Token domain object.
     *
     * @param token the domain token
     * @return the JPA entity
     */
    public static TokenEntity fromDomain(Token token) {
        Objects.requireNonNull(token, "token cannot be null");

        TokenEntity entity = new TokenEntity();
        entity.id = token.getTokenId().value();
        entity.tenantId = token.getTenantId().value();
        entity.tokenId = token.getTokenId().value();
        entity.tokenValue = token.getTokenValue();
        entity.tokenType = token.getTokenType().name();
        entity.userId = token.getUserId().value();
        entity.clientId = token.getClientId().value();
        entity.scopes = serializeScopes(token.getScopes());
        entity.issuedAt = token.getIssuedAt();
        entity.expiresAt = token.getExpiresAt();
        entity.revokedAt = token.getRevokedAt().orElse(null);
        entity.createdAt = Instant.now();
        entity.updatedAt = Instant.now();

        return entity;
    }

    /**
     * Converts this entity to a Token domain object.
     *
     * @return the domain token
     */
    public Token toDomain() {
        Token.Builder builder = Token.builder()
                .tenantId(TenantId.of(tenantId))
                .tokenId(TokenId.of(tokenId))
                .tokenType(TokenType.valueOf(tokenType))
                .userId(UserId.of(userId))
                .clientId(ClientId.of(clientId))
                .tokenValue(tokenValue)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .scopes(deserializeScopes(scopes));

        if (revokedAt != null) {
            builder.revoked(true).revokedAt(revokedAt);
        }

        return builder.build();
    }

    /**
     * Checks whether this token has expired based on the current time.
     *
     * @return true if the token expiration time is in the past
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Marks this token as revoked with the current timestamp.
     */
    public void revoke() {
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Returns the revocation timestamp, or null if not revoked.
     *
     * @return the revocation instant, or null
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    // --- Accessors for JPQL ---

    public String getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getTokenId() { return tokenId; }
    public String getTokenValue() { return tokenValue; }
    public String getTokenType() { return tokenType; }
    public String getUserId() { return userId; }
    public String getClientId() { return clientId; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getExpiresAt() { return expiresAt; }

    // --- Scope serialization helpers ---

    private static String serializeScopes(Set<Scope> scopeSet) {
        if (scopeSet == null || scopeSet.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Scope scope : scopeSet) {
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(scope.value()).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private static Set<Scope> deserializeScopes(String json) {
        if (json == null || json.equals("[]") || json.isBlank()) {
            return Set.of();
        }
        // Simple JSON array parsing for scope strings
        Set<Scope> result = new HashSet<>();
        String stripped = json.replaceAll("[\\[\\]\"]", "");
        if (!stripped.isBlank()) {
            for (String s : stripped.split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    result.add(Scope.of(trimmed));
                }
            }
        }
        return result;
    }
}
