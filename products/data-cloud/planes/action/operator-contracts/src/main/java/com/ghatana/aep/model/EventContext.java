package com.ghatana.aep.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Provides the standard typed input context consumed by AEP EventOperators
 * @doc.layer product
 * @doc.pattern Contract
 */
public record EventContext<T>(
        String tenantId,
        List<CanonicalEvent> events,
        Optional<PatternPartialMatch> partialMatch,
        Optional<PatternMatch> match,
        Map<String, Object> bindings,
        EventTimeContext time,
        UncertaintyContext uncertainty,
        ReplayContext replay,
        Optional<T> input) {

    public EventContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        events = List.copyOf(events != null ? events : List.of());
        partialMatch = partialMatch != null ? partialMatch : Optional.empty();
        match = match != null ? match : Optional.empty();
        bindings = Map.copyOf(bindings != null ? bindings : Map.of());
        time = Objects.requireNonNull(time, "time must not be null");
        uncertainty = Objects.requireNonNull(uncertainty, "uncertainty must not be null");
        replay = Objects.requireNonNull(replay, "replay must not be null");
        input = input != null ? input : Optional.empty();
    }
}
