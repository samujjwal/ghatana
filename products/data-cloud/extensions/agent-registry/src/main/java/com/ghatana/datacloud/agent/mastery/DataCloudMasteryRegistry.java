/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.agent.mastery;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Data Cloud-backed implementation of MasteryRegistry.
 *
 * <p>This implementation stores mastery items, transitions, and evidence in Data Cloud collections:
 * <ul>
 *   <li>agent-mastery-items: mastery items with scores and metadata</li>
 *   <li>agent-mastery-transitions: append-only transition log</li>
 *   <li>agent-mastery-evidence: evidence references and bundles</li>
 * </ul>
 *
 * <p>TODO: Replace in-memory storage with actual Data Cloud persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MasteryRegistry
 * @doc.layer data-cloud
 * @doc.pattern Repository
 */
public final class DataCloudMasteryRegistry implements MasteryRegistry {

    private final Map<String, MasteryItem> masteryItems = new ConcurrentHashMap<>();
    private final Map<String, MasteryTransition> transitions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> evidenceMap = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Promise<Optional<MasteryItem>> findBySkill(
            @NotNull String skillId,
            @NotNull EnvironmentFingerprint env
    ) {
        // Find mastery items matching the skill and environment
        List<MasteryItem> matches = masteryItems.values().stream()
                .filter(item -> item.skillId().equals(skillId))
                .filter(item -> item.applicability().tenantId().equals(env.tenantId()))
                .filter(MasteryItem::isActiveForRetrieval)
                .filter(item -> item.isFresh(Instant.now()))
                .collect(Collectors.toList());

        // TODO: Add version matching logic based on environment fingerprint
        if (matches.isEmpty()) {
            return Promise.of(Optional.empty());
        }

        // Return the highest-scored matching item
        MasteryItem best = matches.stream()
                .max(Comparator.comparingDouble(item -> item.score().executionScore()))
                .orElse(matches.get(0));

        return Promise.of(Optional.of(best));
    }

    @Override
    @NotNull
    public Promise<List<MasteryItem>> query(@NotNull MasteryQuery query) {
        return Promise.of(masteryItems.values().stream()
                .filter(item -> query.skillId() == null || item.skillId().equals(query.skillId()))
                .filter(item -> query.agentId() == null || item.agentId().equals(query.agentId()))
                .filter(item -> query.agentReleaseId() == null || item.agentReleaseId().equals(query.agentReleaseId()))
                .filter(item -> query.tenantId() == null || item.applicability().tenantId().equals(query.tenantId()))
                .filter(item -> query.domain() == null || item.domain().equals(query.domain()))
                .filter(item -> query.states() == null || query.states().contains(item.state()))
                .filter(item -> query.includeObsolete() != null && query.includeObsolete() || item.state() != MasteryState.OBSOLETE)
                .filter(item -> query.includeRetired() != null && query.includeRetired() || item.state() != MasteryState.RETIRED)
                .filter(item -> query.includeMaintenanceOnly() != null && query.includeMaintenanceOnly() || item.state() != MasteryState.MAINTENANCE_ONLY)
                .filter(item -> query.requireFreshness() == null || !query.requireFreshness() || item.isFresh(query.currentTime() != null ? query.currentTime() : Instant.now()))
                .sorted(Comparator.comparingDouble((MasteryItem item) -> item.score().executionScore()).reversed())
                .skip(query.offset() != null ? query.offset() : 0)
                .limit(query.limit() != null ? query.limit() : Long.MAX_VALUE)
                .collect(Collectors.toList()));
    }

    @Override
    @NotNull
    public Promise<MasteryItem> save(@NotNull MasteryItem item) {
        masteryItems.put(item.masteryId(), item);
        return Promise.of(item);
    }

    @Override
    @NotNull
    public Promise<MasteryTransitionResult> transition(@NotNull MasteryTransition transition) {
        MasteryItem item = masteryItems.get(transition.masteryId());
        if (item == null) {
            return Promise.of(MasteryTransitionResult.failure(
                    transition.masteryId(),
                    MasteryState.UNKNOWN,
                    "Mastery item not found: " + transition.masteryId()
            ));
        }

        // Validate transition
        if (!isValidTransition(item.state(), transition.toState(), transition.evidenceRefs())) {
            return Promise.of(MasteryTransitionResult.failure(
                    transition.masteryId(),
                    item.state(),
                    "Invalid transition from " + item.state() + " to " + transition.toState()
            ));
        }

        // Record transition
        transitions.put(transition.transitionId(), transition);

        // Update mastery item state
        MasteryItem updatedItem = new MasteryItem(
                item.masteryId(),
                item.skillId(),
                item.domain(),
                item.agentId(),
                item.agentReleaseId(),
                transition.toState(),
                item.versionScope(),
                item.applicability(),
                item.score(),
                item.procedureIds(),
                item.semanticFactIds(),
                item.negativeKnowledgeIds(),
                item.evidenceRefs(),
                item.evaluationRefs(),
                item.knownFailureModeIds(),
                Instant.now(),
                item.staleAfter(),
                item.labels()
        );

        masteryItems.put(item.masteryId(), updatedItem);

        return Promise.of(MasteryTransitionResult.success(
                item.masteryId(),
                item.state(),
                transition.toState(),
                transition.transitionId()
        ));
    }

    @Override
    @NotNull
    public Promise<List<MasteryItem>> findStale(@NotNull Instant now) {
        return Promise.of(masteryItems.values().stream()
                .filter(item -> !item.isFresh(now))
                .collect(Collectors.toList()));
    }

    /**
     * Validates whether a transition is allowed based on state and evidence.
     *
     * @param fromState current state
     * @param toState target state
     * @param evidenceRefs evidence references
     * @return true if transition is valid
     */
    private boolean isValidTransition(MasteryState fromState, MasteryState toState, Map<String, String> evidenceRefs) {
        // Direct UNKNOWN to MASTERED is not allowed
        if (fromState == MasteryState.UNKNOWN && toState == MasteryState.MASTERED) {
            return false;
        }

        // MASTERED requires evaluation evidence
        if (toState == MasteryState.MASTERED && evidenceRefs.isEmpty()) {
            return false;
        }

        // COMPETENT requires some evidence
        if (toState == MasteryState.COMPETENT && evidenceRefs.isEmpty()) {
            return false;
        }

        // OBSERVED can transition from UNKNOWN with minimal evidence
        if (toState == MasteryState.OBSERVED && fromState == MasteryState.UNKNOWN) {
            return true;
        }

        // QUARANTINED can be reached from any state (for safety)
        if (toState == MasteryState.QUARANTINED) {
            return true;
        }

        // OBSOLETE can be reached from any active state
        if (toState == MasteryState.OBSOLETE && fromState.isActiveForRetrieval()) {
            return true;
        }

        // RETIRED can only be reached from OBSOLETE
        if (toState == MasteryState.RETIRED && fromState == MasteryState.OBSOLETE) {
            return true;
        }

        // MAINTENANCE_ONLY can be reached from MASTERED
        if (toState == MasteryState.MAINTENANCE_ONLY && fromState == MasteryState.MASTERED) {
            return true;
        }

        // Forward progression through lifecycle
        if (fromState == MasteryState.OBSERVED && toState == MasteryState.PRACTICED) {
            return true;
        }

        if (fromState == MasteryState.PRACTICED && toState == MasteryState.COMPETENT) {
            return true;
        }

        if (fromState == MasteryState.COMPETENT && toState == MasteryState.MASTERED) {
            return !evidenceRefs.isEmpty();
        }

        return false;
    }
}
