package com.ghatana.appplatform.eventstore.saga;

import java.time.Instant;
import java.util.Objects;

/**
 * Runtime snapshot of a running or completed saga for a specific tenant and correlation context.
 *
 * @doc.type record
 * @doc.purpose Saga runtime instance (STORY-K05-017)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record SagaInstance(
    String sagaId,
    String sagaType,
    int sagaVersion,
    String tenantId,
    String correlationId,      // e.g. orderId, paymentId
    SagaState state,
    int currentStepOrder,      // 0-indexed step in progress
    int retryCount,
    String lastError,          // nullable; last failure message
    Instant startedAt,
    Instant updatedAt
) {
    public SagaInstance {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(sagaType, "sagaType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(state, "state");
    }

    public SagaInstance withState(SagaState newState) {
        return new SagaInstance(sagaId, sagaType, sagaVersion, tenantId, correlationId,
            newState, currentStepOrder, retryCount, lastError, startedAt, Instant.now());
    }

    public SagaInstance advanceStep() {
        return new SagaInstance(sagaId, sagaType, sagaVersion, tenantId, correlationId,
            SagaState.STEP_PENDING, currentStepOrder + 1, 0, null, startedAt, Instant.now());
    }

    public SagaInstance incrementRetry(String error) {
        return new SagaInstance(sagaId, sagaType, sagaVersion, tenantId, correlationId,
            state, currentStepOrder, retryCount + 1, error, startedAt, Instant.now());
    }

    public boolean isTerminal() {
        return state == SagaState.COMPLETED
            || state == SagaState.COMPENSATED
            || state == SagaState.FAILED;
    }
}
