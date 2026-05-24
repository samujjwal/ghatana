package com.ghatana.aep.pattern.lifecycle;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits governed PatternSpec lifecycle events after validating transitions.
 *
 * @doc.type class
 * @doc.purpose Validates pattern lifecycle transitions and creates auditable lifecycle events
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PatternLifecycleService {

    private final Clock clock;

    public PatternLifecycleService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PatternLifecycleEvent transition(PatternLifecycleTransition transition) {
        PatternLifecyclePolicy.requireAllowed(transition);
        PatternLifecyclePolicy.requireMatchingEventType(transition);
        return new PatternLifecycleEvent(
            UUID.randomUUID().toString(),
            transition.patternId(),
            transition.tenantId(),
            transition.from(),
            transition.to(),
            transition.eventType(),
            transition.actor(),
            clock.instant(),
            transition.evidence());
    }
}
