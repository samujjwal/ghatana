/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.learning.delta;

import com.ghatana.agent.learning.LearningDelta;
import com.ghatana.agent.learning.LearningDeltaRepository;
import com.ghatana.agent.learning.LearningDeltaState;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of LearningDeltaRepository.
 *
 * <p>Stores learning deltas in Data Cloud collections for governance and promotion tracking.
 * Uses LearningDeltaMapper for serialization/deserialization to/from Data Cloud entity data maps.
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence using Data Cloud entity API.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of LearningDeltaRepository
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudLearningDeltaRepository implements LearningDeltaRepository {

    // TODO: Replace with actual Data Cloud entity storage
    private final ConcurrentHashMap<String, Map<String, Object>> entityStore = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<LearningDelta> save(@NotNull LearningDelta delta) {
        Map<String, Object> dataMap = LearningDeltaMapper.toDataMap(delta);
        entityStore.put(delta.deltaId(), dataMap);
        return Promise.of(delta);
    }

    @Override
    @NotNull
    public Promise<Optional<LearningDelta>> findById(@NotNull String deltaId) {
        Map<String, Object> dataMap = entityStore.get(deltaId);
        if (dataMap == null) {
            return Promise.of(Optional.empty());
        }
        try {
            return Promise.of(Optional.of(LearningDeltaMapper.fromDataMap(dataMap)));
        } catch (Exception e) {
            return Promise.of(Optional.empty());
        }
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByAgentId(@NotNull String agentId) {
        return Promise.of(entityStore.values().stream()
                .filter(data -> agentId.equals(data.get("agentId")))
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findBySkillId(@NotNull String skillId) {
        return Promise.of(entityStore.values().stream()
                .filter(data -> skillId.equals(data.get("skillId")))
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByTenant(@NotNull String tenantId, @Nullable String agentId, @Nullable Integer limit, @Nullable Integer offset) {
        return Promise.of(entityStore.values().stream()
                .filter(data -> tenantId.equals(data.get("tenantId")))
                .filter(data -> agentId == null || agentId.equals(data.get("agentId")))
                .map(LearningDeltaMapper::fromDataMap)
                .skip(offset != null ? offset : 0)
                .limit(limit != null ? limit : Integer.MAX_VALUE)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findByState(@NotNull LearningDeltaState state) {
        String stateName = state.name();
        return Promise.of(entityStore.values().stream()
                .filter(data -> stateName.equals(data.get("state")))
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPendingEvaluation() {
        return Promise.of(entityStore.values().stream()
                .filter(data -> {
                    String state = (String) data.get("state");
                    return LearningDeltaState.PROPOSED.name().equals(state) 
                            || LearningDeltaState.PENDING_EVALUATION.name().equals(state);
                })
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPromotable() {
        return Promise.of(entityStore.values().stream()
                .filter(data -> {
                    String state = (String) data.get("state");
                    return LearningDeltaState.EVALUATED.name().equals(state) 
                            || LearningDeltaState.APPROVED.name().equals(state);
                })
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findObsolete(@NotNull Instant before) {
        long beforeMillis = before.toEpochMilli();
        return Promise.of(entityStore.values().stream()
                .filter(data -> {
                    Object proposedAt = data.get("proposedAt");
                    if (proposedAt == null) return false;
                    long proposedAtMillis = proposedAt instanceof Instant 
                            ? ((Instant) proposedAt).toEpochMilli()
                            : ((Number) proposedAt).longValue();
                    return proposedAtMillis < beforeMillis;
                })
                .filter(data -> LearningDeltaState.OBSOLETE.name().equals(data.get("state")))
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState) {
        Map<String, Object> dataMap = entityStore.get(deltaId);
        if (dataMap == null) {
            return Promise.of(null);
        }

        LearningDelta delta = LearningDeltaMapper.fromDataMap(dataMap);
        Instant now = Instant.now();
        
        Map<String, Object> updatedData = new HashMap<>(dataMap);
        updatedData.put("state", newState.name());
        updatedData.put("evaluatedAt", newState == LearningDeltaState.EVALUATED ? now : delta.evaluatedAt());
        updatedData.put("promotedAt", newState == LearningDeltaState.PROMOTED ? now : delta.promotedAt());
        updatedData.put("rejectedAt", newState == LearningDeltaState.REJECTED ? now : delta.rejectedAt());
        
        entityStore.put(deltaId, updatedData);
        return Promise.of(LearningDeltaMapper.fromDataMap(updatedData));
    }

    @Override
    @NotNull
    public Promise<LearningDelta> updateState(@NotNull String deltaId, @NotNull LearningDeltaState newState, @NotNull String rejectionReason) {
        Map<String, Object> dataMap = entityStore.get(deltaId);
        if (dataMap == null) {
            return Promise.of(null);
        }

        LearningDelta delta = LearningDeltaMapper.fromDataMap(dataMap);
        Instant now = Instant.now();
        
        Map<String, Object> updatedData = new HashMap<>(dataMap);
        updatedData.put("state", newState.name());
        updatedData.put("evaluatedAt", newState == LearningDeltaState.EVALUATED ? now : delta.evaluatedAt());
        updatedData.put("promotedAt", newState == LearningDeltaState.PROMOTED ? now : delta.promotedAt());
        updatedData.put("rejectedAt", newState == LearningDeltaState.REJECTED ? now : delta.rejectedAt());
        updatedData.put("rejectionReason", rejectionReason);
        
        entityStore.put(deltaId, updatedData);
        return Promise.of(LearningDeltaMapper.fromDataMap(updatedData));
    }

    @Override
    @NotNull
    public Promise<List<LearningDelta>> findPending(@NotNull String agentId) {
        return Promise.of(entityStore.values().stream()
                .filter(data -> agentId.equals(data.get("agentId")))
                .filter(data -> {
                    String state = (String) data.get("state");
                    return LearningDeltaState.PROPOSED.name().equals(state) 
                            || LearningDeltaState.PENDING_EVALUATION.name().equals(state);
                })
                .map(LearningDeltaMapper::fromDataMap)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<LearningDelta> transition(@NotNull String deltaId, @NotNull LearningDeltaState state) {
        return updateState(deltaId, state);
    }
}
