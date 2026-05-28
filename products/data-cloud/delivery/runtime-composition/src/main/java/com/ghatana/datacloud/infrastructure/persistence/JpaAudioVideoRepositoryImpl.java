package com.ghatana.datacloud.infrastructure.persistence;

import com.ghatana.datacloud.entity.modality.AudioVideoEntity;
import com.ghatana.datacloud.entity.modality.AudioVideoRepository;
import com.ghatana.datacloud.infrastructure.config.JpaThreadPoolConfig;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * JPA implementation of AudioVideoRepository with ActiveJ Promise support.
 *
 * @doc.type class
 * @doc.purpose JPA adapter for audio-video entity persistence with ActiveJ Promises
 * @doc.layer product
 * @doc.pattern Repository Adapter (Infrastructure Layer)
 */
public class JpaAudioVideoRepositoryImpl implements AudioVideoRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaAudioVideoRepositoryImpl.class);

    private final ExecutorService dbExecutor;

    @PersistenceContext
    private EntityManager entityManager;

    public JpaAudioVideoRepositoryImpl() {
        this(JpaThreadPoolConfig.fromEnvironment());
    }

    public JpaAudioVideoRepositoryImpl(JpaThreadPoolConfig config) {
        this(Objects.requireNonNull(config, "config must not be null").createExecutorService());
    }

    public JpaAudioVideoRepositoryImpl(ExecutorService dbExecutor) {
        this.dbExecutor = Objects.requireNonNull(dbExecutor, "dbExecutor must not be null");
    }

    @Override
    public Promise<Optional<AudioVideoEntity>> findById(UUID id) {
        Objects.requireNonNull(id, "ID must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            AudioVideoEntity entity = entityManager.find(AudioVideoEntity.class, id);
            log.debug("findById: id={}, found={}", id, entity != null);
            return Optional.ofNullable(entity);
        });
    }

    @Override
    public Promise<List<AudioVideoEntity>> findByTenantAndCollection(String tenantId, String collectionName) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<AudioVideoEntity> query = entityManager.createQuery(
                "SELECT av FROM AudioVideoEntity av JOIN av.entity e " +
                "WHERE e.tenantId = :tenantId AND e.collectionName = :collectionName " +
                "ORDER BY av.createdAt DESC",
                AudioVideoEntity.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("collectionName", collectionName);
            List<AudioVideoEntity> results = query.getResultList();
            log.debug("findByTenantAndCollection: tenantId={}, collection={}, found={}",
                tenantId, collectionName, results.size());
            return results;
        });
    }

    @Override
    public Promise<List<AudioVideoEntity>> findByModality(String tenantId, AudioVideoEntity.MediaModality modality) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(modality, "Modality must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<AudioVideoEntity> query = entityManager.createQuery(
                "SELECT av FROM AudioVideoEntity av JOIN av.entity e " +
                "WHERE e.tenantId = :tenantId AND av.modality = :modality " +
                "ORDER BY av.createdAt DESC",
                AudioVideoEntity.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("modality", modality);
            List<AudioVideoEntity> results = query.getResultList();
            log.debug("findByModality: tenantId={}, modality={}, found={}", tenantId, modality, results.size());
            return results;
        });
    }

    @Override
    public Promise<List<AudioVideoEntity>> findByTranscodingStatus(String tenantId, AudioVideoEntity.TranscodingStatus status) {
        Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            TypedQuery<AudioVideoEntity> query = entityManager.createQuery(
                "SELECT av FROM AudioVideoEntity av JOIN av.entity e " +
                "WHERE e.tenantId = :tenantId AND av.transcodingStatus = :status " +
                "ORDER BY av.createdAt ASC",
                AudioVideoEntity.class
            );
            query.setParameter("tenantId", tenantId);
            query.setParameter("status", status);
            List<AudioVideoEntity> results = query.getResultList();
            log.debug("findByTranscodingStatus: tenantId={}, status={}, found={}", tenantId, status, results.size());
            return results;
        });
    }

    @Override
    public Promise<AudioVideoEntity> save(AudioVideoEntity entity) {
        Objects.requireNonNull(entity, "Entity must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            AudioVideoEntity saved = entityManager.merge(entity);
            entityManager.flush();
            log.debug("save: id={}, modality={}", saved.getId(), saved.getModality());
            return saved;
        });
    }

    @Override
    public Promise<Void> delete(UUID id) {
        Objects.requireNonNull(id, "ID must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            AudioVideoEntity entity = entityManager.find(AudioVideoEntity.class, id);
            if (entity != null) {
                entityManager.remove(entity);
                entityManager.flush();
                log.debug("delete: id={}", id);
            }
            return null;
        });
    }

    @Override
    public Promise<AudioVideoEntity> updateTranscodingStatus(UUID id, AudioVideoEntity.TranscodingStatus status, String variants) {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(status, "Status must not be null");
        return Promise.ofBlocking(dbExecutor, () -> {
            AudioVideoEntity entity = entityManager.find(AudioVideoEntity.class, id);
            if (entity == null) {
                throw new IllegalArgumentException("AudioVideoEntity not found: " + id);
            }
            entity.setTranscodingStatus(status);
            if (variants != null) {
                entity.setTranscodingVariants(variants);
            }
            AudioVideoEntity saved = entityManager.merge(entity);
            entityManager.flush();
            log.debug("updateTranscodingStatus: id={}, status={}", id, status);
            return saved;
        });
    }
}
