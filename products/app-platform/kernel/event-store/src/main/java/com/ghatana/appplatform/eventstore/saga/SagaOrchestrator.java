package com.ghatana.appplatform.eventstore.saga;

import com.ghatana.appplatform.eventstore.domain.AggregateEventRecord;
import com.ghatana.appplatform.eventstore.port.AggregateEventStore;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Orchestrates saga lifecycle: starts sagas, advances steps on completion events,
 * triggers compensation on failure, and marks terminal states.
 *
 * <p>The orchestrator is event-driven: call {@link #onStepCompleted} when a
 * completion event arrives, and {@link #onStepFailed} when a step fails.
 *
 * @doc.type class
 * @doc.purpose Saga state machine orchestrator (STORY-K05-017)
 * @doc.layer product
 * @doc.pattern Service
 */
public class SagaOrchestrator {

    private static final Logger LOG = Logger.getLogger(SagaOrchestrator.class.getName());

    private final SagaStore sagaStore;
    private final AggregateEventStore eventStore;

    public SagaOrchestrator(SagaStore sagaStore, AggregateEventStore eventStore) {
        this.sagaStore = sagaStore;
        this.eventStore = eventStore;
    }

    /**
     * Start a new saga instance for the given definition and correlation context.
     *
     * @return the newly created SagaInstance (in STEP_PENDING state, step 0)
     */
    public SagaInstance startSaga(String sagaType, String tenantId, String correlationId) {
        SagaDefinition definition = sagaStore.getLatestDefinition(sagaType)
            .orElseThrow(() -> new IllegalArgumentException("No definition found for sagaType=" + sagaType));

        SagaInstance instance = new SagaInstance(
            UUID.randomUUID().toString(),
            sagaType,
            definition.version(),
            tenantId,
            correlationId,
            SagaState.STEP_PENDING,
            0,
            0,
            null,
            Instant.now(),
            Instant.now()
        );
        sagaStore.saveInstance(instance);

        dispatchStepAction(instance, definition);
        LOG.info("[SagaOrchestrator] Started saga=" + instance.sagaId() + " type=" + sagaType);
        return instance;
    }

    /**
     * Called when a step's completion event is received. Advances to next step or
     * marks the saga COMPLETED if all steps are done.
     */
    public void onStepCompleted(String sagaId) {
        SagaInstance current = sagaStore.findInstance(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
        if (current.isTerminal()) return;

        SagaDefinition definition = sagaStore.getDefinition(current.sagaType(), current.sagaVersion())
            .orElseThrow();

        boolean lastStep = current.currentStepOrder() >= definition.totalSteps() - 1;
        if (lastStep) {
            SagaInstance completed = current.withState(SagaState.COMPLETED);
            sagaStore.updateInstance(completed);
            LOG.info("[SagaOrchestrator] Saga completed sagaId=" + sagaId);
        } else {
            SagaInstance advanced = current.advanceStep();
            sagaStore.updateInstance(advanced);
            dispatchStepAction(advanced, definition);
        }
    }

    /**
     * Called when a step fails. Increments retry counter or triggers compensation.
     */
    public void onStepFailed(String sagaId, String errorMessage) {
        SagaInstance current = sagaStore.findInstance(sagaId)
            .orElseThrow(() -> new IllegalArgumentException("Saga not found: " + sagaId));
        if (current.isTerminal()) return;

        SagaDefinition definition = sagaStore.getDefinition(current.sagaType(), current.sagaVersion())
            .orElseThrow();
        SagaStep currentStep = definition.stepAt(current.currentStepOrder());

        if (current.retryCount() < currentStep.maxRetries()) {
            SagaInstance retried = current.incrementRetry(errorMessage);
            sagaStore.updateInstance(retried);
            dispatchStepAction(retried, definition);
        } else {
            LOG.warning("[SagaOrchestrator] Step exhausted retries, triggering compensation. sagaId=" + sagaId);
            SagaInstance compensating = current.withState(SagaState.COMPENSATING);
            sagaStore.updateInstance(compensating);
            runCompensation(compensating, definition);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void dispatchStepAction(SagaInstance instance, SagaDefinition definition) {
        SagaStep step = definition.stepAt(instance.currentStepOrder());
        Map<String, Object> meta = SagaCorrelationPropagator.buildMetadata(
            instance, instance.currentStepOrder());
        eventStore.appendEvent(
            UUID.fromString(instance.correlationId()),
            "saga:" + instance.sagaType(),
            step.actionEventType(),
            Map.of("sagaId", instance.sagaId(), "step", step.stepName()),
            meta
        );
    }

    private void runCompensation(SagaInstance compensating, SagaDefinition definition) {
        // Execute compensation steps in reverse order for steps already completed
        for (int i = compensating.currentStepOrder() - 1; i >= 0; i--) {
            SagaStep step = definition.stepAt(i);
            if (step.hasCompensation()) {
                Map<String, Object> meta = SagaCorrelationPropagator.buildMetadata(compensating, i);
                eventStore.appendEvent(
                    UUID.fromString(compensating.correlationId()),
                    "saga:" + compensating.sagaType(),
                    step.compensationEventType(),
                    Map.of("sagaId", compensating.sagaId(), "step", step.stepName()),
                    meta
                );
            }
        }
        SagaInstance compensated = compensating.withState(SagaState.COMPENSATED);
        sagaStore.updateInstance(compensated);
        LOG.info("[SagaOrchestrator] Compensation completed sagaId=" + compensating.sagaId());
    }
}
