/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.registry;

import com.ghatana.aep.pattern.lifecycle.PatternLifecycleEvent;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleEventType;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleRepository;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleState;
import com.ghatana.aep.registry.store.PatternLifecycleEventEntity;
import com.ghatana.aep.registry.store.PatternLifecycleStateEntity;
import io.activej.promise.Promise;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of {@link PatternLifecycleRepository}.
 *
 * <p>This implementation uses Data Cloud's JPA persistence layer for durable storage
 * of pattern lifecycle state and events in PostgreSQL. All state transitions and events
 * are persisted with tenant isolation, audit metadata, and trace IDs.
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed durable pattern lifecycle storage using JPA
 * @doc.layer product
 * @doc.pattern Repository Implementation
 */
public class DataCloudPatternLifecycleRepository implements PatternLifecycleRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudPatternLifecycleRepository.class);
    private static final Executor BLOCKING_EXECUTOR = ForkJoinPool.commonPool();

    private final EntityManager entityManager;

    public DataCloudPatternLifecycleRepository(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager required");
    }

    @Override
    public Promise<Void> saveState(String tenantId, String patternId, PatternLifecycleState state) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                entityManager.getTransaction().begin();
                
                TypedQuery<PatternLifecycleStateEntity> query = entityManager.createQuery(
                    "SELECT s FROM PatternLifecycleStateEntity s WHERE s.tenantId = :tenantId AND s.patternId = :patternId",
                    PatternLifecycleStateEntity.class);
                query.setParameter("tenantId", tenantId);
                query.setParameter("patternId", patternId);
                
                PatternLifecycleStateEntity entity;
                try {
                    entity = query.getSingleResult();
                    entity.setStateAsEnum(state);
                } catch (NoResultException e) {
                    entity = new PatternLifecycleStateEntity(tenantId, patternId, state);
                    entityManager.persist(entity);
                }
                
                entityManager.getTransaction().commit();
                log.debug("[pattern-lifecycle] Saved state tenant={} pattern={} state={}", 
                    tenantId, patternId, state);
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("[pattern-lifecycle] Failed to save state tenant={} pattern={}: {}", 
                    tenantId, patternId, e.getMessage(), e);
                throw new RuntimeException("Failed to save pattern lifecycle state", e);
            }
        });
    }

    @Override
    public Promise<Optional<PatternLifecycleState>> getState(String tenantId, String patternId) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                TypedQuery<PatternLifecycleStateEntity> query = entityManager.createQuery(
                    "SELECT s FROM PatternLifecycleStateEntity s WHERE s.tenantId = :tenantId AND s.patternId = :patternId",
                    PatternLifecycleStateEntity.class);
                query.setParameter("tenantId", tenantId);
                query.setParameter("patternId", patternId);
                
                PatternLifecycleStateEntity entity = query.getSingleResult();
                return Optional.of(entity.getStateAsEnum());
            } catch (NoResultException e) {
                return Optional.empty();
            } catch (Exception e) {
                log.error("[pattern-lifecycle] Failed to get state tenant={} pattern={}: {}", 
                    tenantId, patternId, e.getMessage(), e);
                throw new RuntimeException("Failed to get pattern lifecycle state", e);
            }
        });
    }

    @Override
    public Promise<Void> saveEvent(PatternLifecycleEvent event) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                entityManager.getTransaction().begin();
                
                PatternLifecycleEventEntity entity = new PatternLifecycleEventEntity(
                    event.tenantId(),
                    event.patternId(),
                    event.from().name(),
                    event.to().name(),
                    event.eventType(),
                    event.actor(),
                    event.occurredAt()
                );
                
                // Store evidence as JSON string
                if (event.evidence() != null && !event.evidence().isEmpty()) {
                    entity.setEvidence(evidenceToJson(event.evidence()));
                }
                
                // Extract trace ID from evidence if present
                if (event.evidence() != null && event.evidence().containsKey("traceId")) {
                    entity.setTraceId(event.evidence().get("traceId").toString());
                }
                
                // Extract policy decision from evidence if present
                if (event.evidence() != null && event.evidence().containsKey("policyDecision")) {
                    entity.setPolicyDecision(event.evidence().get("policyDecision").toString());
                }
                
                // Extract confidence from evidence if present
                if (event.evidence() != null && event.evidence().containsKey("confidence")) {
                    Object confidence = event.evidence().get("confidence");
                    if (confidence instanceof Number) {
                        entity.setConfidence(((Number) confidence).doubleValue());
                    }
                }
                
                entityManager.persist(entity);
                entityManager.getTransaction().commit();
                
                log.debug("[pattern-lifecycle] Saved event tenant={} pattern={} eventId={}", 
                    event.tenantId(), event.patternId(), event.eventId());
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("[pattern-lifecycle] Failed to save event tenant={} pattern={}: {}", 
                    event.tenantId(), event.patternId(), e.getMessage(), e);
                throw new RuntimeException("Failed to save pattern lifecycle event", e);
            }
        });
    }

    @Override
    public Promise<List<PatternLifecycleEvent>> getEvents(String tenantId, String patternId) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                TypedQuery<PatternLifecycleEventEntity> query = entityManager.createQuery(
                    "SELECT e FROM PatternLifecycleEventEntity e WHERE e.tenantId = :tenantId AND e.patternId = :patternId ORDER BY e.occurredAt ASC",
                    PatternLifecycleEventEntity.class);
                query.setParameter("tenantId", tenantId);
                query.setParameter("patternId", patternId);
                
                List<PatternLifecycleEventEntity> entities = query.getResultList();
                return entities.stream()
                    .map(this::toDomainEvent)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("[pattern-lifecycle] Failed to get events tenant={} pattern={}: {}", 
                    tenantId, patternId, e.getMessage(), e);
                throw new RuntimeException("Failed to get pattern lifecycle events", e);
            }
        });
    }

    @Override
    public Promise<Void> delete(String tenantId, String patternId) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                entityManager.getTransaction().begin();
                
                // Delete events first (foreign key dependency)
                TypedQuery<PatternLifecycleEventEntity> eventQuery = entityManager.createQuery(
                    "DELETE FROM PatternLifecycleEventEntity e WHERE e.tenantId = :tenantId AND e.patternId = :patternId",
                    PatternLifecycleEventEntity.class);
                eventQuery.setParameter("tenantId", tenantId);
                eventQuery.setParameter("patternId", patternId);
                eventQuery.executeUpdate();
                
                // Delete state
                TypedQuery<PatternLifecycleStateEntity> stateQuery = entityManager.createQuery(
                    "DELETE FROM PatternLifecycleStateEntity s WHERE s.tenantId = :tenantId AND s.patternId = :patternId",
                    PatternLifecycleStateEntity.class);
                stateQuery.setParameter("tenantId", tenantId);
                stateQuery.setParameter("patternId", patternId);
                stateQuery.executeUpdate();
                
                entityManager.getTransaction().commit();
                log.debug("[pattern-lifecycle] Deleted tenant={} pattern={}", tenantId, patternId);
            } catch (Exception e) {
                if (entityManager.getTransaction().isActive()) {
                    entityManager.getTransaction().rollback();
                }
                log.error("[pattern-lifecycle] Failed to delete tenant={} pattern={}: {}", 
                    tenantId, patternId, e.getMessage(), e);
                throw new RuntimeException("Failed to delete pattern lifecycle", e);
            }
        });
    }

    @Override
    public Promise<List<String>> getPatternsByState(String tenantId, PatternLifecycleState state) {
        return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> {
            try {
                TypedQuery<PatternLifecycleStateEntity> query = entityManager.createQuery(
                    "SELECT s FROM PatternLifecycleStateEntity s WHERE s.tenantId = :tenantId AND s.state = :state",
                    PatternLifecycleStateEntity.class);
                query.setParameter("tenantId", tenantId);
                query.setParameter("state", state.name());
                
                List<PatternLifecycleStateEntity> entities = query.getResultList();
                return entities.stream()
                    .map(PatternLifecycleStateEntity::getPatternId)
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("[pattern-lifecycle] Failed to get patterns by state tenant={} state={}: {}", 
                    tenantId, state, e.getMessage(), e);
                throw new RuntimeException("Failed to get patterns by state", e);
            }
        });
    }

    private PatternLifecycleEvent toDomainEvent(PatternLifecycleEventEntity entity) {
        return new PatternLifecycleEvent(
            entity.getId(),
            entity.getPatternId(),
            entity.getTenantId(),
            PatternLifecycleState.valueOf(entity.getFromState()),
            PatternLifecycleState.valueOf(entity.getToState()),
            entity.getEventTypeAsEnum(),
            entity.getActor(),
            entity.getOccurredAt(),
            jsonToEvidence(entity.getEvidence(), entity.getTraceId(), entity.getPolicyDecision(), entity.getConfidence())
        );
    }

    private String evidenceToJson(java.util.Map<String, Object> evidence) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(evidence);
        } catch (Exception e) {
            log.warn("[pattern-lifecycle] Failed to serialize evidence: {}", e.getMessage());
            return "{}";
        }
    }

    private java.util.Map<String, Object> jsonToEvidence(String evidenceJson, String traceId, String policyDecision, Double confidence) {
        java.util.Map<String, Object> evidence = new java.util.HashMap<>();
        
        if (evidenceJson != null && !evidenceJson.isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.Map<String, Object> parsed = mapper.readValue(evidenceJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
                evidence.putAll(parsed);
            } catch (Exception e) {
                log.warn("[pattern-lifecycle] Failed to deserialize evidence: {}", e.getMessage());
            }
        }
        
        // Ensure trace ID is in evidence
        if (traceId != null && !traceId.isBlank()) {
            evidence.put("traceId", traceId);
        }
        
        // Ensure policy decision is in evidence
        if (policyDecision != null && !policyDecision.isBlank()) {
            evidence.put("policyDecision", policyDecision);
        }
        
        // Ensure confidence is in evidence
        if (confidence != null) {
            evidence.put("confidence", confidence);
        }
        
        return evidence;
    }
}
