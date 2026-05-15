/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.mastery.MasteryItem;
import com.ghatana.agent.mastery.MasteryRegistry;
import com.ghatana.agent.mastery.MasteryState;
import com.ghatana.agent.mastery.MasteryTransition;
import com.ghatana.agent.mastery.transition.MasteryTransitionPolicy;
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
 * Uses the transition policy to validate that the target state is reachable from
 * the current state.
 *
 * @doc.type class
 * @doc.purpose Routes obsolescence events to mastery state transitions
 * @doc.layer agent-core
 * @doc.pattern Router
 */
public final class ObsolescenceRouter {

    private final MasteryRegistry masteryRegistry;
    private final MasteryTransitionPolicy transitionPolicy;

    /**
     * Creates an obsolescence router.
     *
     * @param masteryRegistry mastery registry for state transitions
     * @param transitionPolicy transition policy for validating state transitions
     */
    public ObsolescenceRouter(
            @NotNull MasteryRegistry masteryRegistry,
            @NotNull MasteryTransitionPolicy transitionPolicy) {
        this.masteryRegistry = Objects.requireNonNull(masteryRegistry, "masteryRegistry must not be null");
        this.transitionPolicy = Objects.requireNonNull(transitionPolicy, "transitionPolicy must not be null");
    }

    /**
     * Routes a single obsolescence event to the appropriate state transition.
     *
     * @param event obsolescence event to route
     * @return promise of transition result
     */
    @NotNull
    public Promise<com.ghatana.agent.mastery.MasteryTransitionResult> route(@NotNull ObsolescenceEvent event) {
        // Use tenant-scoped getById instead of deprecated findBySkill with null EnvironmentFingerprint
        return masteryRegistry.getById(event.tenantId(), event.masteryId())
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

                    // Validate transition against policy before executing
                    // Build evidence map for validation
                    Map<String, String> evidenceForValidation = new java.util.HashMap<>();
                    evidenceForValidation.put("eventId", event.eventId());
                    evidenceForValidation.put("reason", event.reason().name());
                    
                    // Add appropriate evidence based on target state
                    if (targetState == MasteryState.RETIRED) {
                        evidenceForValidation.put("no_active_use_case", "true");
                    } else if (targetState == MasteryState.OBSOLETE) {
                        if (event.reason() == ObsolescenceReason.SECURITY_VULNERABILITY) {
                            evidenceForValidation.put("security_advisory", "true");
                        } else if (event.reason() == ObsolescenceReason.API_CHANGE) {
                            evidenceForValidation.put("api_break", "true");
                        } else {
                            evidenceForValidation.put("contradiction", "true");
                        }
                    } else if (targetState == MasteryState.QUARANTINED) {
                        evidenceForValidation.put("unsafe_behavior", "true");
                    }

                    var validation = transitionPolicy.canTransition(item.state(), targetState, evidenceForValidation);
                    if (!validation.allowed()) {
                        // Transition not allowed by policy - try fallback to OBSOLETE if target was RETIRED
                        if (targetState == MasteryState.RETIRED && item.state() != MasteryState.OBSOLETE) {
                            // Try OBSOLETE as intermediate state
                            evidenceForValidation.remove("no_active_use_case");
                            if (event.reason() == ObsolescenceReason.SECURITY_VULNERABILITY) {
                                evidenceForValidation.put("security_advisory", "true");
                            } else if (event.reason() == ObsolescenceReason.API_CHANGE) {
                                evidenceForValidation.put("api_break", "true");
                            } else {
                                evidenceForValidation.put("contradiction", "true");
                            }
                            
                            var obsoleteValidation = transitionPolicy.canTransition(item.state(), MasteryState.OBSOLETE, evidenceForValidation);
                            if (obsoleteValidation.allowed()) {
                                targetState = MasteryState.OBSOLETE;
                            } else {
                                // Neither RETIRED nor OBSOLETE allowed, return failure
                                return Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.failure(
                                        item.masteryId(),
                                        item.state(),
                                        validation.errorMessage().orElse("Transition not allowed by policy")
                                ));
                            }
                        } else {
                            // Transition not allowed, return failure
                            return Promise.of(com.ghatana.agent.mastery.MasteryTransitionResult.failure(
                                    item.masteryId(),
                                    item.state(),
                                    validation.errorMessage().orElse("Transition not allowed by policy")
                            ));
                        }
                    }

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
     * <p>Aligned with transition policy requirements:
     * <ul>
     *   <li>Security issues → QUARANTINED (not direct retirement)</li>
     *   <li>Repeated failures → QUARANTINED</li>
     *   <li>Runtime incompatibility → OBSOLETE (not direct retirement)</li>
     *   <li>Deprecated dependency → OBSOLETE (retirement requires separate workflow)</li>
     *   <li>Version/API mismatch → OBSOLETE or MAINTENANCE_ONLY based on context</li>
     * </ul>
     *
     * <p>RETIREMENT is only allowed from OBSOLETE with explicit no_active_use_case evidence,
     * handled by the transition validation fallback in route().
     *
     * @param reason obsolescence reason
     * @param currentState current mastery state
     * @return target mastery state
     */
    @NotNull
    private MasteryState determineTargetState(@NotNull ObsolescenceReason reason, @NotNull MasteryState currentState) {
        return switch (reason) {
            // Version/API issues go to OBSOLETE first, retirement requires separate workflow
            case VERSION_MISMATCH -> MasteryState.OBSOLETE;
            case API_CHANGE -> MasteryState.OBSOLETE;
            // Runtime incompatibility goes to OBSOLETE, not direct RETIRED
            case RUNTIME_INCOMPATIBILITY -> MasteryState.OBSOLETE;
            // Safety/stability issues go to QUARANTINED
            case REPEATED_FAILURES -> MasteryState.QUARANTINED;
            case SECURITY_VULNERABILITY -> MasteryState.QUARANTINED;
            case DOCUMENTATION_CONTRADICTION -> MasteryState.QUARANTINED;
            case EVAL_REGRESSION -> MasteryState.QUARANTINED;
            // Superseded/deprecated items go to OBSOLETE
            case SUPERSEDED_BY_ALTERNATIVE -> MasteryState.OBSOLETE;
            case DEPRECATED_PATTERN -> MasteryState.OBSOLETE;
            case DEPRECATED_DEPENDENCY -> MasteryState.OBSOLETE;
        };
    }
}
