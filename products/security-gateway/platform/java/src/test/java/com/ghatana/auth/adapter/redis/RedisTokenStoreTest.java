package com.ghatana.auth.adapter.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.DockerClientFactory;
import redis.clients.jedis.JedisPool;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for RedisTokenStore.
 *
 * Uses Testcontainers to spin up a Redis instance for testing.
 * Tests validate:
 * - Token save and retrieval
 * - Tenant isolation
 * - Token expiration tracking
 * - Token revocation
 * - Token introspection
 * - User and client token queries
 * - Distributed access patterns
 *
 * @see RedisTokenStore
 */
@DisplayName("RedisTokenStore Integration Tests")
@Tag("integration")
class RedisTokenStoreTest extends EventloopTestBase {

    private static GenericContainer<?> redis;
    private static boolean dockerAvailable;

    private RedisTokenStore tokenStore;
    private JedisPool jedisPool;
    private TenantId tenantId;
    private UserId userId;
    private ClientId clientId;

    @BeforeAll
    static void startRedis() {
        boolean available;
        try {
            available = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            available = false;
        }
        dockerAvailable = available;
        Assumptions.assumeTrue(dockerAvailable,
                () -> "Skipping RedisTokenStoreTest because Docker is unavailable");

        redis = new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);

        redis.start();
    }

    @AfterAll
    static void stopRedis() {
        if (!dockerAvailable) {
            return;
        }
        if (redis != null) {
            redis.stop();
        }
    }

    @BeforeEach
    void setUp() {
        // GIVEN: Redis connection pool
        String redisHost = redis.getHost();
        int redisPort = redis.getFirstMappedPort();
        jedisPool = new JedisPool(redisHost, redisPort);
        
        // GIVEN: TokenStore connected to Redis
        tokenStore = new RedisTokenStore(jedisPool, new ObjectMapper());
        
        tenantId = TenantId.of("tenant-redis-test-123");
        userId = UserId.of("user-redis-456");
        clientId = ClientId.of("client-redis-789");
        
        // Cleanup before test
        try (var jedis = jedisPool.getResource()) {
            jedis.flushDB();
        }
    }

    /**
     * Verifies that a token can be saved and retrieved from Redis.
     *
     * GIVEN: Valid token
     * WHEN: save() is called, then findByTokenId() is called
     * THEN: Token is returned with identical properties
     */
    @Test
    @DisplayName("Should save and retrieve token from Redis")
    void shouldSaveAndRetrieveTokenFromRedis() {
        // GIVEN: Valid token
        Token token = createToken(tenantId, "redis-token-1");

        // WHEN: Save and retrieve
        runPromise(() -> tokenStore.store(token));
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, token.getTokenId()));

        // THEN: Token retrieved with same properties
        assertThat(retrieved)
            .as("Token should be found in Redis")
            .isPresent();
        assertThat(retrieved.get().getTokenValue())
            .isEqualTo(token.getTokenValue());
    }

    /**
     * Verifies tenant isolation in Redis.
     *
     * GIVEN: Tokens for different tenants
     * WHEN: Each tenant queries Redis
     * THEN: Each tenant only sees its own tokens
     */
    @Test
    @DisplayName("Should enforce tenant isolation in Redis storage")
    void shouldEnforceTenantIsolationInRedis() {
        // GIVEN: Two tenants
        TenantId tenant1 = TenantId.of("redis-tenant-1");
        TenantId tenant2 = TenantId.of("redis-tenant-2");
        
        Token token1 = createToken(tenant1, "token-1");
        Token token2 = createToken(tenant2, "token-2");

        // WHEN: Save both tokens
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // THEN: Each tenant retrieves only its token
        Optional<Token> tenant1Token = runPromise(() -> tokenStore.findById(tenant1, token1.getTokenId()));
        Optional<Token> tenant2Token = runPromise(() -> tokenStore.findById(tenant2, token2.getTokenId()));
        Optional<Token> notFound = runPromise(() -> tokenStore.findById(tenant1, token2.getTokenId()));

        assertThat(tenant1Token).isPresent();
        assertThat(tenant2Token).isPresent();
        assertThat(notFound).isEmpty();
    }

    /**
     * Verifies introspection by token value in Redis.
     *
     * GIVEN: Token with known value stored in Redis
     * WHEN: introspect() is called
     * THEN: Token is found and returned
     */
    @Test
    @DisplayName("Should find token by introspection in Redis")
    void shouldFindTokenByIntrospectionInRedis() {
        // GIVEN: Token with known value
        String tokenValue = "jwt-value-12345";
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
     * Verifies token expiration in Redis.
     *
     * GIVEN: Expired token stored with TTL in Redis
     * WHEN: Redis TTL expires OR findByTokenId() is called
     * THEN: Token is not returned (either auto-expired by Redis or filtered out)
     */
    @Test
    @DisplayName("Should not retrieve expired tokens from Redis")
    void shouldNotRetrieveExpiredTokensFromRedis() {
        // GIVEN: Expired token
        Token expiredToken = Token.builder()
            .tenantId(tenantId)
            .tokenId(TokenId.random())
            .tokenType(TokenType.ACCESS_TOKEN)
            .userId(userId)
            .clientId(clientId)
            .scopes(Set.of(Scope.of("read")))
            .issuedAt(Instant.now().minus(Duration.ofHours(2)))
            .expiresAt(Instant.now().minus(Duration.ofMinutes(1)))  // Expired
            .tokenValue("expired-token-value")
            .build();

        // WHEN: Save and retrieve
        runPromise(() -> tokenStore.store(expiredToken));
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, expiredToken.getTokenId()));

        // THEN: Expired token not returned
        assertThat(retrieved)
            .as("Expired token should not be retrieved")
            .isEmpty();
    }

    /**
     * Verifies revocation in Redis.
     *
     * GIVEN: Valid token, then revoked
     * WHEN: findByTokenId() is called after revocation
     * THEN: Token not found
     */
    @Test
    @DisplayName("Should revoke tokens in Redis")
    void shouldRevokeTokensInRedis() {
        // GIVEN: Valid token in Redis
        Token token = createToken(tenantId, "token-for-revocation");
        runPromise(() -> tokenStore.store(token));

        // WHEN: Revoke the token
        runPromise(() -> tokenStore.revoke(tenantId, token.getTokenId()));

        // THEN: Token not retrievable after revocation
        Optional<Token> retrieved = runPromise(() -> tokenStore.findById(tenantId, token.getTokenId()));
        assertThat(retrieved)
            .as("Revoked token should not be retrievable")
            .isEmpty();
    }

    /**
     * Verifies finding all tokens for a user in Redis.
     *
     * GIVEN: Multiple tokens for same user in Redis
     * WHEN: findByUserId() is called
     * THEN: All user's active tokens are returned
     */
    @Test
    @DisplayName("Should find all user tokens in Redis")
    void shouldFindAllUserTokensInRedis() {
        // GIVEN: Multiple tokens for user
        Token token1 = createToken(tenantId, "user-token-1");
        Token token2 = createToken(tenantId, "user-token-2");
        Token token3 = createToken(tenantId, "user-token-3");

        // WHEN: Save all tokens
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));
        runPromise(() -> tokenStore.store(token3));

        // THEN: All user tokens retrieved from Redis
        List<Token> userTokens = runPromise(() -> tokenStore.findByUserId(tenantId, userId));
        assertThat(userTokens)
            .as("Should retrieve all user tokens from Redis")
            .hasSize(3)
            .extracting(Token::getTokenId)
            .containsExactlyInAnyOrder(token1.getTokenId(), token2.getTokenId(), token3.getTokenId());
    }

    /**
     * Verifies finding all tokens for a client in Redis.
     *
     * GIVEN: Multiple tokens for same client in Redis
     * WHEN: findByClientId() is called
     * THEN: All client's active tokens are returned
     */
    @Test
    @DisplayName("Should find all client tokens in Redis")
    void shouldFindAllClientTokensInRedis() {
        // GIVEN: Multiple tokens for client
        ClientId client = ClientId.of("redis-client-123");
        Token token1 = createToken(tenantId, userId, client, "client-token-1");
        Token token2 = createToken(tenantId, userId, client, "client-token-2");

        // WHEN: Save tokens
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
     * Verifies revoking all tokens for a user in Redis.
     *
     * GIVEN: Multiple tokens for user
     * WHEN: revokeAllForUser() is called
     * THEN: All user tokens are revoked
     */
    @Test
    @DisplayName("Should revoke all user tokens in Redis")
    void shouldRevokeAllUserTokensInRedis() {
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
            .as("No tokens should remain for user after revocation")
            .isEmpty();
    }

    /**
     * Verifies revoking all tokens for a client in Redis.
     *
     * GIVEN: Multiple tokens for client
     * WHEN: revokeAllForClient() is called
     * THEN: All client tokens are revoked
     */
    @Test
    @DisplayName("Should revoke all client tokens in Redis")
    void shouldRevokeAllClientTokensInRedis() {
        // GIVEN: Multiple tokens for client
        ClientId client = ClientId.of("redis-client-revoke");
        Token token1 = createToken(tenantId, userId, client, "client-revoke-1");
        Token token2 = createToken(tenantId, userId, client, "client-revoke-2");
        runPromise(() -> tokenStore.store(token1));
        runPromise(() -> tokenStore.store(token2));

        // WHEN: Revoke all client tokens
        runPromise(() -> tokenStore.revokeAllForClient(tenantId, client));

        // THEN: No client tokens retrievable
        List<Token> remaining = runPromise(() -> tokenStore.findByClientId(tenantId, client));
        assertThat(remaining)
            .as("No tokens should remain for client after revocation")
            .isEmpty();
    }

    /**
     * Verifies revocation status reporting in Redis.
     *
     * GIVEN: Token in active and revoked states
     * WHEN: isRevoked() is called
     * THEN: Returns false for active, true for revoked
     */
    @Test
    @DisplayName("Should correctly report revocation status in Redis")
    void shouldCorrectlyReportRevocationStatusInRedis() {
        // GIVEN: Valid token
        Token token = createToken(tenantId, "token-for-status-check");
        runPromise(() -> tokenStore.store(token));

        // WHEN/THEN: Initially valid
        Boolean initialStatus = runPromise(() -> tokenStore.isValid(tenantId, token.getTokenId()));
        assertThat(initialStatus).isTrue();

        // WHEN: Revoke
        runPromise(() -> tokenStore.revoke(tenantId, token.getTokenId()));

        // THEN: Now invalid
        Boolean revokedStatus = runPromise(() -> tokenStore.isValid(tenantId, token.getTokenId()));
        assertThat(revokedStatus).isFalse();
    }

    /**
     * Verifies distributed access - multiple instances access same Redis data.
     *
     * GIVEN: Two RedisTokenStore instances connected to same Redis
     * WHEN: Token saved by instance 1, retrieved by instance 2
     * THEN: Token is successfully retrieved (distributed access works)
     */
    @Test
    @DisplayName("Should support distributed access across instances")
    void shouldSupportDistributedAccessAcrossInstances() {
        // GIVEN: Two TokenStore instances connected to same Redis
        RedisTokenStore instance1 = new RedisTokenStore(jedisPool);
        RedisTokenStore instance2 = new RedisTokenStore(jedisPool);
        
        Token token = createToken(tenantId, "distributed-token");

        // WHEN: Token saved by instance 1
        runPromise(() -> instance1.store(token));

        // THEN: Token retrieved by instance 2 (proves distributed access)
        Optional<Token> retrieved = runPromise(() -> instance2.findById(tenantId, token.getTokenId()));
        assertThat(retrieved)
            .as("Instance 2 should retrieve token saved by instance 1")
            .isPresent();
        assertThat(retrieved.get().getTokenValue())
            .isEqualTo(token.getTokenValue());
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
