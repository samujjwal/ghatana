/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.queue.impl;

import com.ghatana.orchestrator.queue.ExecutionQueueJob;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for execution queue jobs.
 *
 * <p>Uses PostgreSQL SKIP LOCKED for efficient distributed job claiming.
 *
 * @doc.type class
 * @doc.purpose JPA repository for durable execution queue
 * @doc.layer core
 * @doc.pattern Repository
 */
public class ExecutionQueueJobRepository {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionQueueJobRepository.class);

    private final EntityManager entityManager;

    public ExecutionQueueJobRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Save or update a job.
     */
    public ExecutionQueueJobEntity save(ExecutionQueueJobEntity entity) {
        if (entityManager.find(ExecutionQueueJobEntity.class, entity.getJobId()) != null) {
            return entityManager.merge(entity);
        } else {
            entityManager.persist(entity);
            return entity;
        }
    }

    /**
     * Find a job by ID.
     */
    public Optional<ExecutionQueueJobEntity> findById(String jobId) {
        return Optional.ofNullable(entityManager.find(ExecutionQueueJobEntity.class, jobId));
    }

    /**
     * Find a job by ID with pessimistic lock.
     */
    public Optional<ExecutionQueueJobEntity> findByIdForUpdate(String jobId) {
        return Optional.ofNullable(
            entityManager.find(ExecutionQueueJobEntity.class, jobId, LockModeType.PESSIMISTIC_WRITE)
        );
    }

    /**
     * Check if a job with idempotency key exists for tenant.
     */
    public boolean existsByTenantAndIdempotencyKey(String tenantId, String idempotencyKey) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(j) FROM ExecutionQueueJobEntity j " +
            "WHERE j.tenantId = :tenantId AND j.idempotencyKey = :idempotencyKey",
            Long.class
        )
        .setParameter("tenantId", tenantId)
        .setParameter("idempotencyKey", idempotencyKey)
        .getSingleResult();

        return count > 0;
    }

    /**
     * Poll for pending jobs using SKIP LOCKED for distributed claiming.
     *
     * <p>This query atomically claims jobs that are:
     * <ul>
     *   <li>In PENDING status</li>
     *   <li>Have no next_retry_at OR next_retry_at is in the past</li>
     *   <li>Not locked by another transaction (SKIP LOCKED)</li>
     * </ul>
     *
     * @param maxJobs Maximum number of jobs to return
     * @return List of claimed job entities
     */
    public List<ExecutionQueueJobEntity> pollPendingJobs(int maxJobs) {
        // Using native query for SKIP LOCKED support
        // Note: JPQL doesn't support SKIP LOCKED, so we use native SQL
        @SuppressWarnings("unchecked")
        List<ExecutionQueueJobEntity> jobs = entityManager.createNativeQuery(
            "SELECT * FROM execution_queue " +
            "WHERE status = 'PENDING' " +
            "AND (next_retry_at IS NULL OR next_retry_at <= NOW()) " +
            "ORDER BY enqueued_at ASC " +
            "LIMIT :maxJobs " +
            "FOR UPDATE SKIP LOCKED",
            ExecutionQueueJobEntity.class
        )
        .setParameter("maxJobs", maxJobs)
        .getResultList();

        return jobs;
    }

    /**
     * Find expired leases (timed out jobs).
     */
    public List<ExecutionQueueJobEntity> findExpiredLeases() {
        return entityManager.createQuery(
            "SELECT j FROM ExecutionQueueJobEntity j " +
            "WHERE j.status = :status " +
            "AND j.leaseExpiresAt IS NOT NULL " +
            "AND j.leaseExpiresAt < :now",
            ExecutionQueueJobEntity.class
        )
        .setParameter("status", ExecutionQueueJob.JobStatus.IN_PROGRESS)
        .setParameter("now", Instant.now())
        .getResultList();
    }

    /**
     * Count jobs by status.
     */
    public long countByStatus(ExecutionQueueJob.JobStatus status, String tenantId) {
        String jpql = "SELECT COUNT(j) FROM ExecutionQueueJobEntity j WHERE j.status = :status";
        if (tenantId != null) {
            jpql += " AND j.tenantId = :tenantId";
        }

        TypedQuery<Long> query = entityManager.createQuery(jpql, Long.class)
            .setParameter("status", status);

        if (tenantId != null) {
            query.setParameter("tenantId", tenantId);
        }

        return query.getSingleResult();
    }

    /**
     * Delete old completed/failed jobs.
     */
    public int deleteOldJobs(Instant completedBefore) {
        return entityManager.createQuery(
            "DELETE FROM ExecutionQueueJobEntity j " +
            "WHERE j.completedAt IS NOT NULL " +
            "AND j.completedAt < :completedBefore"
        )
        .setParameter("completedBefore", completedBefore)
        .executeUpdate();
    }

    /**
     * Get average processing time for completed jobs.
     */
    public Double getAverageProcessingTimeMs(String tenantId) {
        String jpql = "SELECT AVG(EXTRACT(EPOCH FROM (j.completedAt - j.enqueuedAt)) * 1000) " +
                     "FROM ExecutionQueueJobEntity j " +
                     "WHERE j.status = :status AND j.completedAt IS NOT NULL";

        if (tenantId != null) {
            jpql += " AND j.tenantId = :tenantId";
        }

        TypedQuery<Double> query = entityManager.createQuery(jpql, Double.class)
            .setParameter("status", ExecutionQueueJob.JobStatus.COMPLETED);

        if (tenantId != null) {
            query.setParameter("tenantId", tenantId);
        }

        return query.getSingleResult();
    }

    /**
     * Get oldest pending job age in milliseconds.
     */
    public Long getOldestPendingAgeMs(String tenantId) {
        String jpql = "SELECT MIN(j.enqueuedAt) FROM ExecutionQueueJobEntity j " +
                     "WHERE j.status = :status";

        if (tenantId != null) {
            jpql += " AND j.tenantId = :tenantId";
        }

        TypedQuery<Instant> query = entityManager.createQuery(jpql, Instant.class)
            .setParameter("status", ExecutionQueueJob.JobStatus.PENDING);

        if (tenantId != null) {
            query.setParameter("tenantId", tenantId);
        }

        Instant oldest = query.getSingleResult();
        if (oldest == null) {
            return 0L;
        }

        return Instant.now().toEpochMilli() - oldest.toEpochMilli();
    }
}

