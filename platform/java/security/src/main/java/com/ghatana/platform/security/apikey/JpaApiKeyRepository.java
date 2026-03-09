package com.ghatana.platform.security.apikey;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA-backed persistent implementation of {@link ApiKeyRepository}.
 *
 * <p>Replaces {@link InMemoryApiKeyRepository} for production deployments where
 * API keys must survive application restarts. Uses a JPA {@link EntityManager}
 * directly to stay consistent with the platform's {@code database} module patterns.</p>
 *
 * <p>Callers are responsible for transaction boundaries (begin/commit/rollback).
 * Mutating operations ({@code save}, {@code deleteById}, {@code deleteByKey})
 * must be called within an active transaction.</p>
 *
 * @see ApiKeyRepository
 * @see InMemoryApiKeyRepository
 * @doc.type class
 * @doc.purpose JPA-backed persistent ApiKeyRepository for production use
 * @doc.layer infrastructure
 * @doc.pattern Repository
 */
public class JpaApiKeyRepository implements ApiKeyRepository {

    private final EntityManager entityManager;

    public JpaApiKeyRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "EntityManager cannot be null");
    }

    @Override
    public Optional<ApiKey> findByKey(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        TypedQuery<ApiKey> q = entityManager.createQuery(
                "SELECT k FROM ApiKey k WHERE k.key = :key", ApiKey.class);
        q.setParameter("key", key);
        return q.getResultStream().findFirst();
    }

    @Override
    public Optional<ApiKey> findById(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        return Optional.ofNullable(entityManager.find(ApiKey.class, id));
    }

    @Override
    public List<ApiKey> findByOwner(String owner) {
        Objects.requireNonNull(owner, "owner cannot be null");
        TypedQuery<ApiKey> q = entityManager.createQuery(
                "SELECT k FROM ApiKey k WHERE k.owner = :owner", ApiKey.class);
        q.setParameter("owner", owner);
        return q.getResultList();
    }

    @Override
    public List<ApiKey> findAll() {
        return entityManager.createQuery("SELECT k FROM ApiKey k", ApiKey.class).getResultList();
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        Objects.requireNonNull(apiKey, "apiKey cannot be null");
        if (entityManager.find(ApiKey.class, apiKey.getId()) == null) {
            entityManager.persist(apiKey);
            return apiKey;
        } else {
            return entityManager.merge(apiKey);
        }
    }

    @Override
    public boolean deleteById(String id) {
        Objects.requireNonNull(id, "id cannot be null");
        ApiKey existing = entityManager.find(ApiKey.class, id);
        if (existing == null) {
            return false;
        }
        entityManager.remove(existing);
        return true;
    }

    @Override
    public boolean deleteByKey(String key) {
        Objects.requireNonNull(key, "key cannot be null");
        return findByKey(key)
                .map(k -> { entityManager.remove(k); return true; })
                .orElse(false);
    }
}
