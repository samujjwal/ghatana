/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.store;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * ActiveJ-friendly JPA repository for consumer offset persistence.
 */
public class ConsumerOffsetRepository extends AbstractRepository<ConsumerOffsetEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ConsumerOffsetRepository.class);

    public ConsumerOffsetRepository(EntityManagerFactory entityManagerFactory) {
        super(entityManagerFactory);
    }

    public Optional<ConsumerOffsetEntity> findByPartition(String tenantId, String consumerGroup, String partitionId) {
        return execute(em -> {
            TypedQuery<ConsumerOffsetEntity> query = em.createQuery(
                    "SELECT c FROM ConsumerOffsetEntity c WHERE c.tenantId = :tenantId AND c.consumerGroup = :consumerGroup AND c.partitionId = :partitionId",
                    ConsumerOffsetEntity.class);
            query.setParameter("tenantId", tenantId);
            query.setParameter("consumerGroup", consumerGroup);
            query.setParameter("partitionId", partitionId);
            List<ConsumerOffsetEntity> results = query.getResultList();
            return results.stream().findFirst();
        }, false);
    }

    public List<ConsumerOffsetEntity> findByConsumerGroup(String tenantId, String consumerGroup) {
        return execute(em -> {
            TypedQuery<ConsumerOffsetEntity> query = em.createQuery(
                    "SELECT c FROM ConsumerOffsetEntity c WHERE c.tenantId = :tenantId AND c.consumerGroup = :consumerGroup",
                    ConsumerOffsetEntity.class);
            query.setParameter("tenantId", tenantId);
            query.setParameter("consumerGroup", consumerGroup);
            return query.getResultList();
        }, false);
    }

    public ConsumerOffsetEntity save(ConsumerOffsetEntity entity) {
        return executeInsertOrUpdate(entity);
    }
}

