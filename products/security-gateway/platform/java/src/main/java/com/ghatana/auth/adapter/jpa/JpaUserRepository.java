package com.ghatana.auth.adapter.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.ghatana.auth.core.port.UserRepository;
import com.ghatana.platform.domain.auth.User;
import com.ghatana.platform.domain.auth.UserStatus;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.domain.auth.UserId;

import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

/**
 * JPA implementation of UserRepository for PostgreSQL persistence.
 *
 * <p>
 * <b>Purpose</b><br>
 * Persists User domain models using JPA/Hibernate with PostgreSQL backend.
 * Provides CRUD operations with automatic tenant scoping.
 *
 * <p>
 * <b>Tenant Isolation</b><br>
 * All queries explicitly filter by tenant_id. Database constraints enforce: -
 * UNIQUE(tenant_id, email) - email unique per tenant - INDEX(tenant_id) - fast
 * tenant filtering
 *
 * <p>
 * <b>Async Model</b><br>
 * All database operations wrapped in Promise.ofBlocking() to avoid blocking
 * eventloop. Heavy operations (queries, saves) run on ForkJoinPool.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EntityManager em = entityManagerFactory.createEntityManager();
 * UserRepository repo = new JpaUserRepository(em);
 *
 * TenantId tenant = TenantId.of("tenant-1");
 * repo.findByEmail(tenant, "alice@example.com")
 *     .whenComplete((optUser, error) -> {
 *         if (error != null) {
 *             log.error("Query failed", error);
 *             return;
 *         }
 *         if (optUser.isPresent()) {
 *             User user = optUser.get();
 *             log.info("Found user: {}", user.getEmail());
 *         } else {
 *             log.info("User not found");
 *         }
 *     });
 * }</pre>
 *
 * @see UserRepository for interface contract
 * @see com.ghatana.auth.adapter.memory.InMemoryUserRepository for testing
 * @doc.type class
 * @doc.purpose JPA user repository adapter for PostgreSQL
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class JpaUserRepository implements UserRepository {

    private final EntityManager entityManager;
    private static final java.util.concurrent.ForkJoinPool DB_POOL = java.util.concurrent.ForkJoinPool.commonPool();

    /**
     * Create JPA user repository.
     *
     * @param entityManager JPA entity manager
     */
    public JpaUserRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Promise<User> save(User user) {
        if (user == null) {
            return Promise.ofException(
                    new IllegalArgumentException("user must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                UserEntity entity = new UserEntity(user.getTenantId().value(), user);
                entityManager.getTransaction().begin();
                entityManager.persist(entity);
                entityManager.getTransaction().commit();
                return entity.toDomain();
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                throw e;
            }
        });
    }

    @Override
    public Promise<Optional<User>> findByUserId(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (userId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("userId must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                TypedQuery<UserEntity> query = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.id = :id",
                        UserEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("id", UUID.fromString(userId.value().toString()));

                try {
                    UserEntity entity = query.getSingleResult();
                    return Optional.of(entity.toDomain());
                } catch (NoResultException e) {
                    return Optional.empty();
                }
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Optional<User>> findByEmail(TenantId tenantId, String email) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (email == null || email.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("email must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                TypedQuery<UserEntity> query = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.email = :email",
                        UserEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("email", email);

                try {
                    UserEntity entity = query.getSingleResult();
                    return Optional.of(entity.toDomain());
                } catch (NoResultException e) {
                    return Optional.empty();
                }
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Optional<User>> findByUsername(TenantId tenantId, String username) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (username == null || username.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("username must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                TypedQuery<UserEntity> query = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.email = :username",
                        UserEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("username", username);

                UserEntity entity = query.getSingleResult();
                return Optional.of(entity.toDomain());
            } catch (NoResultException e) {
                return Optional.empty();
            }
        });
    }

    @Override
    public Promise<java.util.List<User>> findAllByTenant(TenantId tenantId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                TypedQuery<UserEntity> query = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId",
                        UserEntity.class
                );
                query.setParameter("tenantId", tenantId.value());

                return query.getResultList().stream()
                        .map(UserEntity::toDomain)
                        .collect(java.util.stream.Collectors.toList());
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Optional<User>> authenticate(TenantId tenantId, String username, String password) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (username == null || username.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("username must not be null")
            );
        }
        if (password == null || password.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("password must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                TypedQuery<UserEntity> query = entityManager.createQuery(
                        "SELECT u FROM UserEntity u WHERE u.tenantId = :tenantId AND u.email = :username",
                        UserEntity.class
                );
                query.setParameter("tenantId", tenantId.value());
                query.setParameter("username", username);

                UserEntity entity = query.getSingleResult();
                // Note: Password verification should use BCrypt or similar - simplified here
                if (entity.getPasswordHash() != null && entity.getPasswordHash().equals(password)) {
                    return Optional.of(entity.toDomain());
                }
                return Optional.empty();
            } catch (NoResultException e) {
                return Optional.empty();
            } catch (Exception e) {
                throw e;
            }
        });
    }

    @Override
    public Promise<Void> delete(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (userId == null) {
            return Promise.ofException(
                    new IllegalArgumentException("userId must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                entityManager.getTransaction().begin();
                int deleted = entityManager.createQuery(
                        "DELETE FROM UserEntity u WHERE u.tenantId = :tenantId AND u.id = :id"
                )
                        .setParameter("tenantId", tenantId.value())
                        .setParameter("id", UUID.fromString(userId.value().toString()))
                        .executeUpdate();
                entityManager.getTransaction().commit();
                return null;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                throw e;
            }
        });
    }

    @Override
    public Promise<Void> updatePassword(TenantId tenantId, UserId userId, String newPassword) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (userId == null || userId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("userId must not be null")
            );
        }
        if (newPassword == null || newPassword.isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("newPassword must not be null or blank")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                entityManager.getTransaction().begin();

                UserEntity user = entityManager.find(UserEntity.class, UUID.fromString(userId.value()));
                if (user != null && tenantId.value().equals(user.getTenantId())) {
                    user.setPasswordHash(newPassword);
                    user.setUpdatedAt(Instant.now());
                    entityManager.merge(user);
                }

                entityManager.getTransaction().commit();
                return null;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                throw e;
            }
        });
    }

    @Override
    public Promise<Void> lockUser(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (userId == null || userId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("userId must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                entityManager.getTransaction().begin();

                UserEntity user = entityManager.find(UserEntity.class, UUID.fromString(userId.value()));
                if (user != null && tenantId.value().equals(user.getTenantId())) {
                    user.setStatus(UserStatus.LOCKED);
                    user.setUpdatedAt(Instant.now());
                    entityManager.merge(user);
                }

                entityManager.getTransaction().commit();
                return null;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                throw e;
            }
        });
    }

    @Override
    public Promise<Void> unlockUser(TenantId tenantId, UserId userId) {
        if (tenantId == null || tenantId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("tenantId must not be null")
            );
        }
        if (userId == null || userId.value().isBlank()) {
            return Promise.ofException(
                    new IllegalArgumentException("userId must not be null")
            );
        }

        return Promise.ofBlocking(DB_POOL, () -> {
            try {
                entityManager.getTransaction().begin();

                UserEntity user = entityManager.find(UserEntity.class, UUID.fromString(userId.value()));
                if (user != null && tenantId.value().equals(user.getTenantId())) {
                    user.setStatus(UserStatus.ACTIVE);
                    user.setUpdatedAt(Instant.now());
                    entityManager.merge(user);
                }

                entityManager.getTransaction().commit();
                return null;
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                throw e;
            }
        });
    }
}
