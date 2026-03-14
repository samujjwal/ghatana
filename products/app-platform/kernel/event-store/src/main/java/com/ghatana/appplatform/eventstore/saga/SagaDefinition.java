package com.ghatana.appplatform.eventstore.saga;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable blueprint for a saga: ordered steps with compensation.
 * Stored in {@code saga_definitions} (V007 migration) and versioned.
 *
 * @doc.type record
 * @doc.purpose Versioned saga definition blueprint (STORY-K05-016)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SagaDefinition(
    String sagaType,      // unique type identifier, e.g. "payment-fulfilment-saga"
    int version,
    String description,
    List<SagaStep> steps  // ordered; must not be empty
) {
    public SagaDefinition {
        Objects.requireNonNull(sagaType, "sagaType");
        Objects.requireNonNull(steps, "steps");
        if (steps.isEmpty()) throw new IllegalArgumentException("Saga must have at least one step");
        steps = Collections.unmodifiableList(steps);
    }

    /**
     * Return the saga step for the given step order, or throws if absent.
     */
    public SagaStep stepAt(int order) {
        return steps.stream()
            .filter(s -> s.stepOrder() == order)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No step at order=" + order));
    }

    public int totalSteps() {
        return steps.size();
    }
}
