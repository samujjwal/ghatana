package com.ghatana.appplatform.eventstore.saga;

/**
 * Lifecycle state of a {@link SagaInstance}.
 *
 * <p>Transitions:
 * <pre>
 *   STARTED → STEP_PENDING → STEP_COMPLETE → ... → COMPLETED
 *                                          → COMPENSATING → COMPENSATED
 *                                          → FAILED (non-recoverable)
 * </pre>
 *
 * @doc.type enum
 * @doc.purpose Saga lifecycle state machine (STORY-K05-016)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum SagaState {
    /** Saga created; first step not yet dispatched. */
    STARTED,
    /** A step is dispatched; waiting for outcome. */
    STEP_PENDING,
    /** A step completed successfully; advancing to next. */
    STEP_COMPLETE,
    /** All steps completed successfully. */
    COMPLETED,
    /** Rollback triggered; compensation steps being executed. */
    COMPENSATING,
    /** All compensation steps completed. */
    COMPENSATED,
    /** Non-recoverable failure; compensation could not complete. */
    FAILED
}
