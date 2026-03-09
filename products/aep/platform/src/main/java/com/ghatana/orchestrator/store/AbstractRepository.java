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

import java.util.function.Function;

/**
 * Base class for JPA repositories with manual EntityManager management.
 */
public abstract class AbstractRepository<E> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final EntityManagerFactory entityManagerFactory;

    protected AbstractRepository(EntityManagerFactory entityManagerFactory) {
        this.entityManagerFactory = entityManagerFactory;
    }

    protected E executeInsertOrUpdate(E entity) {
        return execute(em -> {
            if (entity == null) {
                throw new IllegalArgumentException("Entity cannot be null");
            }
            // Assuming entity has an ID or we can merge it.
            // Since we don't know the ID field here generically without reflection or interface,
            // we'll just use merge which handles both persist (if new) and update (if existing).
            // However, merge on a new entity without ID might fail if ID generation is not handled.
            // For ConsumerOffsetEntity, ID is generated.
            return em.merge(entity);
        }, true);
    }

    protected <T> T execute(Function<EntityManager, T> action, boolean transactional) {
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
            logger.error("Repository operation failed", e);
            throw e;
        } finally {
            if (em.isOpen()) {
                em.close();
            }
        }
    }
}

