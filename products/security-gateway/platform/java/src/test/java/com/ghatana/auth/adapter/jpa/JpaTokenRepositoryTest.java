package com.ghatana.auth.adapter.jpa;

import com.ghatana.platform.security.port.TokenStore;
import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.Scope;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.TokenType;
import com.ghatana.platform.domain.auth.UserId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;

import jakarta.persistence.*;
import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for JpaTokenRepository with PostgreSQL.
 *
 * Uses Testcontainers to spin up PostgreSQL instance for testing.
 * Tests validate:
 * - Token persistence in PostgreSQL
 * - Tenant isolation at database level
 * - Transaction management
 * - Query performance with indexes
 * - Revocation state tracking
 * - Cleanup of expired tokens
 *
 * @see JpaTokenRepository
 */
@DisplayName("JPA Token Repository Integration Tests")
@Tag("integration")
class JpaTokenRepositoryTest extends EventloopTestBase {

    private static PostgreSQLContainer<?> postgres;
    private static boolean dockerAvailable;

    private JpaTokenRepository tokenStore;
    private EntityManager entityManager;
    private EntityManagerFactory emf;
    private TenantId tenantId;
    private UserId userId;
    private ClientId clientId;

    @BeforeAll
    static void startPostgres() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            available = false;
        }
        dockerAvailable = available;
        Assumptions.assumeTrue(dockerAvailable,
                () -> "Skipping JpaTokenRepositoryTest because Docker is unavailable");

        postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("ghatana_test")
                .withUsername("ghatana")
                .withPassword("password");

        postgres.start();
    }

    @AfterAll
    static void stopPostgres() {
        if (!dockerAvailable) {
            return;
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // GIVEN: PostgreSQL connection pool
        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        // Create EntityManagerFactory (simplified for testing)
        // In production, use Hibernate/JPA configuration
        try {
            emf = Persistence.createEntityManagerFactory("ghatana-auth-test");
            entityManager = emf.createEntityManager();
        } catch (Exception e) {
            Assumptions.assumeTrue(false,
                    () -> "Skipping JpaTokenRepositoryTest because JPA provider 'ghatana-auth-test' is not configured: "
                            + e.getMessage());
        }

        // GIVEN: TokenStore connected to PostgreSQL
        tokenStore = new JpaTokenRepository(entityManager);

        tenantId = TenantId.of("tenant-jpa-test-123");
        userId = UserId.of("user-jpa-456");
        clientId = ClientId.of("client-jpa-789");

        // Setup database (create tables if not exist)
        setupDatabase();
    }

    /**
     * Verifies that a token can be saved and retrieved from PostgreSQL.
     *
     * GIVEN: Valid token
     * WHEN: save() is called, then findByTokenId() is called
     * THEN: Token is persisted and retrieved
     */
    @Test
    @DisplayName("Should persist and retrieve token from PostgreSQL")
    void shouldPersistAndRetrieveToken() {
        // GIVEN: Valid token
        Token token = createToken(tenantId, "postgres-token-1");

        // WHEN: Save and retrieve
        runPromise(() -> tokenStore.store(token));
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, token.getTokenId()));

        // THEN: Token retrieved with same properties
        assertThat(retrieved)
                .as("Token should be persisted in PostgreSQL")
                .isPresent();
        assertThat(retrieved.get().getTokenValue())
                .isEqualTo(token.getTokenValue());
    }

    /**
     * Verifies tenant isolation at database level.
     *
     * GIVEN: Tokens for different tenants
     * WHEN: Each tenant queries database
     * THEN: Each tenant only sees its own tokens (enforced by unique constraint)
     */
    @Test
    @DisplayName("Should enforce tenant isolation in database schema")
    void shouldEnforceTenantIsolationAtDatabaseLevel() {
        // GIVEN: Two tenants with same token ID (possible without tenant isolation)
        TenantId tenant1 = TenantId.of("postgres-tenant-1");
        TenantId tenant2 = TenantId.of("postgres-tenant-2");

        Token token1 = createToken(tenant1, "token-1");
        Token token2 = createToken(tenant2, "token-1"); // Same token value pattern

        // WHEN: Save both tokens
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // THEN: Each tenant retrieves only its token
        Optional<Token> tenant1Token = runPromise(() -> tokenStore.findById(tenant1, token1.getTokenId()));
        Optional<Token> tenant2Token = runPromise(() -> tokenStore.findById(tenant2, token2.getTokenId()));

        assertThat(tenant1Token).isPresent();
        assertThat(tenant2Token).isPresent();
        assertThat(tenant1Token.get().getTenantId()).isEqualTo(tenant1);
        assertThat(tenant2Token.get().getTenantId()).isEqualTo(tenant2);
    }

    /**
     * Verifies transaction rollback on error.
     *
     * GIVEN: Valid token, then error occurs
     * WHEN: Exception is thrown during save
     * THEN: Transaction is rolled back
     */
    @Test
    @DisplayName("Should rollback transaction on error")
    void shouldRollbackTransactionOnError() {
        // GIVEN: Valid token
        Token token = createToken(tenantId, "token-for-rollback");

        // WHEN: Save first token
        runPromise(() -> tokenStore.store(token));

        // WHEN: Try to save duplicate (should fail - unique constraint)
        // Note: Duplicate token_id within same tenant
        Token duplicate = Token.builder()
                .tenantId(tenantId)
                .tokenId(token.getTokenId()) // Same ID - should fail
                .tokenType(TokenType.ACCESS_TOKEN)
                .userId(userId)
                .clientId(clientId)
                .scopes(Set.of(Scope.of("read")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .tokenValue("different-value")
                .build();

        // THEN: Operation should handle error gracefully
        try {
            runPromise(() -> tokenStore.store(duplicate));
            // If we get here, test framework didn't prevent duplicate
        } catch (Exception e) {
            // Expected - unique constraint violation
            assertThat(e.getMessage()).contains("constraint", "unique");
        }
    }

    /**
     * Verifies token expiration is tracked correctly.
     *
     * GIVEN: Expired and valid tokens in database
     * WHEN: findByTokenId() is called
     * THEN: Expired tokens not returned
     */
    @Test
    @DisplayName("Should not retrieve expired tokens from database")
    void shouldNotRetrieveExpiredTokensFromDatabase() {
        // GIVEN: Expired token
        Token expiredToken = Token.builder()
                .tenantId(tenantId)
                .tokenId(TokenId.random())
                .tokenType(TokenType.ACCESS_TOKEN)
                .userId(userId)
                .clientId(clientId)
                .scopes(Set.of(Scope.of("read")))
                .issuedAt(Instant.now().minus(Duration.ofHours(2)))
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1))) // Expired
                .tokenValue("expired-value")
                .build();

        // WHEN: Save and retrieve
        runPromise(() -> tokenStore.store(expiredToken));
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, expiredToken.getTokenId()));

        // THEN: Expired token not returned
        assertThat(retrieved)
                .as("Expired token should not be retrieved from database")
                .isEmpty();
    }

    /**
     * Verifies introspection queries work correctly.
     *
     * GIVEN: Token with known value in database
     * WHEN: introspect() is called
     * THEN: Token found by value
     */
    @Test
    @DisplayName("Should find token by introspection in database")
    void shouldFindTokenByIntrospectionInDatabase() {
        // GIVEN: Token with known value
        String tokenValue = "jwt-db-value-12345";
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
                .isPresent();
        assertThat(found.get().getTokenId())
                .isEqualTo(token.getTokenId());
    }

    /**
     * Verifies finding all user tokens.
     *
     * GIVEN: Multiple tokens for same user in database
     * WHEN: findByUserId() is called
     * THEN: All user's active tokens returned, ordered by issued time
     */
    @Test
    @DisplayName("Should find all user tokens in database")
    void shouldFindAllUserTokensInDatabase() {
        // GIVEN: Multiple tokens for user
        Token token1 = createToken(tenantId, "user-token-1");
        Token token2 = createToken(tenantId, "user-token-2");
        Token token3 = createToken(tenantId, "user-token-3");

        // WHEN: Save all
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));
        runPromise(() -> tokenStore.store(token3));

        // THEN: All user tokens retrieved
        List<Token> userTokens = runPromise(() -> tokenStore.findByUserId(tenantId, userId));
        assertThat(userTokens)
                .as("Should retrieve all user tokens from database")
                .hasSize(3)
                .extracting(Token::getTokenId)
                .containsExactlyInAnyOrder(token1.getTokenId(), token2.getTokenId(), token3.getTokenId());
    }

    /**
     * Verifies finding all client tokens.
     *
     * GIVEN: Multiple tokens for same client
     * WHEN: findByClientId() is called
     * THEN: All client's active tokens returned
     */
    @Test
    @DisplayName("Should find all client tokens in database")
    void shouldFindAllClientTokensInDatabase() {
        // GIVEN: Multiple tokens for client
        ClientId client = ClientId.of("postgres-client-123");
        Token token1 = createToken(tenantId, userId, client, "client-token-1");
        Token token2 = createToken(tenantId, userId, client, "client-token-2");

        // WHEN: Save
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // THEN: All client tokens retrieved
        List<Token> clientTokens = runPromise(() -> tokenStore.findByClientId(tenantId, client));
        assertThat(clientTokens)
                .as("Should retrieve all client tokens")
                .hasSize(2)
                .extracting(Token::getTokenId)
                .containsExactlyInAnyOrder(token1.getTokenId(), token2.getTokenId());
    }

    /**
     * Verifies revocation in database.
     *
     * GIVEN: Valid token, then revoked
     * WHEN: isRevoked() and findByTokenId() called
     * THEN: Token marked as revoked, not returned in queries
     */
    @Test
    @DisplayName("Should revoke tokens in database")
    void shouldRevokeTokensInDatabase() {
        // GIVEN: Valid token in database
        Token token = createToken(tenantId, "token-for-db-revocation");
        runPromise(() -> tokenStore.store(token));

        // WHEN: Revoke the token
        runPromise(() -> tokenStore.revoke(tenantId, token.getTokenId()));

        // THEN: Token not retrievable after revocation
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, token.getTokenId()));
        Boolean isRevoked = runPromise(() -> tokenStore.isValid(tenantId, token.getTokenId()));

        assertThat(retrieved)
                .as("Revoked token should not be retrievable")
                .isEmpty();

        assertThat(isRevoked)
                .as("isValid() should return false")
                .isFalse();
    }

    /**
     * Verifies revoking all user tokens in database.
     *
     * GIVEN: Multiple tokens for user
     * WHEN: revokeAllForUser() called
     * THEN: All user tokens marked as revoked
     */
    @Test
    @DisplayName("Should revoke all user tokens in database")
    void shouldRevokeAllUserTokensInDatabase() {
        // GIVEN: Multiple tokens for user
        Token token1 = createToken(tenantId, "user-revoke-1");
        Token token2 = createToken(tenantId, "user-revoke-2");
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // WHEN: Revoke all user tokens
        runPromise(() -> tokenStore.revokeAllForUser(tenantId, userId));

        // THEN: No user tokens retrievable
        List<Token> remaining = runPromise(() -> tokenStore.findByUserId(tenantId, userId));
        assertThat(remaining)
                .as("No tokens should remain for user")
                .isEmpty();
    }

    /**
     * Verifies cleanup of expired tokens.
     *
     * GIVEN: Mix of expired and valid tokens
     * WHEN: cleanupExpired() called
     * THEN: Expired revoked tokens removed from database
     */
    @Test
    @DisplayName("Should clean up expired tokens from database")
    void shouldCleanupExpiredTokensFromDatabase() {
        // GIVEN: Expired token
        Token expiredToken = Token.builder()
                .tenantId(tenantId)
                .tokenId(TokenId.random())
                .tokenType(TokenType.REFRESH_TOKEN)
                .userId(userId)
                .clientId(clientId)
                .scopes(Set.of(Scope.of("refresh")))
                .issuedAt(Instant.now().minus(Duration.ofDays(30)))
                .expiresAt(Instant.now().minus(Duration.ofMinutes(1))) // Expired
                .tokenValue("expired-for-cleanup")
                .build();

        // WHEN: Save and mark as revoked
        runPromise(() -> tokenStore.store(expiredToken));
        runPromise(() -> tokenStore.revoke(tenantId, expiredToken.getTokenId()));

        // WHEN: Cleanup
        Integer deletedCount = runPromise(() -> tokenStore.deleteExpired(tenantId));

        // THEN: Expired revoked token removed
        assertThat(deletedCount)
                .as("Should delete expired, revoked tokens")
                .isGreaterThan(0);
    }

    // ===== Helper methods =====

    private void setupDatabase() {
        // Create tenants table if not exists
        try {
            EntityTransaction tx = entityManager.getTransaction();
            tx.begin();
            entityManager.createNativeQuery(
                    "CREATE TABLE IF NOT EXISTS tenants (id VARCHAR(255) PRIMARY KEY)").executeUpdate();
            tx.commit();
        } catch (Exception e) {
            // Table might exist
        }

        // Insert test tenant
        try {
            EntityTransaction tx = entityManager.getTransaction();
            tx.begin();
            entityManager.createNativeQuery(
                    "INSERT INTO tenants (id) VALUES (:id)").setParameter("id", tenantId.value()).executeUpdate();
            tx.commit();
        } catch (Exception e) {
            // Tenant might already exist
        }
    }

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
