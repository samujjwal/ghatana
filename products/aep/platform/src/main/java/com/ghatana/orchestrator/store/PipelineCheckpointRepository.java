/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * ActiveJ-friendly JPA repository for pipeline checkpoint persistence.
 * Replaces Spring Data dependencies with manual EntityManager usage.
 */
public class PipelineCheckpointRepository {
    private static final Logger logger = LoggerFactory.getLogger(PipelineCheckpointRepository.class);

    private final EntityManagerFactory entityManagerFactory;

    public PipelineCheckpointRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public Optional<PipelineCheckpointEntity> findById(String id) {
        return execute(em -> Optional.ofNullable(em.find(PipelineCheckpointEntity.class, id)), false);
    }

    public Optional<PipelineCheckpointEntity> findByIdempotencyKey(String tenantId, String idempotencyKey) {
        return execute(em -> {
            TypedQuery<PipelineCheckpointEntity> query = em.createQuery(
                    "SELECT c FROM PipelineCheckpointEntity c WHERE c.tenantId = :tenantId AND c.idempotencyKey = :idempotencyKey",
                    PipelineCheckpointEntity.class);
            query.setParameter("tenantId", tenantId);
            query.setParameter("idempotencyKey", idempotencyKey);
            List<PipelineCheckpointEntity> results = query.getResultList();
            return results.stream().findFirst();
        }, false);
    }

    public boolean existsByIdempotencyKey(String tenantId, String idempotencyKey) {
        return execute(em -> em.createQuery(
                "SELECT COUNT(c) > 0 FROM PipelineCheckpointEntity c WHERE c.tenantId = :tenantId AND c.idempotencyKey = :idempotencyKey",
                Boolean.class)
            .setParameter("tenantId", tenantId)
            .setParameter("idempotencyKey", idempotencyKey)
            .getSingleResult(), false);
    }

    public PipelineCheckpointEntity save(PipelineCheckpointEntity entity) {
        return executeInsertOrUpdate(entity);
    }

    public Optional<PipelineCheckpointEntity> findByInstanceId(String instanceId) {
        return execute(em -> {
            TypedQuery<PipelineCheckpointEntity> query = em.createQuery(
                    "SELECT c FROM PipelineCheckpointEntity c WHERE c.instanceId = :instanceId",
                    PipelineCheckpointEntity.class);
            query.setParameter("instanceId", instanceId);
            List<PipelineCheckpointEntity> results = query.getResultList();
            return results.stream().findFirst();
        }, false);
    }

    public List<PipelineCheckpointEntity> findByPipelineIdOrderByCreatedAtDesc(String tenantId, String pipelineId) {
        return execute(em -> em.createQuery(
                "SELECT c FROM PipelineCheckpointEntity c WHERE c.tenantId = :tenantId AND c.pipelineId = :pipelineId ORDER BY c.createdAt DESC",
                PipelineCheckpointEntity.class)
            .setParameter("tenantId", tenantId)
            .setParameter("pipelineId", pipelineId)
            .getResultList(), false);
    }

    public List<PipelineCheckpointEntity> findActiveExecutions() {
        return execute(em -> em.createQuery(
                "SELECT c FROM PipelineCheckpointEntity c WHERE c.status IN ('CREATED', 'RUNNING') ORDER BY c.createdAt DESC",
                PipelineCheckpointEntity.class)
            .getResultList(), false);
    }

    public List<PipelineCheckpointEntity> findStaleExecutions(Instant staleBefore) {
        return execute(em -> em.createQuery(
                "SELECT c FROM PipelineCheckpointEntity c WHERE c.status IN ('CREATED', 'RUNNING') AND c.createdAt < :staleBefore ORDER BY c.createdAt ASC",
                PipelineCheckpointEntity.class)
            .setParameter("staleBefore", staleBefore)
            .getResultList(), false);
    }

    public int deleteCompletedBefore(Instant completedBefore) {
        return execute(em -> em.createQuery(
                "DELETE FROM PipelineCheckpointEntity c WHERE c.status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND c.updatedAt < :completedBefore")
            .setParameter("completedBefore", completedBefore)
            .executeUpdate(), true);
    }

    public long countActiveExecutions() {
        return execute(em -> em.createQuery(
                "SELECT COUNT(c) FROM PipelineCheckpointEntity c WHERE c.status IN ('CREATED', 'RUNNING')",
                Long.class)
            .getSingleResult(), false);
    }

    public long countByStatus(PipelineCheckpointStatus status) {
        return execute(em -> em.createQuery(
                "SELECT COUNT(c) FROM PipelineCheckpointEntity c WHERE c.status = :status",
                Long.class)
            .setParameter("status", status)
            .getSingleResult(), false);
    }

    public List<PipelineCheckpointEntity> findRecentExecutions(String pipelineId, Instant since) {
        return execute(em -> em.createQuery(
                "SELECT c FROM PipelineCheckpointEntity c WHERE c.pipelineId = :pipelineId AND c.createdAt > :since ORDER BY c.createdAt DESC",
                PipelineCheckpointEntity.class)
            .setParameter("pipelineId", pipelineId)
            .setParameter("since", since)
            .getResultList(), false);
    }

    public List<PipelineCheckpointEntity> findAll() {
        return execute(em -> {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<PipelineCheckpointEntity> cq = cb.createQuery(PipelineCheckpointEntity.class);
            Root<PipelineCheckpointEntity> root = cq.from(PipelineCheckpointEntity.class);
            cq.select(root);
            return em.createQuery(cq).getResultList();
        }, false);
    }

    private PipelineCheckpointEntity executeInsertOrUpdate(PipelineCheckpointEntity entity) {
        return execute(em -> {
            if (entity == null) {
                throw new IllegalArgumentException("PipelineCheckpointEntity cannot be null");
            }
            if (entity.getInstanceId() == null) {
                em.persist(entity);
                return entity;
            }
            return em.merge(entity);
        }, true);
    }

    private <T> T execute(Function<EntityManager, T> action, boolean transactional) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        boolean started = false;
        try {
            if (transactional && !tx.isActive()) {
                tx.begin();
                started = true;
            }
            T result = action.apply(em);
            if (started) {
                tx.commit();
            }
            return result;
        } catch (RuntimeException e) {
            if (started && tx.isActive()) {
                tx.rollback();
            }
            logger.error("Pipeline checkpoint repository operation failed", e);
            throw e;
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }
}
