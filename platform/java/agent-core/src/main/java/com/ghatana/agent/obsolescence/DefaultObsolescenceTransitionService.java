/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.MasteryTransitionResult;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default implementation of ObsolescenceTransitionService.
 *
 * <p>Processes obsolescence events by validating them, creating mastery transitions,
 * and routing them through the mastery registry.
 *
 * @doc.type class
 * @doc.purpose Default implementation of ObsolescenceTransitionService
 * @doc.layer agent-core
 * @doc.pattern Service
 */
public final class DefaultObsolescenceTransitionService implements ObsolescenceTransitionService {

    private final MasteryRegistry masteryRegistry;

    /**
     * Creates a default obsolescence transition service.
     *
     * @param masteryRegistry mastery registry for routing transitions
     */
    public DefaultObsolescenceTransitionService(@NotNull MasteryRegistry masteryRegistry) {
        this.masteryRegistry = Objects.requireNonNull(masteryRegistry, "masteryRegistry must not be null");
    }

    @Override
    @NotNull
    public Promise<MasteryTransitionResult> processObsolescenceEvent(@NotNull ObsolescenceEvent event) {
        // Validate the event
        if (!validateObsolescenceEvent(event)) {
            return Promise.of(MasteryTransitionResult.failure(
                    event.masteryId(),
                    MasteryState.UNKNOWN,
                    "Invalid obsolescence event: " + event.eventId()
            ));
        }

        // P0 FIX: Use getById with tenantId instead of query to get the exact item
        return masteryRegistry.getById(event.tenantId(), event.masteryId()).then(itemOpt -> {
            if (itemOpt.isEmpty()) {
                return Promise.of(MasteryTransitionResult.failure(
                        event.masteryId(),
                        MasteryState.UNKNOWN,
                        "Mastery item not found for tenant " + event.tenantId() + ": " + event.masteryId()
                ));
            }

            MasteryItem item = itemOpt.get();

            // P0 FIX: Use actual item state, skill ID, agent ID, and release ID
            // Create transition from event with real item metadata
            MasteryTransition transition = createTransitionFromEvent(event, item);

            // Route through mastery registry
            return masteryRegistry.transition(transition);
        });
    }

    @Override
    public boolean validateObsolescenceEvent(@NotNull ObsolescenceEvent event) {
        // Check required fields
        if (event.eventId() == null || event.eventId().isBlank()) {
            return false;
        }
        if (event.masteryId() == null || event.masteryId().isBlank()) {
            return false;
        }
        if (event.tenantId() == null || event.tenantId().isBlank()) {
            return false;
        }
        if (event.reason() == null) {
            return false;
        }
        if (event.description() == null || event.description().isBlank()) {
            return false;
        }
        if (event.detectedAt() == null) {
            return false;
        }
        if (event.severity() == null) {
            return false;
        }
        if (event.recommendedTransition() == null) {
            return false;
        }

        // Validate recommended transition is a valid state
        try {
            MasteryState.valueOf(event.recommendedTransition().name());
        } catch (IllegalArgumentException e) {
            return false;
        }

        return true;
    }

    /**
     * P0 FIX: Updated to accept MasteryItem for accurate transition creation.
     * Uses actual item state, skill ID, agent ID, and release ID instead of hardcoded values.
     */
    @NotNull
    public MasteryTransition createTransitionFromEvent(@NotNull ObsolescenceEvent event, @NotNull MasteryItem item) {
        // Build policy-compatible evidence map from event metadata and reason.
        Map<String, String> evidenceMap = new HashMap<>(event.metadata());
        evidenceMap.putAll(ObsolescenceEvidenceMapper.toTransitionEvidence(event));
        evidenceMap.put("obsolescenceReason", event.reason().name());
        evidenceMap.put("severity", event.severity().name());
        evidenceMap.put("eventId", event.eventId());
        evidenceMap.put("originalEventId", event.eventId()); // Track original obsolescence event

        // Add evidence refs to evidence map
        for (int i = 0; i < event.evidenceRefs().size(); i++) {
            evidenceMap.put("evidence_" + i, event.evidenceRefs().get(i).toString());
        }

        // Use actual item state and route to a policy-valid target transition.
        MasteryState fromState = item.state();
        MasteryState routedTarget = routeTransitionTarget(fromState, event.recommendedTransition(), event.reason());

        // P0 FIX: Use actual item metadata instead of fake values
        String agentId = item.agentId();
        String agentReleaseId = item.agentReleaseId();
        String skillId = item.skillId();

        return new MasteryTransition(
                UUID.randomUUID().toString(),
                event.tenantId(),
                item.masteryId(),
                agentId,
                agentReleaseId,
                skillId,
                fromState, // P0 FIX: Use actual current state
                routedTarget,
                "Obsolescence detected: " + event.description(),
                "obsolescence-transition-service",
                Instant.now(),
                Map.copyOf(evidenceMap),
                Map.of(
                        "eventId", event.eventId(),
                        "reason", event.reason().name(),
                        "severity", event.severity().name(),
                        "originalState", fromState.name(),
                        "recommendedState", event.recommendedTransition().name(),
                        "routedState", routedTarget.name()
                )
        );
    }

    @NotNull
    private static MasteryState routeTransitionTarget(
            @NotNull MasteryState fromState,
            @NotNull MasteryState recommended,
            @NotNull ObsolescenceReason reason) {
        if (reason == ObsolescenceReason.SECURITY_VULNERABILITY) {
            return MasteryState.QUARANTINED;
        }
        if (fromState == MasteryState.MASTERED && recommended == MasteryState.OBSOLETE) {
            return MasteryState.MAINTENANCE_ONLY;
        }
        if (fromState == MasteryState.OBSOLETE && recommended == MasteryState.RETIRED) {
            return MasteryState.RETIRED;
        }
        if (fromState == MasteryState.MAINTENANCE_ONLY && recommended == MasteryState.OBSOLETE) {
            return MasteryState.OBSOLETE;
        }
        return recommended;
    }

    /**
     * @deprecated Use {@link #createTransitionFromEvent(ObsolescenceEvent, MasteryItem)} instead.
     * This version cannot accurately determine the current state or metadata.
     */
    @Override
    @NotNull
    @Deprecated
    public MasteryTransition createTransitionFromEvent(@NotNull ObsolescenceEvent event) {
        // This method is kept for interface compatibility but should not be used
        // It will create an invalid transition that will be rejected by the registry
        return new MasteryTransition(
                UUID.randomUUID().toString(),
                event.tenantId(),
                event.masteryId(),
                "unknown",
                "unknown",
                "unknown",
                MasteryState.UNKNOWN,
                event.recommendedTransition(),
                "DEPRECATED: Use createTransitionFromEvent(ObsolescenceEvent, MasteryItem)",
                "obsolescence-transition-service",
                Instant.now(),
                Map.of("deprecated", "true"),
                Map.of("deprecated", "true")
        );
    }
}
