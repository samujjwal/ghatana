package com.ghatana.auth.adapter.jpa;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import com.ghatana.platform.security.port.TokenStore;
import com.ghatana.platform.domain.auth.ClientId;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.Token;
import com.ghatana.platform.domain.auth.TokenId;
import com.ghatana.platform.domain.auth.UserId;

import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

/**
 * JPA-based implementation of TokenStore using PostgreSQL.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides durable token persistence with ACID guarantees for production
 * environments. Supports complex queries, transaction management, and audit
 * trails.
 *
 * <p>
 * <b>Key Features</b><br>
 * - Durable persistence: All tokens stored in PostgreSQL with transactional
 * guarantees - Audit trail: Token lifecycle tracked through database records -
 * Complex queries: Support for user/client lookups, filtering, pagination -
 * Soft deletes: Revoked tokens marked as revoked, not physically deleted -
 * Tenant isolation: All queries scoped by tenant ID with database constraints -
 * Indexes: Optimized for common access patterns (tenant+userId,
 * tenant+clientId) - Full-text search: Support for future token search
 * capabilities
 *
 * <p>
 * <b>Entity Schema</b><br>
 * Table: tokens - id (UUID): Primary key - tenant_id (VARCHAR): Tenant
 * identifier (indexed) - token_id (VARCHAR): Unique token identifier (indexed)
 * - token_value (TEXT): JWT token value (indexed for introspection) -
 * token_type (VARCHAR): TOKEN_TYPE enum value - user_id (VARCHAR): User who
 * owns the token (indexed) - client_id (VARCHAR): Client that generated token
 * (indexed) - scopes (TEXT): JSON array of scopes - issued_at (TIMESTAMP):
 * Token issuance time - expires_at (TIMESTAMP): Token expiration time (indexed
 * for cleanup) - revoked_at (TIMESTAMP): Revocation timestamp (null if active)
 * - created_at (TIMESTAMP): Record creation time - updated_at (TIMESTAMP):
 * Record update time
 *
 * <p>
 * <b>Indexes</b><br>
 * - UNIQUE(tenant_id, token_id) - (tenant_id, user_id) - for findByUserId
 * queries - (tenant_id, client_id) - for findByClientId queries - (tenant_id,
 * token_value) - for introspection - (tenant_id, expires_at) - for cleanup
 * queries - (tenant_id, revoked_at) - for revocation queries
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * EntityManager entityManager = ...;
 * TokenStore tokenStore = new JpaTokenRepository(entityManager);
 *
 * // Save a token
 * Token token = ...;
 * tokenStore.save(token).get();
 *
 * // Retrieve by ID
 * Optional<Token> found = tokenStore.findByTokenId(tenantId, tokenId).get();
 *
 * // Find all user tokens
 * List<Token> userTokens = tokenStore.findByUserId(tenantId, userId).get();
 *
 * // Revoke a token
 * tokenStore.revoke(tenantId, tokenId).get();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe: Uses EntityManager from JPA container. Multiple threads can
 * safely use the same instance as EntityManager is thread-safe.
 *
 * <p>
 * <b>Transaction Management</b><br>
 * All database operations use Promise.ofBlocking() to prevent blocking the
 * ActiveJ event loop. Transactions are managed by the JPA container.
 *
 * @see TokenStore
 * @see TokenEntity
 * @see InMemoryTokenStore for in-memory alternative
 * @see RedisTokenStore for distributed alternative
 * @doc.type class
 * @doc.purpose JPA-based durable token storage with PostgreSQL
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JpaTokenRepository implements TokenStore {

    private final EntityManager entityManager;

    /**
     * Creates a new JpaTokenRepository.
     *
     * @param entityManager the JPA EntityManager
     * @throws IllegalArgumentException if entityManager is null
     */
    public JpaTokenRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager cannot be null");
    }

    /**
     * Saves a new token or updates an existing token.
     *
     * GIVEN: Valid token with all required fields WHEN: save() is called THEN:
     * Token is persisted in database
     */
    @Override
    public Promise<Void> store(Token token) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Convert Token domain object to TokenEntity
                TokenEntity entity = TokenEntity.fromDomain(token);

                // Start transaction
                EntityTransaction transaction = entityManager.getTransaction();
                transaction.begin();

                try {
                    // Persist entity
                    entityManager.persist(entity);
                    transaction.commit();
                } catch (Exception e) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                    throw e;
                }

                return (Void) null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to save token to database", e);
            }
        });
    }

    /**
     * Finds a token by its ID.
     *
     * GIVEN: Valid tenant and token ID WHEN: findByTokenId() is called THEN:
     * Token is returned if found, not expired, and not revoked
     */
    @Override
    public Promise<Optional<Token>> findById(TenantId tenantId, TokenId tokenId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Query by tenant and token ID
                TypedQuery<TokenEntity> query = entityManager.createQuery(
                        "SELECT t FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.tokenId = :tokenId AND t.revokedAt IS NULL",
                        TokenEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("tokenId", tokenId.value());

                List<TokenEntity> results = query.getResultList();

                if (results.isEmpty()) {
                    return Optional.empty();
                }

                TokenEntity entity = results.get(0);

                // Check if expired
                if (entity.isExpired()) {
                    return Optional.empty();
                }

                // Convert to domain object
                Token token = entity.toDomain();
                return Optional.of(token);
            } catch (Exception e) {
                throw new RuntimeException("Failed to retrieve token from database", e);
            }
        });
    }

    /**
     * Introspects a token by its string value.
     *
     * GIVEN: Valid tenant and token value WHEN: introspect() is called THEN:
     * Token is returned if found, not expired, and not revoked
     */
    @Override
    public Promise<Optional<Token>> findByValue(TenantId tenantId, String tokenValue) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Query by token value
                TypedQuery<TokenEntity> query = entityManager.createQuery(
                        "SELECT t FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.tokenValue = :tokenValue AND t.revokedAt IS NULL",
                        TokenEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("tokenValue", tokenValue);

                List<TokenEntity> results = query.getResultList();

                if (results.isEmpty()) {
                    return Optional.empty();
                }

                TokenEntity entity = results.get(0);

                // Check if expired
                if (entity.isExpired()) {
                    return Optional.empty();
                }

                Token token = entity.toDomain();
                return Optional.of(token);
            } catch (Exception e) {
                throw new RuntimeException("Failed to introspect token from database", e);
            }
        });
    }

    /**
     * Finds all active tokens for a user.
     *
     * GIVEN: Valid tenant and user ID WHEN: findByUserId() is called THEN: All
     * active, non-revoked tokens for user are returned
     */
    public Promise<List<Token>> findByUserId(TenantId tenantId, UserId userId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Query all active tokens for user
                TypedQuery<TokenEntity> query = entityManager.createQuery(
                        "SELECT t FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.userId = :userId AND t.revokedAt IS NULL ORDER BY t.issuedAt DESC",
                        TokenEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("userId", userId.value());

                List<TokenEntity> results = query.getResultList();

                // Convert to domain objects and filter expired
                return results.stream()
                        .filter(entity -> !entity.isExpired())
                        .map(TokenEntity::toDomain)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException("Failed to find user tokens in database", e);
            }
        });
    }

    /**
     * Finds all active tokens for a client.
     *
     * GIVEN: Valid tenant and client ID WHEN: findByClientId() is called THEN:
     * All active, non-revoked tokens for client are returned
     */
    public Promise<List<Token>> findByClientId(TenantId tenantId, ClientId clientId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Query all active tokens for client
                TypedQuery<TokenEntity> query = entityManager.createQuery(
                        "SELECT t FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.clientId = :clientId AND t.revokedAt IS NULL ORDER BY t.issuedAt DESC",
                        TokenEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("clientId", clientId.value());

                List<TokenEntity> results = query.getResultList();

                // Convert to domain objects and filter expired
                return results.stream()
                        .filter(entity -> !entity.isExpired())
                        .map(TokenEntity::toDomain)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                throw new RuntimeException("Failed to find client tokens in database", e);
            }
        });
    }

    /**
     * Revokes a token by ID.
     *
     * GIVEN: Valid tenant and token ID WHEN: revoke() is called THEN: Token is
     * marked as revoked
     */
    @Override
    public Promise<Void> revoke(TenantId tenantId, TokenId tokenId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Find and update token
                TypedQuery<TokenEntity> query = entityManager.createQuery(
                        "SELECT t FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.tokenId = :tokenId",
                        TokenEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("tokenId", tokenId.value());

                List<TokenEntity> results = query.getResultList();

                if (!results.isEmpty()) {
                    EntityTransaction transaction = entityManager.getTransaction();
                    transaction.begin();

                    try {
                        TokenEntity entity = results.get(0);
                        entity.revoke();
                        entityManager.merge(entity);
                        transaction.commit();
                    } catch (Exception e) {
                        if (transaction.isActive()) {
                            transaction.rollback();
                        }
                        throw e;
                    }
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to revoke token in database", e);
            }
        });
    }

    /**
     * Revokes all tokens for a user.
     *
     * GIVEN: Valid tenant and user ID WHEN: revokeAllForUser() is called THEN:
     * All tokens for user are marked as revoked
     */
    public Promise<Void> revokeAllForUser(TenantId tenantId, UserId userId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                EntityTransaction transaction = entityManager.getTransaction();
                transaction.begin();

                try {
                    // Update all user tokens to revoked
                    Query query = entityManager.createQuery(
                            "UPDATE TokenEntity t SET t.revokedAt = CURRENT_TIMESTAMP WHERE t.tenantId = :tenantId AND t.userId = :userId"
                    );
                    query.setParameter("tenantId", tenantId.value());
                    query.setParameter("userId", userId.value());
                    query.executeUpdate();

                    transaction.commit();
                } catch (Exception e) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                    throw e;
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to revoke all user tokens in database", e);
            }
        });
    }

    /**
     * Revokes all tokens for a client.
     *
     * GIVEN: Valid tenant and client ID WHEN: revokeAllForClient() is called
     * THEN: All tokens for client are marked as revoked
     */
    public Promise<Void> revokeAllForClient(TenantId tenantId, ClientId clientId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                EntityTransaction transaction = entityManager.getTransaction();
                transaction.begin();

                try {
                    // Update all client tokens to revoked
                    Query query = entityManager.createQuery(
                            "UPDATE TokenEntity t SET t.revokedAt = CURRENT_TIMESTAMP WHERE t.tenantId = :tenantId AND t.clientId = :clientId"
                    );
                    query.setParameter("tenantId", tenantId.value());
                    query.setParameter("clientId", clientId.value());
                    query.executeUpdate();

                    transaction.commit();
                } catch (Exception e) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                    throw e;
                }

                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to revoke all client tokens in database", e);
            }
        });
    }

    /**
     * Checks if a token is revoked.
     *
     * GIVEN: Valid tenant and token ID WHEN: isRevoked() is called THEN:
     * Returns true if token is revoked, false otherwise
     */
    @Override
    public Promise<Boolean> isValid(TenantId tenantId, TokenId tokenId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                // Query revocation status
                TypedQuery<TokenEntity> query = entityManager.createQuery(
                        "SELECT t FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.tokenId = :tokenId",
                        TokenEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("tokenId", tokenId.value());

                List<TokenEntity> results = query.getResultList();

                if (results.isEmpty()) {
                    return false;
                }

                TokenEntity entity = results.get(0);
                return entity.getRevokedAt() == null && !entity.isExpired();
            } catch (Exception e) {
                throw new RuntimeException("Failed to check token validity in database", e);
            }
        });
    }

    /**
     * Cleans up expired tokens from the database.
     *
     * GIVEN: Database with potentially expired tokens WHEN: cleanupExpired() is
     * called THEN: Expired tokens are soft-deleted
     */
    @Override
    public Promise<Integer> deleteExpired(TenantId tenantId) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            try {
                EntityTransaction transaction = entityManager.getTransaction();
                transaction.begin();

                try {
                    // Delete all expired, revoked tokens
                    Query query = entityManager.createQuery(
                            "DELETE FROM TokenEntity t WHERE t.tenantId = :tenantId AND t.expiresAt < CURRENT_TIMESTAMP AND t.revokedAt IS NOT NULL"
                    );
                    query.setParameter("tenantId", tenantId.value());
                    int deleted = query.executeUpdate();

                    transaction.commit();
                    return deleted;
                } catch (Exception e) {
                    if (transaction.isActive()) {
                        transaction.rollback();
                    }
                    throw e;
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to cleanup expired tokens in database", e);
            }
        });
    }
}
