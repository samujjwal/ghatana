package com.ghatana.appplatform.eventstore.saga;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single step within a saga: forward action + optional compensation.
 *
 * <p>Steps are ordered by {@code stepOrder}. Compensation runs in reverse order.
 *
 * @doc.type record
 * @doc.purpose Saga step descriptor with action and compensation logic (STORY-K05-016)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SagaStep(
    String stepName,
    int stepOrder,
    String actionEventType,       // event type to publish to advance this step
    String completionEventType,   // event type we listen for to mark step complete
    String compensationEventType, // event type to publish on rollback (null = no compensation)
    int maxRetries,
    boolean mandatory             // if false, failure is skipped rather than triggering rollback
) {
    public SagaStep {
        Objects.requireNonNull(stepName, "stepName");
        Objects.requireNonNull(actionEventType, "actionEventType");
        Objects.requireNonNull(completionEventType, "completionEventType");
        if (stepOrder < 0) throw new IllegalArgumentException("stepOrder must be >= 0");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
    }

    public boolean hasCompensation() {
        return compensationEventType != null;
    }

    public static SagaStep of(String name, int order, String action, String completion) {
        return new SagaStep(name, order, action, completion, null, 3, true);
    }

    public static SagaStep withCompensation(String name, int order,
                                            String action, String completion,
                                            String compensation) {
        return new SagaStep(name, order, action, completion, compensation, 3, true);
    }
}
