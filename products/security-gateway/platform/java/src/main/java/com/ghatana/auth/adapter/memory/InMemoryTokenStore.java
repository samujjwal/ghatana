package com.ghatana.auth.adapter.memory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghatana.platform.security.port.TokenStore;
import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.UserId;

import io.activej.promise.Promise;

/**
 * In-memory implementation of TokenStore for testing and development.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides non-persistent token storage for unit testing and development
 * environments. All tokens stored in memory with tenant isolation. Supports
 * token expiration tracking and revocation management.
 *
 * <p>
 * <b>Storage Structure</b><br>
 * - Map<String, Map<String, Token>>: tenant → tokenId → token - Map<String,
 * Set<String>>: tenant:user → set of token IDs - Map<String, Set<String>>:
 * revoked token IDs (tenant:tokenId)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * TokenStore tokenStore = new InMemoryTokenStore();
 *
 * // Save token
 * Token token = Token.builder()
 *     .tenantId(tenantId)
 *     .tokenId(TokenId.random())
 *     .tokenType(TokenType.ACCESS_TOKEN)
 *     .userId(userId)
 *     .ttl(Duration.ofMinutes(15))
 *     .tokenValue(jwtString)
 *     .build();
 *
 * tokenStore.save(token).getResult();  // Get result for testing
 *
 * // Retrieve token
 * Optional<Token> found = tokenStore.findByTokenId(tenantId, token.getId()).getResult();
 *
 * // Revoke token
 * tokenStore.revoke(tenantId, token.getId()).getResult();
 *
 * // Check if revoked
 * boolean isRevoked = tokenStore.isRevoked(tenantId, token.getId()).getResult();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe via ConcurrentHashMap. All operations atomic.
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * O(1) operations for save/retrieve/revoke. Memory usage: O(n) where n = number
 * of active tokens.
 *
 * <p>
 * <b>Limitations</b><br>
 * - Non-persistent (data lost on restart) - Not suitable for production (no
 * distributed support) - No automatic cleanup of expired tokens (manual via
 * cleanupExpired) - TTL expiration not enforced automatically (checked on
 * retrieval)
 *
 * @see TokenStore
 * @doc.type class
 * @doc.purpose In-memory token store adapter for testing
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class InMemoryTokenStore implements TokenStore {

    private static final Logger logger = LoggerFactory.getLogger(InMemoryTokenStore.class);

    // Structure: tenantId -> tokenId -> Token
    private final Map<String, Map<String, Token>> tokensByTenant = new ConcurrentHashMap<>();

    // Structure: "tenantId:userId" -> set of token IDs
    private final Map<String, Set<String>> tokensByUser = new ConcurrentHashMap<>();

    // Structure: "tenantId:clientId" -> set of token IDs
    private final Map<String, Set<String>> tokensByClient = new ConcurrentHashMap<>();

    // Structure: "tenantId:tokenId" -> revocation timestamp
    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    @Override
    public Promise<Void> store(Token token) {
        saveToken(token);
        return Promise.of(null);
    }

    @Override
    public Promise<Optional<Token>> findById(TenantId tenantId, TokenId tokenId) {
        return Promise.of(findToken(tenantId, tokenId));
    }

    @Override
    public Promise<Optional<Token>> findByValue(TenantId tenantId, String tokenValue) {
        return Promise.of(
                getTokensForTenant(tenantId.value())
                        .values()
                        .stream()
                        .filter(t -> t.getTokenValue().equals(tokenValue) && !isTokenRevoked(tenantId, t.getTokenId()))
                        .filter(this::isTokenValid)
                        .findFirst()
        );
    }

    public Promise<List<Token>> findByUserId(TenantId tenantId, UserId userId) {
        return Promise.of(
                getTokensForUser(tenantId, userId)
                        .stream()
                        .filter(this::isTokenValid)
                        .filter(t -> !isTokenRevoked(tenantId, t.getTokenId()))
                        .collect(Collectors.toList())
        );
    }

    public Promise<List<Token>> findByClientId(TenantId tenantId, ClientId clientId) {
        return Promise.of(
                getTokensForClient(tenantId, clientId)
                        .stream()
                        .filter(this::isTokenValid)
                        .filter(t -> !isTokenRevoked(tenantId, t.getTokenId()))
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Promise<Void> revoke(TenantId tenantId, TokenId tokenId) {
        return Promise.of(revokeToken(tenantId, tokenId));
    }

    public Promise<Void> revokeAllForUser(TenantId tenantId, UserId userId) {
        return Promise.of(revokeAllUserTokens(tenantId, userId));
    }

    public Promise<Void> revokeAllForClient(TenantId tenantId, ClientId clientId) {
        return Promise.of(revokeAllClientTokens(tenantId, clientId));
    }

    @Override
    public Promise<Boolean> isValid(TenantId tenantId, TokenId tokenId) {
        return Promise.of(!isTokenRevoked(tenantId, tokenId));
    }

    @Override
    public Promise<Integer> deleteExpired(TenantId tenantId) {
        return Promise.of(deleteExpiredTokens(tenantId));
    }

    // ===== Helper methods =====
    private Token saveToken(Token token) {
        String tenantId = token.getTenantId().value();
        String tokenId = token.getTokenId().value();

        // Save in main store
        getTokensForTenant(tenantId).put(tokenId, token);

        // Index by user
        String userKey = tenantId + ":" + token.getUserId().value();
        tokensByUser.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet()).add(tokenId);

        // Index by client
        String clientKey = tenantId + ":" + token.getClientId().value();
        tokensByClient.computeIfAbsent(clientKey, k -> ConcurrentHashMap.newKeySet()).add(tokenId);

        logger.debug("Token saved: tenant={}, tokenId={}, type={}", tenantId, tokenId, token.getTokenType());
        return token;
    }

    private Optional<Token> findToken(TenantId tenantId, TokenId tokenId) {
        Optional<Token> token = Optional.ofNullable(
                getTokensForTenant(tenantId.value()).get(tokenId.value())
        );

        // Check if token is valid (not expired) and not revoked
        return token
                .filter(this::isTokenValid)
                .filter(t -> !isTokenRevoked(tenantId, tokenId));
    }

    private List<Token> getTokensForUser(TenantId tenantId, UserId userId) {
        String userKey = tenantId.value() + ":" + userId.value();
        Set<String> tokenIds = tokensByUser.getOrDefault(userKey, Set.of());

        return tokenIds.stream()
                .map(id -> getTokensForTenant(tenantId.value()).get(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Token> getTokensForClient(TenantId tenantId, ClientId clientId) {
        String clientKey = tenantId.value() + ":" + clientId.value();
        Set<String> tokenIds = tokensByClient.getOrDefault(clientKey, Set.of());

        return tokenIds.stream()
                .map(id -> getTokensForTenant(tenantId.value()).get(id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Void revokeToken(TenantId tenantId, TokenId tokenId) {
        String revokeKey = tenantId.value() + ":" + tokenId.value();
        revokedTokens.put(revokeKey, Instant.now());

        logger.debug("Token revoked: tenant={}, tokenId={}", tenantId.value(), tokenId.value());
        return null;
    }

    private Void revokeAllUserTokens(TenantId tenantId, UserId userId) {
        getTokensForUser(tenantId, userId)
                .forEach(token -> revokeToken(tenantId, token.getTokenId()));

        logger.debug("All tokens revoked for user: tenant={}, userId={}", tenantId.value(), userId.value());
        return null;
    }

    private Void revokeAllClientTokens(TenantId tenantId, ClientId clientId) {
        getTokensForClient(tenantId, clientId)
                .forEach(token -> revokeToken(tenantId, token.getTokenId()));

        logger.debug("All tokens revoked for client: tenant={}, clientId={}", tenantId.value(), clientId.value());
        return null;
    }

    private boolean isTokenRevoked(TenantId tenantId, TokenId tokenId) {
        String revokeKey = tenantId.value() + ":" + tokenId.value();
        return revokedTokens.containsKey(revokeKey);
    }

    private boolean isTokenValid(Token token) {
        return token.getExpiresAt().isAfter(Instant.now());
    }

    private int deleteExpiredTokens(TenantId tenantId) {
        String tenantKey = tenantId.value();
        Map<String, Token> tokens = getTokensForTenant(tenantKey);

        int initialCount = tokens.size();
        tokens.entrySet().removeIf(entry -> !isTokenValid(entry.getValue()));
        int deletedCount = initialCount - tokens.size();

        if (deletedCount > 0) {
            logger.debug("Cleaned up {} expired tokens for tenant: {}", deletedCount, tenantKey);
        }
        return deletedCount;
    }

    private Map<String, Token> getTokensForTenant(String tenantId) {
        return tokensByTenant.computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
    }

    /**
     * Clears all tokens (useful for test cleanup).
     */
    public void clear() {
        tokensByTenant.clear();
        tokensByUser.clear();
        tokensByClient.clear();
        revokedTokens.clear();
        logger.debug("All tokens cleared");
    }
}
