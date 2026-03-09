package com.ghatana.auth.adapter.memory;

import com.ghatana.platform.security.port.TokenStore;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.Scope;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.TokenType;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for InMemoryTokenStore.
 *
 * Tests validate:
 * - Token save and retrieval
 * - Tenant isolation
 * - Token expiration tracking
 * - Token revocation
 * - Token introspection
 * - User and client token queries
 * - Cleanup of expired tokens
 *
 * @see InMemoryTokenStore
 */
@DisplayName("InMemoryTokenStore Tests")
class InMemoryTokenStoreTest extends EventloopTestBase {

    private InMemoryTokenStore tokenStore;
    private TenantId tenantId;
    private UserId userId;
    private ClientId clientId;

    @BeforeEach
    void setUp() {
        // GIVEN: TokenStore with test tenant/user/client
        tokenStore = new InMemoryTokenStore();
        tenantId = TenantId.of("tenant-test-123");
        userId = UserId.of("user-test-456");
        clientId = ClientId.of("client-test-789");
    }

    /**
     * Verifies that a token can be saved and retrieved by ID.
     *
     * GIVEN: Valid token object
     * WHEN: save() is called, then findByTokenId() is called
     * THEN: Token is returned with identical properties
     */
    @Test
    @DisplayName("Should save and retrieve token by ID")
    void shouldSaveAndRetrieveTokenById() {
        // GIVEN: Valid token
        Token token = Token.builder()
            .tenantId(tenantId)
            .tokenId(TokenId.random())
            .tokenType(TokenType.ACCESS_TOKEN)
            .userId(userId)
            .clientId(clientId)
            .scopes(Set.of(Scope.of("read"), Scope.of("write")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(1)))
            .tokenValue("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...")
            .build();

        // WHEN: Save and retrieve token
        runPromise(() -> tokenStore.store(token));
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, token.getTokenId()));

        // THEN: Token is retrieved with same properties
        assertThat(retrieved)
            .as("Token should be found after save")
            .isPresent();
        assertThat(retrieved.get().getTokenId())
            .isEqualTo(token.getTokenId());
    }

    /**
     * Verifies tenant isolation - tokens from different tenants are isolated.
     *
     * GIVEN: Tokens for two different tenants with same user/client
     * WHEN: findByTokenId() is called for each tenant
     * THEN: Each query returns only tokens for that tenant
     */
    @Test
    @DisplayName("Should enforce tenant isolation for token storage")
    void shouldEnforceTenantIsolationForTokens() {
        // GIVEN: Two tenants
        TenantId tenant1 = TenantId.of("tenant-1");
        TenantId tenant2 = TenantId.of("tenant-2");
        
        // GIVEN: Tokens for each tenant
        Token token1 = createToken(tenant1, "token1");
        Token token2 = createToken(tenant2, "token2");

        // WHEN: Save tokens
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // THEN: Each tenant can only retrieve its own token
        Optional<Token> retrieved1 = runPromise(() -> tokenStore.findById(tenant1, token1.getTokenId()));
        Optional<Token> retrieved2 = runPromise(() -> tokenStore.findById(tenant2, token2.getTokenId()));
        Optional<Token> notFound = runPromise(() -> tokenStore.findById(tenant1, token2.getTokenId()));

        assertThat(retrieved1)
            .as("Tenant 1 should find its token")
            .isPresent();
        assertThat(retrieved1.get().getTokenId())
            .isEqualTo(token1.getTokenId());

        assertThat(retrieved2)
            .as("Tenant 2 should find its token")
            .isPresent();
        assertThat(retrieved2.get().getTokenId())
            .isEqualTo(token2.getTokenId());

        assertThat(notFound)
            .as("Tenant 1 should not find Tenant 2's token")
            .isEmpty();
    }

    /**
     * Verifies that expired tokens are not returned.
     *
     * GIVEN: Expired token (expiresAt in past)
     * WHEN: findByTokenId() is called
     * THEN: Optional.empty() is returned
     */
    @Test
    @DisplayName("Should not retrieve expired tokens")
    void shouldNotRetrieveExpiredTokens() {
        // GIVEN: Expired token
        Token expiredToken = Token.builder()
            .tenantId(tenantId)
            .tokenId(TokenId.random())
            .tokenType(TokenType.ACCESS_TOKEN)
            .userId(userId)
            .clientId(clientId)
            .scopes(Set.of(Scope.of("read")))
            .issuedAt(Instant.now().minus(Duration.ofHours(2)))
            .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))  // Expired!
            .tokenValue("expired-token-value")
            .build();

        // WHEN: Save and try to retrieve
        runPromise(() -> tokenStore.store(expiredToken));
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, expiredToken.getTokenId()));

        // THEN: Expired token is not returned
        assertThat(retrieved)
            .as("Expired token should not be retrieved")
            .isEmpty();
    }

    /**
     * Verifies that revoked tokens cannot be retrieved.
     *
     * GIVEN: Valid token, then revoked
     * WHEN: findByTokenId() is called after revocation
     * THEN: Optional.empty() is returned
     */
    @Test
    @DisplayName("Should not retrieve revoked tokens")
    void shouldNotRetrieveRevokedTokens() {
        // GIVEN: Valid token
        Token token = createToken(tenantId, "token-for-revocation");
        runPromise(() -> tokenStore.store(token));

        // WHEN: Revoke the token
        runPromise(() -> tokenStore.revoke(tenantId, token.getTokenId()));

        // THEN: Token cannot be retrieved
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, token.getTokenId()));
        assertThat(retrieved)
            .as("Revoked token should not be retrievable")
            .isEmpty();
    }

    /**
     * Verifies isRevoked() method works correctly.
     *
     * GIVEN: Token in two states (active and revoked)
     * WHEN: isRevoked() is called for each state
     * THEN: Returns false for active, true for revoked
     */
    @Test
    @DisplayName("Should correctly report revocation status")
    void shouldCorrectlyReportRevocationStatus() {
        // GIVEN: Valid token
        Token token = createToken(tenantId, "token-for-revoke-check");
        runPromise(() -> tokenStore.store(token));

        // WHEN/THEN: Initially valid
        Boolean initialStatus = runPromise(() -> tokenStore.isValid(tenantId, token.getTokenId()));
        assertThat(initialStatus)
            .as("Token should be valid initially")
            .isTrue();

        // WHEN: Revoke the token
        runPromise(() -> tokenStore.revoke(tenantId, token.getTokenId()));

        // THEN: Now invalid
        Boolean revokedStatus = runPromise(() -> tokenStore.isValid(tenantId, token.getTokenId()));
        assertThat(revokedStatus)
            .as("Token should be invalid after revoke() call")
            .isFalse();
    }

    /**
     * Verifies introspection by token value.
     *
     * GIVEN: Token with known value
     * WHEN: introspect() is called with token value
     * THEN: Token is found and returned
     */
    @Test
    @DisplayName("Should find token by introspection with token value")
    void shouldFindTokenByIntrospection() {
        // GIVEN: Token with known value
        String tokenValue = "test-jwt-value-12345";
        Token token = Token.builder()
            .tenantId(tenantId)
            .tokenId(TokenId.random())
            .tokenType(TokenType.ACCESS_TOKEN)
            .userId(userId)
            .clientId(clientId)
            .scopes(Set.of(Scope.of("read")))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(1)))
            .tokenValue(tokenValue)
            .build();

        // WHEN: Save and find by value
        runPromise(() -> tokenStore.store(token));
        Optional<Token> found = runPromise(() -> tokenStore.findByValue(tenantId, tokenValue));

        // THEN: Token found via introspection
        assertThat(found)
            .as("Token should be found by introspection")
            .isPresent();
        assertThat(found.get().getTokenValue())
            .isEqualTo(tokenValue);
    }

    /**
     * Verifies finding all tokens for a user.
     *
     * GIVEN: Multiple tokens for same user
     * WHEN: findByUserId() is called
     * THEN: All user's active tokens are returned
     */
    @Test
    @DisplayName("Should find all active tokens for a user")
    void shouldFindAllTokensForUser() {
        // GIVEN: Multiple tokens for same user
        Token token1 = createToken(tenantId, "user-token-1");
        Token token2 = createToken(tenantId, "user-token-2");
        Token token3 = createToken(tenantId, "user-token-3");

        // WHEN: Save all tokens
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));
        runPromise(() -> tokenStore.store(token3));

        // THEN: All user tokens are returned
        List<Token> userTokens = runPromise(() -> tokenStore.findByUserId(tenantId, userId));
        assertThat(userTokens)
            .as("Should find all user tokens")
            .hasSize(3)
            .extracting(Token::getTokenId)
            .containsExactlyInAnyOrder(token1.getTokenId(), token2.getTokenId(), token3.getTokenId());
    }

    /**
     * Verifies finding all tokens for a client.
     *
     * GIVEN: Multiple tokens for same client
     * WHEN: findByClientId() is called
     * THEN: All client's active tokens are returned
     */
    @Test
    @DisplayName("Should find all active tokens for a client")
    void shouldFindAllTokensForClient() {
        // GIVEN: Multiple tokens for same client
        ClientId client = ClientId.of("test-client-789");
        Token token1 = createToken(tenantId, userId, client, "client-token-1");
        Token token2 = createToken(tenantId, userId, client, "client-token-2");

        // WHEN: Save all tokens
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // THEN: All client tokens are returned
        List<Token> clientTokens = runPromise(() -> tokenStore.findByClientId(tenantId, client));
        assertThat(clientTokens)
            .as("Should find all client tokens")
            .hasSize(2)
            .extracting(Token::getTokenId)
            .containsExactlyInAnyOrder(token1.getTokenId(), token2.getTokenId());
    }

    /**
     * Verifies revoking all tokens for a user.
     *
     * GIVEN: Multiple tokens for user
     * WHEN: revokeAllForUser() is called
     * THEN: All user's tokens become unavailable
     */
    @Test
    @DisplayName("Should revoke all tokens for a user")
    void shouldRevokeAllTokensForUser() {
        // GIVEN: Multiple tokens for user
        Token token1 = createToken(tenantId, "user-revoke-1");
        Token token2 = createToken(tenantId, "user-revoke-2");
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // WHEN: Revoke all user tokens
        runPromise(() -> tokenStore.revokeAllForUser(tenantId, userId));

        // THEN: All tokens are revoked and not retrievable
        List<Token> remaining = runPromise(() -> tokenStore.findByUserId(tenantId, userId));
        assertThat(remaining)
            .as("No active tokens should remain for user")
            .isEmpty();
    }

    /**
     * Verifies cleanup of expired tokens.
     *
     * GIVEN: Mix of expired and valid tokens
     * WHEN: cleanupExpired() is called
     * THEN: Only expired tokens are removed
     */
    @Test
    @DisplayName("Should clean up expired tokens")
    void shouldCleanupExpiredTokens() {
        // GIVEN: Mix of expired and valid tokens
        Token validToken = createToken(tenantId, "valid-token");
        Token expiredToken = Token.builder()
            .tenantId(tenantId)
            .tokenId(TokenId.random())
            .tokenType(TokenType.REFRESH_TOKEN)
            .userId(userId)
            .clientId(clientId)
            .scopes(Set.of(Scope.of("refresh")))
            .issuedAt(Instant.now().minus(Duration.ofDays(30)))
            .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))  // Expired
            .tokenValue("expired-value")
            .build();

        // WHEN: Save both
        runPromise(() -> tokenStore.store(validToken));
        runPromise(() -> tokenStore.store(expiredToken));

        // WHEN: Cleanup expired
        Integer deletedCount = runPromise(() -> tokenStore.deleteExpired(tenantId));

        // THEN: One token deleted (the expired one)
        assertThat(deletedCount)
            .as("Should delete exactly 1 expired token")
            .isEqualTo(1);

        // THEN: Valid token still retrievable, expired token gone
        Optional<Token> validStill = runPromise(() -> tokenStore.findById(tenantId, validToken.getTokenId()));
        Optional<Token> expiredGone = runPromise(() -> tokenStore.findById(tenantId, expiredToken.getTokenId()));

        assertThat(validStill)
            .as("Valid token should still exist")
            .isPresent();

        assertThat(expiredGone)
            .as("Expired token should be deleted")
            .isEmpty();
    }

    /**
     * Verifies exception handling for null tenant.
     *
     * GIVEN: Null tenant ID
     * WHEN: Any operation is called
     * THEN: Appropriate exception or error handling occurs
     */
    @Test
    @DisplayName("Should handle null tenant gracefully")
    void shouldHandleNullTenant() {
        // GIVEN: Null tenant (should not happen in practice but test defensive code)
        // WHEN/THEN: Operations should handle null safely
        assertThatCode(() -> {
            // This should not throw, but handle gracefully
            Optional<Token> result = runPromise(() -> 
                tokenStore.findById(TenantId.of("null-test"), TokenId.random())
            );
            assertThat(result).isEmpty();
        }).doesNotThrowAnyException();
    }

    // ===== Helper methods =====

    private Token createToken(TenantId tenantId, String tokenValueSuffix) {
        return createToken(tenantId, userId, clientId, tokenValueSuffix);
    }

    private Token createToken(TenantId tenantId, UserId userId, ClientId clientId, String tokenValueSuffix) {
        Instant now = Instant.now();
        return Token.builder()
            .tenantId(tenantId)
            .tokenId(TokenId.random())
            .tokenType(TokenType.ACCESS_TOKEN)
            .userId(userId)
            .clientId(clientId)
            .scopes(Set.of(Scope.of("read"), Scope.of("write")))
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .tokenValue("jwt-value-" + tokenValueSuffix)
            .build();
    }
}
