/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor;

import com.ghatana.aep.domain.pipeline.AgentStep;
import com.ghatana.orchestrator.executor.model.AgentStepResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.promise.SettablePromise;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * AgentStepRunner - executes agent steps with timeout, retry, and event emission.
 *
 * <p><b>Purpose</b><br>
 * Provides non-blocking execution of agent steps with configurable timeout,
 * retry policies with exponential backoff, and event emission for observability.
 *
 * <p><b>Architecture Role</b><br>
 * Core executor in the orchestrator layer. Uses ActiveJ Promise for all async
 * operations to maintain compatibility with the platform's event-driven model.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Uses AtomicBoolean for state management and Promise composition
 * for async coordination.
 *
 * @doc.type class
 * @doc.purpose Agent step execution with timeout, retry, and event emission
 * @doc.layer product
 * @doc.pattern Executor, Retry
 */
@Slf4j
public class AgentStepRunner {

    private final MetricsCollector metrics;
    private final AgentEventEmitter eventEmitter;
    private final String tenantId;
    private final AgentExecutionPolicy policy;
    private final Executor blockingExecutor;
    private volatile boolean shutdown = false;

    public AgentStepRunner(MetricsCollector metrics, 
                          AgentEventEmitter eventEmitter, 
                          String tenantId) {
        this(metrics, eventEmitter, tenantId, AgentExecutionPolicy.defaultPolicy());
    }

