/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransition;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routes obsolescence events to appropriate mastery state transitions.
 *
 * <p>When an obsolescence event is detected, this router determines the appropriate
 * target state and initiates a mastery state transition via the MasteryRegistry.
 *
 * @doc.type class
 * @doc.purpose Routes obsolescence events to mastery state transitions
 * @doc.layer agent-core
 * @doc.pattern Router
 */
public final class ObsolescenceRouter {

    private final MasteryRegistry masteryRegistry;

    /**
     * Creates an obsolescence router.
     *
     * @param masteryRegistry mastery registry for state transitions
     */
    public ObsolescenceRouter(@NotNull MasteryRegistry masteryRegistry) {
        this.masteryRegistry = Objects.requireNonNull(masteryRegistry, "masteryRegistry must not be null");
    }

    /**
     * Routes a single obsolescence event to the appropriate state transition.
     *
     * @param event obsolescence event to route
     * @return promise of transition result
     */
    @NotNull
    public Promise<com.ghatana.agent.mastery.MasteryTransitionResult> route(@NotNull ObsolescenceEvent event) {
        return masteryRegistry.findBySkill(event.masteryId(), null)
                .then(masteryOpt -> {
                    if (masteryOpt.isEmpty()) {
                        // Mastery item not found, cannot transition
                        return Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.failure(
                                event.masteryId(),
                                MasteryState.UNKNOWN,
                                "Mastery item not found: " + event.masteryId()
                        ));
                    }

                    MasteryItem item = masteryOpt.get();
                    MasteryState targetState = determineTargetState(event.reason(), item.state());

                    // Create evidence refs map from event evidence
                    Map<String, String> evidenceRefs = Map.of("eventId", event.eventId());
                    for (ObsolescenceEvidenceRef ref : event.evidenceRefs()) {
                        String key = ref.refType() + ":" + ref.refId();
                        String value = ref.description() != null ? ref.description() : "";
                        Map<String, String> newMap = new java.util.HashMap<>(evidenceRefs);
                        newMap.put(key, value);
                        evidenceRefs = Map.copyOf(newMap);
                    }

                    // Create and execute transition
                    MasteryTransition transition = new MasteryTransition(
                            java.util.UUID.randomUUID().toString(),
                            item.tenantId(),
                            item.masteryId(),
                            item.agentId(),
                            item.agentReleaseId(),
                            item.skillId(),
                            item.state(),
                            targetState,
                            "Obsolescence detected: " + event.reason() + " - " + event.description(),
                            "obsolescence-router",
                            event.detectedAt(),
                            evidenceRefs,
                            Map.of("eventId", event.eventId(), "reason", event.reason().name())
                    );

                    return masteryRegistry.transition(transition);
                });
    }

    /**
     * Routes multiple obsolescence events to state transitions.
     *
     * @param events obsolescence events to route
     * @return promise of list of transition results
     */
    @NotNull
    public Promise<List<com.ghatana.agent.mastery.MasteryTransitionResult>> routeAll(@NotNull List<ObsolescenceEvent> events) {
        Promise<List<com.ghatana.agent.mastery.MasteryTransitionResult>> result = Promise.of(new java.util.ArrayList<>());
        for (ObsolescenceEvent event : events) {
            result = result.then(results ->
                route(event).map(transitionResult -> {
                    results.add(transitionResult);
                    return results;
                })
            );
        }
        return result;
    }

    /**
     * Determines the target mastery state based on obsolescence reason.
     *
     * @param reason obsolescence reason
     * @param currentState current mastery state
     * @return target mastery state
     */
    @NotNull
    private MasteryState determineTargetState(@NotNull ObsolescenceReason reason, @NotNull MasteryState currentState) {
        return switch (reason) {
            case VERSION_MISMATCH -> MasteryState.OBSOLETE;
            case API_CHANGE -> MasteryState.OBSOLETE;
            case RUNTIME_INCOMPATIBILITY -> MasteryState.RETIRED;
            case REPEATED_FAILURES -> MasteryState.QUARANTINED;
            case SECURITY_VULNERABILITY -> MasteryState.RETIRED;
            case DOCUMENTATION_CONTRADICTION -> MasteryState.QUARANTINED;
            case SUPERSEDED_BY_ALTERNATIVE -> MasteryState.OBSOLETE;
            case DEPRECATED_PATTERN -> MasteryState.OBSOLETE;
            case DEPRECATED_DEPENDENCY -> MasteryState.RETIRED;
        };
    }
}
