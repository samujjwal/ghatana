/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of LearningDeltaRepository.
 *
 * <p>Stores learning deltas in Data Cloud collections for governance and promotion tracking.
 * Uses LearningDeltaMapper for serialization/deserialization to/from Data Cloud entity data maps.
 *
 * <p>Tenant isolation: writes use the delta's own tenantId as the EntityRepository tenant key.
 * Cross-tenant query methods scan all tenant IDs encountered since instantiation. A volatile
 * in-memory reverse index (deltaId → entity UUID, deltaId → tenantId) is maintained so that
 * findById and updateState can route to the correct tenant partition without requiring a full
 * cross-tenant scan.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of LearningDeltaRepository backed by EntityRepository
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudLearningDeltaRepository implements LearningDeltaRepository {

    private static final String COLLECTION = "agent-learning-deltas";
    private static final int MAX_RESULTS = 10_000;

    private final EntityRepository entityRepository;

    /** Volatile reverse index: delta ID → entity UUID. Rebuilt by save() calls. */
    private final ConcurrentHashMap<String, UUID> deltaToEntityId = new ConcurrentHashMap<>();
    /** Volatile reverse index: delta ID → tenant ID. Rebuilt by save() calls. */
    private final ConcurrentHashMap<String, String> deltaToTenantId = new ConcurrentHashMap<>();
    /** All tenant IDs encountered since instantiation — used for cross-tenant scans. */
    private final Set<String> knownTenantIds = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new DataCloudLearningDeltaRepository.
     *
     * @param entityRepository Data Cloud entity repository for durable persistence
     */
    public DataCloudLearningDeltaRepository(@NotNull EntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    @NotNull
    public Promise<LearningDelta> save(@NotNull LearningDelta delta) {
        String tenantId = delta.tenantId();
        String deltaId = delta.deltaId();
        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(delta);

        UUID existingEntityId = deltaToEntityId.get(deltaId);
        if (existingEntityId == null) {
            // New delta — generate a fresh UUID and persist
            UUID newId = UUID.randomUUID();
            Entity entity = Entity.builder()
                    .id(newId)
                    .tenantId(tenantId)
                    .collectionName(COLLECTION)
                    .version(1)
                    .active(true)
                    .data(dataMap)
                    .createdBy(delta.proposedBy())
                    .build();
            return entityRepository.save(tenantId, entity)
                    .map(saved -> {
                        deltaToEntityId.put(deltaId, saved.getId());
                        deltaToTenantId.put(deltaId, tenantId);
                        knownTenantIds.add(tenantId);
                        return LearningDeltaMapper.fromDataMap(saved.getData());
                    });
        }

        // Existing delta — load current entity to preserve version for optimistic locking
        return entityRepository.findById(tenantId, COLLECTION, existingEntityId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        // Entity disappeared — re-create with the same UUID
                        Entity entity = Entity.builder()
                                .id(existingEntityId)
                                .tenantId(tenantId)
                                .collectionName(COLLECTION)
                                .version(1)
                                .active(true)
                                .data(dataMap)
                                .createdBy(delta.proposedBy())
                                .build();
                        return entityRepository.save(tenantId, entity);
                    }
                    Entity updated = opt.get().toBuilder().data(dataMap).build();
                    return entityRepository.save(tenantId, updated);
                })
                .map(saved -> LearningDeltaMapper.fromDataMap(saved.getData()));
    }

    @Override
    @NotNull
    public Promise<Optional<LearningDelta>> findById(@NotNull String deltaId) {
        String tenantId = deltaToTenantId.get(deltaId);
        UUID entityId = deltaToEntityId.get(deltaId);
        if (tenantId != null && entityId != null) {
            return entityRepository.findById(tenantId, COLLECTION, entityId)
                    .map(opt -> opt.map(e -> LearningDeltaMapper.fromDataMap(e.getData())));
        }

        // Fallback path: reverse indexes are volatile, so scan known tenant partitions.
        return findByIdAcrossKnownTenants(deltaId);
    }

    @Override
    @NotNull
    public Promise<Optional<LearningDelta>> findById(@NotNull String tenantId, @NotNull String deltaId) {
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.of(Optional.empty());
        }
        return entityRepository.findAll(tenantId, COLLECTION, Map.of("deltaId", deltaId), null, 0, 1)
                .map(entities -> {
                    Optional<Entity> match = firstByDeltaId(entities, deltaId);
                    if (match.isEmpty()) {
                        return Optional.empty();
                    }
                    Entity entity = match.get();
                    deltaToEntityId.put(deltaId, entity.getId());
                    deltaToTenantId.put(deltaId, tenantId);
                    knownTenantIds.add(tenantId);
                    return Optional.of(LearningDeltaMapper.fromDataMap(entity.getData()));
                });
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByAgentId(@NotNull String agentId) {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> agentId.equals(d.agentId()))
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findBySkillId(@NotNull String skillId) {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> skillId.equals(d.skillId()))
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByTenant(@NotNull String tenantId, @Nullable String agentId, @Nullable Integer limit, @Nullable Integer offset) {
        Map<String, Object> filter = new HashMap<>();
        if (agentId != null) {
            filter.put("agentId", agentId);
        }
        int effectiveOffset = offset != null ? offset : 0;
        int effectiveLimit = limit != null ? limit : MAX_RESULTS;
        return entityRepository.findAll(tenantId, COLLECTION, filter, null, 0, MAX_RESULTS)
                .map(entities -> entities.stream()
                        .map(e -> LearningDeltaMapper.fromDataMap(e.getData()))
                        .skip(effectiveOffset)
                        .limit(effectiveLimit)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByState(@NotNull LearningDeltaState state) {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> d.state() == state)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPendingEvaluation() {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> d.state() == LearningDeltaState.PROPOSED
                                || d.state() == LearningDeltaState.PENDING_EVALUATION)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPromotable() {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> d.state() == LearningDeltaState.EVALUATED
                                || d.state() == LearningDeltaState.APPROVED)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findObsolete(@NotNull Instant before) {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> d.state() == LearningDeltaState.OBSOLETE)
                        .filter(d -> d.proposedAt() != null && d.proposedAt().isBefore(before))
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState) {
        return doUpdateState(deltaId, newState, null);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(
            @NotNull String tenantId,
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState) {
        return doUpdateState(tenantId, deltaId, newState, null);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState,
            @NotNull String rejectionReason) {
        return doUpdateState(deltaId, newState, rejectionReason);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(
            @NotNull String tenantId,
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState,
            @NotNull String rejectionReason) {
        return doUpdateState(tenantId, deltaId, newState, rejectionReason);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateStateWithRejection(
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState,
            @NotNull String rejectionReason) {
        return doUpdateState(deltaId, newState, rejectionReason);
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPending(@NotNull String agentId) {
        return scanAllTenants()
                .map(deltas -> deltas.stream()
                        .filter(d -> agentId.equals(d.agentId()))
                        .filter(d -> d.state() == LearningDeltaState.PROPOSED
                                || d.state() == LearningDeltaState.PENDING_EVALUATION)
                        .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<LearningDelta> transition(@NotNull String deltaId, @NotNull LearningDeltaState state) {
        return doUpdateState(deltaId, state, null);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> transition(
            @NotNull String tenantId,
            @NotNull String deltaId,
            @NotNull LearningDeltaState state) {
        return doUpdateState(tenantId, deltaId, state, null);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> appendEvaluationResult(
            @NotNull String deltaId,
            @NotNull String evaluationRunId,
            @NotNull String outcome,
            @NotNull java.util.Map<String, Object> metrics) {
        String tenantId = deltaToTenantId.get(deltaId);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.ofException(new IllegalStateException(
                    "tenantId is required for appendEvaluationResult; use tenant-scoped overload"));
        }
        return appendEvaluationResult(tenantId, deltaId, evaluationRunId, outcome, metrics);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> appendEvaluationResult(
            @NotNull String tenantId,
            @NotNull String deltaId,
            @NotNull String evaluationRunId,
            @NotNull String outcome,
            @NotNull java.util.Map<String, Object> metrics) {
        return findEntityByTenantAndDeltaId(tenantId, deltaId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalStateException("Learning delta not found: " + deltaId));
                    }
                    Entity existing = opt.get();
                    LearningDelta current = LearningDeltaMapper.fromDataMap(existing.getData());
                    
                    // Append evaluation result to evaluation refs
                    java.util.List<String> updatedEvalRefs = new java.util.ArrayList<>(current.evaluationRefs());
                    updatedEvalRefs.add(evaluationRunId);
                    
                    Map<String, Object> updatedData = new HashMap<>(existing.getData());
                    updatedData.put("evaluationRefs", updatedEvalRefs);
                    
                    // Store evaluation outcome and metrics in metadata
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metadata = (java.util.Map<String, Object>) updatedData.getOrDefault("metadata", new HashMap<>());
                    java.util.Map<String, Object> newMetadata = new HashMap<>(metadata);
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> evalResults = (java.util.Map<String, Object>) newMetadata.getOrDefault("evaluationResults", new HashMap<>());
                    java.util.Map<String, Object> newEvalResults = new HashMap<>(evalResults);
                    newEvalResults.put(evaluationRunId, Map.of(
                            "outcome", outcome,
                            "metrics", metrics,
                            "timestamp", Instant.now().toEpochMilli()
                    ));
                    newMetadata.put("evaluationResults", newEvalResults);

                        appendLifecycleEvent(newMetadata, "evaluated", Map.of(
                            "deltaId", deltaId,
                            "evaluationRunId", evaluationRunId,
                            "outcome", outcome));

                    updatedData.put("metadata", newMetadata);
                    
                    Entity updated = existing.toBuilder().data(updatedData).build();
                    return entityRepository.save(tenantId, updated)
                            .map(saved -> LearningDeltaMapper.fromDataMap(saved.getData()));
                });
    }

    @Override
    @NotNull
    public Promise<LearningDelta> appendPromotionResult(
            @NotNull String deltaId,
            @NotNull String promotionId,
            @NotNull String outcome,
            @Nullable String reason) {
        String tenantId = deltaToTenantId.get(deltaId);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.ofException(new IllegalStateException(
                    "tenantId is required for appendPromotionResult; use tenant-scoped overload"));
        }
        return appendPromotionResult(tenantId, deltaId, promotionId, outcome, reason);
    }

    @Override
    @NotNull
    public Promise<LearningDelta> appendPromotionResult(
            @NotNull String tenantId,
            @NotNull String deltaId,
            @NotNull String promotionId,
            @NotNull String outcome,
            @Nullable String reason) {
        return findEntityByTenantAndDeltaId(tenantId, deltaId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalStateException("Learning delta not found: " + deltaId));
                    }
                    Entity existing = opt.get();
                    
                    Map<String, Object> updatedData = new HashMap<>(existing.getData());
                    
                    // Store promotion result in metadata
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metadata = (java.util.Map<String, Object>) updatedData.getOrDefault("metadata", new HashMap<>());
                    java.util.Map<String, Object> newMetadata = new HashMap<>(metadata);
                    newMetadata.put("promotionResult", Map.of(
                            "promotionId", promotionId,
                            "outcome", outcome,
                            "reason", reason != null ? reason : "",
                            "timestamp", Instant.now().toEpochMilli()
                    ));

                            appendLifecycleEvent(newMetadata, "promoted", Map.of(
                                "deltaId", deltaId,
                                "promotionId", promotionId,
                                "outcome", outcome,
                                "reason", reason != null ? reason : ""));

                    updatedData.put("metadata", newMetadata);
                    
                    Entity updated = existing.toBuilder().data(updatedData).build();
                    return entityRepository.save(tenantId, updated)
                            .map(saved -> LearningDeltaMapper.fromDataMap(saved.getData()));
                });
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Promise<LearningDelta> doUpdateState(
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState,
            @Nullable String rejectionReason) {
        String tenantId = deltaToTenantId.get(deltaId);
        if (tenantId == null || tenantId.isBlank()) {
            return Promise.ofException(new IllegalStateException(
                    "tenantId is required for state update; use tenant-scoped overload"));
        }
        return doUpdateState(tenantId, deltaId, newState, rejectionReason);
    }

    private Promise<LearningDelta> doUpdateState(
            @NotNull String tenantId,
            @NotNull String deltaId,
            @NotNull LearningDeltaState newState,
            @Nullable String rejectionReason) {
        return findEntityByTenantAndDeltaId(tenantId, deltaId)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.ofException(new IllegalStateException("Learning delta not found: " + deltaId));
                    }
                    Entity existing = opt.get();
                    LearningDelta current = LearningDeltaMapper.fromDataMap(existing.getData());
                    Instant now = Instant.now();
                    Map<String, Object> updatedData = new HashMap<>(existing.getData());
                    updatedData.put("state", newState.name());
                    if (newState == LearningDeltaState.EVALUATED && current.evaluatedAt() == null) {
                        updatedData.put("evaluatedAt", now.toEpochMilli());
                    }
                    if (newState == LearningDeltaState.PROMOTED && current.promotedAt() == null) {
                        updatedData.put("promotedAt", now.toEpochMilli());
                    }
                    if (newState == LearningDeltaState.REJECTED && current.rejectedAt() == null) {
                        updatedData.put("rejectedAt", now.toEpochMilli());
                    }
                    if (rejectionReason != null) {
                        updatedData.put("rejectionReason", rejectionReason);
                    }

                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> metadata = (java.util.Map<String, Object>) updatedData.getOrDefault("metadata", new HashMap<>());
                    java.util.Map<String, Object> newMetadata = new HashMap<>(metadata);
                    appendLifecycleEvent(newMetadata, "state-changed", Map.of(
                            "deltaId", deltaId,
                            "state", newState.name(),
                            "rejectionReason", rejectionReason != null ? rejectionReason : ""));
                    updatedData.put("metadata", newMetadata);

                    Entity updated = existing.toBuilder().data(updatedData).build();
                    return entityRepository.save(tenantId, updated)
                            .map(saved -> LearningDeltaMapper.fromDataMap(saved.getData()));
                });
    }

    private Promise<Optional<LearningDelta>> findByIdAcrossKnownTenants(@NotNull String deltaId) {
        if (knownTenantIds.isEmpty()) {
            return Promise.of(Optional.empty());
        }

        Promise<Optional<LearningDelta>> acc = Promise.of(Optional.empty());
        for (String tenant : knownTenantIds) {
            acc = acc.then(found -> {
                if (found.isPresent()) {
                    return Promise.of(found);
                }
                return entityRepository.findAll(tenant, COLLECTION, Map.of("deltaId", deltaId), null, 0, 1)
                        .map(entities -> {
                            Optional<Entity> match = firstByDeltaId(entities, deltaId);
                            if (match.isEmpty()) {
                                return Optional.empty();
                            }
                            Entity entity = match.get();
                            deltaToEntityId.put(deltaId, entity.getId());
                            deltaToTenantId.put(deltaId, tenant);
                            return Optional.of(LearningDeltaMapper.fromDataMap(entity.getData()));
                        });
            });
        }
        return acc;
    }

    private Promise<Optional<Entity>> findEntityByTenantAndDeltaId(
            @NotNull String tenantId,
            @NotNull String deltaId) {
        UUID entityId = deltaToEntityId.get(deltaId);
        if (entityId != null) {
            return entityRepository.findById(tenantId, COLLECTION, entityId)
                    .then(opt -> {
                        if (opt.isPresent()) {
                            return Promise.of(opt);
                        }
                        return entityRepository.findAll(tenantId, COLLECTION, Map.of("deltaId", deltaId), null, 0, 1)
                                .map(entities -> firstByDeltaId(entities, deltaId));
                    });
        }

        return entityRepository.findAll(tenantId, COLLECTION, Map.of("deltaId", deltaId), null, 0, 1)
                .map(entities -> {
                    Optional<Entity> match = firstByDeltaId(entities, deltaId);
                    if (match.isEmpty()) {
                        return Optional.empty();
                    }
                    Entity entity = match.get();
                    deltaToEntityId.put(deltaId, entity.getId());
                    deltaToTenantId.put(deltaId, tenantId);
                    knownTenantIds.add(tenantId);
                    return Optional.of(entity);
                });
    }

    private static Optional<Entity> firstByDeltaId(@NotNull List<Entity> entities, @NotNull String deltaId) {
        return entities.stream()
                .filter(entity -> deltaId.equals(String.valueOf(entity.getData().get("deltaId"))))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private static void appendLifecycleEvent(
            @NotNull Map<String, Object> metadata,
            @NotNull String eventType,
            @NotNull Map<String, Object> details) {
        List<Map<String, Object>> events = (List<Map<String, Object>>) metadata.getOrDefault("lifecycleEvents", new ArrayList<>());
        List<Map<String, Object>> updatedEvents = new ArrayList<>(events);
        Map<String, Object> event = new HashMap<>(details);
        event.put("eventType", eventType);
        event.put("timestamp", Instant.now().toEpochMilli());
        updatedEvents.add(Map.copyOf(event));
        metadata.put("lifecycleEvents", updatedEvents);
    }

    /**
     * Loads all deltas across all tenant IDs encountered since instantiation.
     * Used by interface methods that lack a tenantId parameter.
     */
    private Promise<List<LearningDelta>> scanAllTenants() {
        if (knownTenantIds.isEmpty()) {
            return Promise.of(new ArrayList<>());
        }
        Promise<List<LearningDelta>> acc = Promise.of(new ArrayList<>());
        for (String tenantId : knownTenantIds) {
            acc = acc.then(soFar ->
                    entityRepository.findAll(tenantId, COLLECTION, Map.of(), null, 0, MAX_RESULTS)
                            .map(entities -> {
                                List<LearningDelta> combined = new ArrayList<>(soFar);
                                entities.stream()
                                        .map(e -> LearningDeltaMapper.fromDataMap(e.getData()))
                                        .forEach(combined::add);
                                return combined;
                            }));
        }
        return acc;
    }
}
