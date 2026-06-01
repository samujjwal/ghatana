package com.ghatana.aep.pattern.lifecycle;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose Validates PatternSpec lifecycle transitions and governance-sensitive promotion rules
 * @doc.layer product
 * @doc.pattern Policy
 */
public final class PatternLifecyclePolicy {

    private static final Map<PatternLifecycleState, Set<PatternLifecycleState>> ALLOWED_TRANSITIONS =
        new EnumMap<>(PatternLifecycleState.class);

    static {
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.DRAFT, EnumSet.of(PatternLifecycleState.CANDIDATE, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.CANDIDATE, EnumSet.of(PatternLifecycleState.VALIDATED, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.VALIDATED, EnumSet.of(PatternLifecycleState.SHADOW, PatternLifecycleState.RECOMMENDED, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.SHADOW, EnumSet.of(PatternLifecycleState.RECOMMENDED, PatternLifecycleState.DEGRADED, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.RECOMMENDED, EnumSet.of(PatternLifecycleState.APPROVED, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.APPROVED, EnumSet.of(PatternLifecycleState.ACTIVE, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.ACTIVE, EnumSet.of(PatternLifecycleState.DEGRADED, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.DEGRADED, EnumSet.of(PatternLifecycleState.ACTIVE, PatternLifecycleState.RETIRED));
        ALLOWED_TRANSITIONS.put(PatternLifecycleState.RETIRED, EnumSet.noneOf(PatternLifecycleState.class));
    }

    private PatternLifecyclePolicy() {}

    public static boolean isAllowed(PatternLifecycleState from, PatternLifecycleState to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(PatternLifecycleTransition transition) {
        if (!isAllowed(transition.from(), transition.to())) {
            throw new IllegalArgumentException(
                "Invalid pattern lifecycle transition: " + transition.from() + " -> " + transition.to());
        }
        if (transition.from() == PatternLifecycleState.RECOMMENDED && transition.to() == PatternLifecycleState.ACTIVE) {
            throw new IllegalArgumentException("Recommended patterns must be approved before activation");
        }
    }

    public static void requireMatchingEventType(PatternLifecycleTransition transition) {
        PatternLifecycleEventType expected = expectedEventType(transition.to());
        if (transition.eventType() != expected) {
            throw new IllegalArgumentException(
                "Pattern lifecycle transition to " + transition.to() + " must emit " + expected.eventType());
        }
    }

    private static PatternLifecycleEventType expectedEventType(PatternLifecycleState to) {
        return switch (to) {
            case CANDIDATE -> PatternLifecycleEventType.PATTERN_CREATED;
            case VALIDATED -> PatternLifecycleEventType.PATTERN_VALIDATED;
            case SHADOW -> PatternLifecycleEventType.PATTERN_SHADOW_DEPLOYED;
            case RECOMMENDED -> PatternLifecycleEventType.PATTERN_RECOMMENDED;
            case APPROVED -> PatternLifecycleEventType.PATTERN_APPROVED;
            case ACTIVE -> PatternLifecycleEventType.PATTERN_PROMOTED;
            case DEGRADED -> PatternLifecycleEventType.PATTERN_DEGRADED;
            case RETIRED -> PatternLifecycleEventType.PATTERN_RETIRED;
            case DRAFT -> PatternLifecycleEventType.PATTERN_CREATED;
            default -> throw new IllegalArgumentException("Unsupported pattern lifecycle state: " + to);
        };
    }
}
