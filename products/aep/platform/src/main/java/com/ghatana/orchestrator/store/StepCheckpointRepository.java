/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * ActiveJ-friendly JPA repository for step checkpoint persistence.
 */
public class StepCheckpointRepository {
    private static final Logger logger = LoggerFactory.getLogger(StepCheckpointRepository.class);

    private final EntityManagerFactory entityManagerFactory;

    public StepCheckpointRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    public StepCheckpointEntity save(StepCheckpointEntity entity) {
        return execute(em -> {
            if (entity == null) {
                throw new IllegalArgumentException("StepCheckpointEntity cannot be null");
            }
            if (entity.getId() == null) {
                em.persist(entity);
                return entity;
            }
            return em.merge(entity);
        }, true);
    }

    public Optional<StepCheckpointEntity> findByInstanceIdAndStepId(String instanceId, String stepId) {
        return execute(em -> em.createQuery(
                "SELECT s FROM StepCheckpointEntity s WHERE s.instanceId = :instanceId AND s.stepId = :stepId",
                StepCheckpointEntity.class)
            .setParameter("instanceId", instanceId)
            .setParameter("stepId", stepId)
            .getResultList().stream().findFirst(), false);
    }

    public List<StepCheckpointEntity> findByInstanceIdOrderByStartedAtAsc(String instanceId) {
        return execute(em -> em.createQuery(
                "SELECT s FROM StepCheckpointEntity s WHERE s.instanceId = :instanceId ORDER BY s.startedAt ASC",
                StepCheckpointEntity.class)
            .setParameter("instanceId", instanceId)
            .getResultList(), false);
    }

    public Optional<StepCheckpointEntity> findLastSuccessfulStep(String instanceId) {
        return execute(em -> em.createQuery(
                "SELECT s FROM StepCheckpointEntity s WHERE s.instanceId = :instanceId AND s.status = :status ORDER BY s.startedAt DESC",
                StepCheckpointEntity.class)
            .setParameter("instanceId", instanceId)
            .setParameter("status", PipelineCheckpointStatus.STEP_SUCCESS)
            .setMaxResults(1)
            .getResultList().stream().findFirst(), false);
    }

    public List<StepCheckpointEntity> findFailedSteps(String instanceId) {
        return execute(em -> em.createQuery(
                "SELECT s FROM StepCheckpointEntity s WHERE s.instanceId = :instanceId AND s.status = :status ORDER BY s.startedAt DESC",
                StepCheckpointEntity.class)
            .setParameter("instanceId", instanceId)
            .setParameter("status", PipelineCheckpointStatus.STEP_FAILED)
            .getResultList(), false);
    }

    public long countCompletedSteps(String instanceId) {
        return execute(em -> em.createQuery(
                "SELECT COUNT(s) FROM StepCheckpointEntity s WHERE s.instanceId = :instanceId AND s.status = :status",
                Long.class)
            .setParameter("instanceId", instanceId)
            .setParameter("status", PipelineCheckpointStatus.STEP_SUCCESS)
            .getSingleResult(), false);
    }

    public List<StepCheckpointEntity> findLongRunningSteps(Instant staleBefore) {
        return execute(em -> em.createQuery(
                "SELECT s FROM StepCheckpointEntity s WHERE s.status = :status AND s.startedAt < :staleBefore",
                StepCheckpointEntity.class)
            .setParameter("status", PipelineCheckpointStatus.RUNNING)
            .setParameter("staleBefore", staleBefore)
            .getResultList(), false);
    }

    public int deleteForCompletedPipelines(Instant completedBefore) {
        return execute(em -> em.createQuery(
                "DELETE FROM StepCheckpointEntity s WHERE s.instanceId IN (" +
                        "SELECT c.instanceId FROM PipelineCheckpointEntity c WHERE c.status IN (:completed, :failed, :cancelled) " +
                        "AND c.updatedAt < :completedBefore)")
            .setParameter("completed", PipelineCheckpointStatus.COMPLETED)
            .setParameter("failed", PipelineCheckpointStatus.FAILED)
            .setParameter("cancelled", PipelineCheckpointStatus.CANCELLED)
            .setParameter("completedBefore", completedBefore)
            .executeUpdate(), true);
    }

    public List<StepCheckpointEntity> findHighRetrySteps(int minRetries) {
        return execute(em -> em.createQuery(
                "SELECT s FROM StepCheckpointEntity s WHERE s.retryCount > :minRetries ORDER BY s.retryCount DESC, s.startedAt DESC",
                StepCheckpointEntity.class)
            .setParameter("minRetries", minRetries)
            .getResultList(), false);
    }

    public Double getAverageStepDurationMs(String stepName) {
        return execute(em -> em.createQuery(
                "SELECT AVG((EXTRACT(EPOCH FROM (s.completedAt - s.startedAt)) * 1000)) FROM StepCheckpointEntity s " +
                        "WHERE s.stepName = :stepName AND s.status = :status AND s.completedAt IS NOT NULL",
                Double.class)
            .setParameter("stepName", stepName)
            .setParameter("status", PipelineCheckpointStatus.STEP_SUCCESS)
            .getSingleResult(), false);
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
            logger.error("Step checkpoint repository operation failed", e);
            throw e;
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }
}