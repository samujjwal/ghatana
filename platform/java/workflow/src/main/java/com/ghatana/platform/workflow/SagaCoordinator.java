/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static com.ghatana.platform.workflow.SagaStep.SagaStepResult;

/**
 * Coordinator for managing Saga-based distributed transactions.
 * Orchestrates multiple service operations with compensation support.
 *
 * @doc.type class
 * @doc.purpose Saga coordinator for cross-service transaction coordination
 * @doc.layer core
 * @doc.pattern Coordinator
 */
public final class SagaCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SagaCoordinator.class);

    private final Map<String, SagaExecution> activeExecutions = new ConcurrentHashMap<>();
    private final Map<String, SagaExecution> completedExecutions = new ConcurrentHashMap<>();
    private final int maxCompletedExecutions = 10000;

    /**
     * Executes a saga with the given steps and compensation policy.
     *
     * @param sagaId unique saga identifier
     * @param steps list of saga steps to execute
     * @param policy compensation policy
     * @return promise resolving to the saga result
     */
    public Promise<SagaResult> execute(String sagaId, List<SagaStep> steps, SagaPolicy policy) {
        SagaExecution execution = new SagaExecution(
            sagaId != null ? sagaId : UUID.randomUUID().toString(),
            steps,
            policy,
            Instant.now()
        );

        activeExecutions.put(execution.id(), execution);

        log.info("[saga] Starting saga execution: id={}, steps={}, policy={}",
            execution.id(), steps.size(), policy);

        return executeSteps(execution, 0)
            .then(result -> {
                activeExecutions.remove(execution.id());
                completedExecutions.put(execution.id(), execution);
                
                // Prune old completed executions
                if (completedExecutions.size() > maxCompletedExecutions) {
                    completedExecutions.keySet().stream()
                        .findFirst()
                        .ifPresent(completedExecutions::remove);
                }

                log.info("[saga] Completed saga execution: id={}, status={}", execution.id(), result.status());
                return Promise.of(result);
            })
            .whenException(error -> {
                activeExecutions.remove(execution.id());
                completedExecutions.put(execution.id(), execution);
                log.error("[saga] Saga execution failed: id={}, error={}", execution.id(), error.getMessage(), error);
            });
    }

    /**
     * Executes saga steps sequentially with compensation on failure.
     */
    private Promise<SagaResult> executeSteps(SagaExecution execution, int stepIndex) {
        if (stepIndex >= execution.steps().size()) {
            return Promise.of(new SagaResult(execution.id(), SagaStatus.COMPLETED,
                "All steps completed successfully", execution.completedSteps()));
        }

        SagaStep step = execution.steps().get(stepIndex);
        log.debug("[saga] Executing step {}/{}: {}", stepIndex + 1, execution.steps().size(), step.name());

        return step.execute()
            .then(stepResult -> {
                execution.addCompletedStep(stepResult);
                
                if (stepResult.success()) {
                    return executeSteps(execution, stepIndex + 1);
                } else {
                    log.warn("[saga] Step failed: {}, error={}", step.name(), stepResult.error());
                    
                    if (execution.policy() == SagaPolicy.BACKWARD_COMPENSATION) {
                        return compensateSteps(execution, stepIndex);
                    } else if (execution.policy() == SagaPolicy.FORWARD_RECOVERY) {
                        // Continue with remaining steps despite failure
                        return executeSteps(execution, stepIndex + 1);
                    } else {
                        // NONE - fail immediately
                        return Promise.of(new SagaResult(execution.id(), SagaStatus.FAILED,
                            "Step failed with NONE policy: " + step.name(), execution.completedSteps()));
                    }
                }
            });
    }

    /**
     * Compensates completed steps in reverse order.
     */
    private Promise<SagaResult> compensateSteps(SagaExecution execution, int stepIndex) {
        if (stepIndex < 0) {
            return Promise.of(new SagaResult(execution.id(), SagaStatus.COMPENSATED,
                "Saga compensated successfully", execution.completedSteps()));
        }

        SagaStep step = execution.steps().get(stepIndex);
        log.debug("[saga] Compensating step: {}", step.name());

        return step.compensate()
            .then(compensationResult -> {
                execution.addCompensatedStep(stepIndex);
                
                if (compensationResult.success()) {
                    return compensateSteps(execution, stepIndex - 1);
                } else {
                    log.error("[saga] Compensation failed for step: {}, error={}",
                        step.name(), compensationResult.error());
                    return Promise.of(new SagaResult(execution.id(), SagaStatus.PARTIALLY_COMPENSATED,
                        "Partial compensation: failed at " + step.name(), execution.completedSteps()));
                }
            })
            .whenException(error -> {
                log.error("[saga] Step execution exception: {}, error={}", step.name(), error.getMessage());
            });
    }

    /**
     * Gets an active saga execution by ID.
     *
     * @param sagaId saga identifier
     * @return the execution, or empty if not found
     */
    public java.util.Optional<SagaExecution> getActiveExecution(String sagaId) {
        return java.util.Optional.ofNullable(activeExecutions.get(sagaId));
    }

    /**
     * Gets a completed saga execution by ID.
     *
     * @param sagaId saga identifier
     * @return the execution, or empty if not found
     */
    public java.util.Optional<SagaExecution> getCompletedExecution(String sagaId) {
        return java.util.Optional.ofNullable(completedExecutions.get(sagaId));
    }

    /**
     * Gets all active saga executions.
     *
     * @return list of active executions
     */
    public List<SagaExecution> getActiveExecutions() {
        return new ArrayList<>(activeExecutions.values());
    }

    /**
     * Gets all completed saga executions.
     *
     * @return list of completed executions
     */
    public List<SagaExecution> getCompletedExecutions() {
        return new ArrayList<>(completedExecutions.values());
    }

    /**
     * Clears completed executions older than the given timestamp.
     *
     * @param beforeTimestamp cutoff timestamp
     */
    public void clearCompletedBefore(Instant beforeTimestamp) {
        completedExecutions.entrySet().removeIf(entry -> 
            entry.getValue().startedAt().isBefore(beforeTimestamp));
    }

    /**
     * Clears all completed executions.
     */
    public void clearAllCompleted() {
        completedExecutions.clear();
    }

    /**
     * Saga execution state.
     *
     * @param id saga identifier
     * @param steps list of steps
     * @param policy compensation policy
     * @param startedAt when the saga started
     */
    public record SagaExecution(
        String id,
        List<SagaStep> steps,
        SagaPolicy policy,
        Instant startedAt,
        List<SagaStepResult> completedSteps,
        java.util.Set<Integer> compensatedSteps
    ) {
        public SagaExecution {
            completedSteps = new ArrayList<>(completedSteps);
            compensatedSteps = new java.util.concurrent.ConcurrentSkipListSet<>(compensatedSteps);
        }

        public SagaExecution(String id, List<SagaStep> steps, SagaPolicy policy, Instant startedAt) {
            this(id, steps, policy, startedAt, new ArrayList<>(), new java.util.concurrent.ConcurrentSkipListSet<>());
        }

        public void addCompletedStep(SagaStepResult result) {
            completedSteps.add(result);
        }

        public void addCompensatedStep(int stepIndex) {
            compensatedSteps.add(stepIndex);
        }

        public List<SagaStepResult> completedSteps() {
            return List.copyOf(completedSteps);
        }

        public java.util.Set<Integer> compensatedSteps() {
            return java.util.Set.copyOf(compensatedSteps);
        }
    }

    /**
     * Saga result.
     *
     * @param sagaId saga identifier
     * @param status final status
     * @param message result message
     * @param stepResults results of completed steps
     */
    public record SagaResult(
        String sagaId,
        SagaStatus status,
        String message,
        List<SagaStepResult> stepResults
    ) {}

    /**
     * Saga status enumeration.
     */
    public enum SagaStatus {
        COMPLETED,
        FAILED,
        COMPENSATED,
        PARTIALLY_COMPENSATED
    }
}
