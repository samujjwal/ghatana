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

        // Fetch the mastery item
        return masteryRegistry.query(
                com.ghatana.agent.mastery.MasteryQuery.bySkill(event.masteryId())
        ).then(items -> {
            if (items.isEmpty()) {
                return Promise.of(MasteryTransitionResult.failure(
                        event.masteryId(),
                        MasteryState.UNKNOWN,
                        "Mastery item not found: " + event.masteryId()
                ));
            }

            MasteryItem item = items.get(0);

            // Validate tenant match
            if (!item.tenantId().equals(event.tenantId())) {
                return Promise.of(MasteryTransitionResult.failure(
                        event.masteryId(),
                        item.state(),
                        "Tenant mismatch: item tenant " + item.tenantId() + " != event tenant " + event.tenantId()
                ));
            }

            // Create transition from event
            MasteryTransition transition = createTransitionFromEvent(event);

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

    @Override
    @NotNull
    public MasteryTransition createTransitionFromEvent(@NotNull ObsolescenceEvent event) {
        // Build evidence map from event metadata and evidence refs
        Map<String, String> evidenceMap = new HashMap<>(event.metadata());
        evidenceMap.put("obsolescenceReason", event.reason().name());
        evidenceMap.put("severity", event.severity().name());
        evidenceMap.put("eventId", event.eventId());

        // Add evidence refs to evidence map
        for (int i = 0; i < event.evidenceRefs().size(); i++) {
            evidenceMap.put("evidence_" + i, event.evidenceRefs().get(i).toString());
        }

        // Determine the current state by querying the mastery item
        // For now, we'll use UNKNOWN as the from state since we don't have the item here
        // The actual transition will be validated by the mastery registry

        return new MasteryTransition(
                UUID.randomUUID().toString(),
                event.tenantId(),
                event.masteryId(),
                "obsolescence-detector",
                "obsolescence-detector",
                event.masteryId().split("-")[0], // Extract skillId from masteryId
                MasteryState.MASTERED, // Assume current state, will be validated by registry
                event.recommendedTransition(),
                "Obsolescence detected: " + event.description(),
                "obsolescence-transition-service",
                Instant.now(),
                Map.copyOf(evidenceMap),
                Map.of(
                        "eventId", event.eventId(),
                        "reason", event.reason().name(),
                        "severity", event.severity().name()
                )
        );
    }
}