    public AgentStepRunner(MetricsCollector metrics, 
                          AgentEventEmitter eventEmitter, 
                          String tenantId,
                          AgentExecutionPolicy policy) {
        this.metrics = metrics;
        this.eventEmitter = eventEmitter;
        this.tenantId = tenantId;
        this.policy = policy;
        // Use virtual threads for blocking operations (Java 21+)
        this.blockingExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * Execute an agent step with timeout and retry logic.
     *
     * @param step the agent step to execute
     * @return Promise completing with the step result
     */
    public Promise<AgentStepResult> execute(AgentStep step) {
        return executeStep(step, s -> null);
    }

    /**
     * Execute an agent step with a custom step function.
     *
     * <p>Uses ActiveJ Promise for non-blocking execution with:
     * <ul>
     *   <li>Configurable timeout via policy</li>
     *   <li>Exponential backoff retry with max attempts</li>
     *   <li>Event emission for each attempt</li>
     * </ul>
     *
     * @param step the agent step to execute
     * @param stepFunction the function to execute the step
     * @return Promise completing with the step result
     */
    public Promise<AgentStepResult> executeStep(AgentStep step, Function<AgentStep, Object> stepFunction) {
        if (step == null) {
            return Promise.ofException(new NullPointerException("step cannot be null"));
        }
        if (stepFunction == null) {
            return Promise.ofException(new NullPointerException("stepFunction cannot be null"));
        }
        if (shutdown) {
            return Promise.ofException(new IllegalStateException("AgentStepRunner is shutdown"));
        }
        
        Instant startTime = Instant.now();
        int maxRetries = policy.getMaxRetries();
        long timeoutMs = policy.getStepTimeout().toMillis();

        return executeWithTimeoutAndRetry(step, stepFunction, startTime, 0, maxRetries, timeoutMs);
    }

    private Promise<AgentStepResult> executeWithTimeoutAndRetry(
            AgentStep step,
            Function<AgentStep, Object> stepFunction,
            Instant startTime,
            int attemptNumber,
            int maxRetries,
            long timeoutMs) {

        final int currentAttempt = attemptNumber + 1;
        AtomicBoolean completed = new AtomicBoolean(false);

        // Execute the step function in a blocking executor wrapped with Promise
        // Note: Exceptions are automatically propagated as Promise failures by ofBlocking
        Promise<Object> stepPromise = Promise.ofBlocking(blockingExecutor, () -> stepFunction.apply(step));

        // Apply timeout using ActiveJ Promises utility
        Promise<Object> timedPromise = Promises.timeout(Duration.ofMillis(timeoutMs), stepPromise);

        return timedPromise
                .map(result -> {
                    if (completed.compareAndSet(false, true)) {
                        Instant endTime = Instant.now();
                        AgentStepResult stepResult = AgentStepResult.builder()
                                .stepId(step.getId())
                                .agentId(step.getAgentId())
                                .status(AgentStepResult.ExecutionStatus.SUCCESS)
                                .result(result)
                                .startTime(startTime)
                                .endTime(endTime)
                                .attemptNumber(currentAttempt)
                                .totalAttempts(currentAttempt)
                                .metrics(createMetrics(startTime, endTime))
                                .build();

                        emitResultEvent(stepResult);
                        return stepResult;
                    }
                    return null;
                })
                .then((result, exception) -> {
                    if (exception == null && result != null) {
                        return Promise.of(result);
                    }
                    
                    if (!completed.compareAndSet(false, true)) {
                        // Already completed, return a placeholder
                        return Promise.of(AgentStepResult.builder()
                                .stepId(step.getId())
                                .agentId(step.getAgentId())
                                .status(AgentStepResult.ExecutionStatus.SUCCESS)
                                .build());
                    }

                    Instant endTime = Instant.now();
                    
                    // Check if this is a timeout
                    if (isTimeoutException(exception)) {
                        AgentStepResult timeoutResult = AgentStepResult.builder()
                                .stepId(step.getId())
                                .agentId(step.getAgentId())
                                .status(AgentStepResult.ExecutionStatus.TIMEOUT)
                                .error(new TimeoutException("Step execution timed out after " + timeoutMs + "ms"))
                                .startTime(startTime)
                                .endTime(endTime)
                                .attemptNumber(currentAttempt)
                                .totalAttempts(currentAttempt)
                                .metrics(createMetrics(startTime, endTime))
                                .build();

                        emitResultEvent(timeoutResult);
                        return Promise.of(timeoutResult);
                    }

                    // Check if we should retry
                    if (currentAttempt <= maxRetries) {
                        AgentStepResult retryResult = AgentStepResult.builder()
                                .stepId(step.getId())
                                .agentId(step.getAgentId())
                                .status(AgentStepResult.ExecutionStatus.RETRY)
                                .error(exception)
                                .startTime(startTime)
                                .endTime(endTime)
                                .attemptNumber(currentAttempt)
                                .totalAttempts(currentAttempt)
                                .metrics(createMetrics(startTime, endTime))
                                .build();

                        emitResultEvent(retryResult);

                        // Calculate backoff delay
                        long delayMs = calculateBackoffDelay(currentAttempt);
                        
                        log.debug("Retrying step {} after {}ms (attempt {}/{})", 
                                step.getId(), delayMs, currentAttempt, maxRetries + 1);

                        // Schedule retry with delay using ActiveJ Promises.delay()
                        return Promises.delay(Duration.ofMillis(delayMs))
                                .then(() -> {
                                    completed.set(false); // Reset for next attempt
                                    return executeWithTimeoutAndRetry(step, stepFunction, startTime, currentAttempt, maxRetries, timeoutMs);
                                });
                    }

                    // Max retries exceeded - return failure
                    AgentStepResult failedResult = AgentStepResult.builder()
                            .stepId(step.getId())
                            .agentId(step.getAgentId())
                            .status(AgentStepResult.ExecutionStatus.FAILED)
                            .error(unwrapException(exception))
                            .startTime(startTime)
                            .endTime(endTime)
                            .attemptNumber(currentAttempt)
                            .totalAttempts(currentAttempt)
                            .metrics(createMetrics(startTime, endTime))
                            .build();

                    emitResultEvent(failedResult);
                    return Promise.of(failedResult);
                });
    }

    private long calculateBackoffDelay(int attemptNumber) {
        long baseDelay = policy.getInitialBackoff().toMillis();
        double multiplier = policy.getBackoffMultiplier();
        long delay = (long) (baseDelay * Math.pow(multiplier, attemptNumber - 1));
        
        // Apply jitter if enabled
        if (policy.isJitterEnabled()) {
            delay = (long) (delay * (0.5 + Math.random()));
        }
        
        return Math.min(delay, policy.getMaxBackoff().toMillis());
    }

    private boolean isTimeoutException(Throwable t) {
        if (t == null) return false;
        if (t instanceof TimeoutException) return true;
        if (t instanceof InterruptedException) return true;
        if (t.getCause() != null) {
            return isTimeoutException(t.getCause());
        }
        String message = t.getMessage();
        return message != null && (message.contains("timeout") || message.contains("interrupted"));
    }

    private Throwable unwrapException(Throwable t) {
        if (t instanceof RuntimeException && t.getCause() != null) {
            return t.getCause();
        }
        return t;
    }

    private void emitResultEvent(AgentStepResult result) {
        try {
            if (policy.isEmitResultEvents() && eventEmitter != null) {
                eventEmitter.emitStepResult(result);
            }
        } catch (Exception e) {
            log.warn("Failed to emit step result event for {}: {}", result.getStepId(), e.getMessage());
        }
    }

    private Map<String, Object> createMetrics(Instant startTime, Instant endTime) {
        Map<String, Object> metricsMap = new HashMap<>();
        metricsMap.put("execution_time_ms", endTime.toEpochMilli() - startTime.toEpochMilli());
        metricsMap.put("policy", policy.toString());
        metricsMap.put("timestamp", endTime.toString());
        metricsMap.put("tenant_id", tenantId);
        return metricsMap;
    }

    /**
     * Shutdown the runner and clean up resources.
     */
    public void shutdown() {
        shutdown = true;
        // Virtual thread executor doesn't need explicit shutdown
    }
}