package com.ghatana.aep.pattern.lifecycle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tracks PatternSpec lifecycle state and auditable transition events.
 *
 * @doc.type class
 * @doc.purpose Maintains governed lifecycle state so promotion decisions are validated against stored state
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class PatternLifecycleRegistry {

    private final PatternLifecycleService lifecycleService;
    private final Map<PatternKey, PatternLifecycleState> states = new HashMap<>();
    private final Map<PatternKey, List<PatternLifecycleEvent>> events = new HashMap<>();

    public PatternLifecycleRegistry(PatternLifecycleService lifecycleService) {
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService");
    }

    public synchronized void initializeDraft(String tenantId, String patternId) {
        PatternKey key = new PatternKey(tenantId, patternId);
        if (states.containsKey(key)) {
            throw new IllegalStateException("Pattern lifecycle already exists: " + patternId);
        }
        states.put(key, PatternLifecycleState.DRAFT);
        events.put(key, new ArrayList<>());
    }

    public synchronized PatternLifecycleEvent transition(PatternLifecycleTransition transition) {
        PatternKey key = new PatternKey(transition.tenantId(), transition.patternId());
        PatternLifecycleState currentState = states.get(key);
        if (currentState == null) {
            throw new IllegalStateException("Pattern lifecycle does not exist: " + transition.patternId());
        }
        if (currentState != transition.from()) {
            throw new IllegalArgumentException(
                "Pattern lifecycle state mismatch for " + transition.patternId()
                    + ": expected " + currentState + " but transition declared " + transition.from());
        }

        PatternLifecycleEvent event = lifecycleService.transition(transition);
        states.put(key, transition.to());
        events.computeIfAbsent(key, ignored -> new ArrayList<>()).add(event);
        return event;
    }

    public synchronized Optional<PatternLifecycleState> currentState(String tenantId, String patternId) {
        return Optional.ofNullable(states.get(new PatternKey(tenantId, patternId)));
    }

    public synchronized List<PatternLifecycleEvent> events(String tenantId, String patternId) {
        return List.copyOf(events.getOrDefault(new PatternKey(tenantId, patternId), List.of()));
    }

    private record PatternKey(String tenantId, String patternId) {
        private PatternKey {
            tenantId = requireText(tenantId, "tenantId");
            patternId = requireText(patternId, "patternId");
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value;
        }
    }
}
