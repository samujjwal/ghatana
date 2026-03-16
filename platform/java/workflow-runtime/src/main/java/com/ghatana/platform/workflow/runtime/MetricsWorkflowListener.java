/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.runtime;

import com.ghatana.platform.workflow.WorkflowLifecycleEvent;
import com.ghatana.platform.workflow.WorkflowLifecycleListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle listener that publishes workflow metrics to Micrometer.
 *
 * <p>Tracks:
 * <ul>
 *   <li>{@code workflow.runs.total} — counter by workflow ID and terminal status</li>
 *   <li>{@code workflow.runs.active} — gauge of in-flight runs</li>
 *   <li>{@code workflow.step.duration} — timer per step (start → complete/fail)</li>
 *   <li>{@code workflow.step.failures} — counter of step failures</li>
 *   <li>{@code workflow.step.retries} — counter of retry attempts</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Publishes workflow lifecycle metrics to Micrometer
 * @doc.layer platform
 * @doc.pattern Observer
 */
public final class MetricsWorkflowListener implements WorkflowLifecycleListener {

    private final MeterRegistry registry;
    private final Map<String, Instant> stepStartTimes = new ConcurrentHashMap<>();
    private final Counter runsStarted;
    private final Counter runsCompleted;
    private final Counter runsFailed;
    private final Counter runsCompensated;
    private final Counter stepRetries;

    public MetricsWorkflowListener(@NotNull MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.runsStarted = Counter.builder("workflow.runs.started")
            .description("Total workflow runs started")
            .register(registry);
        this.runsCompleted = Counter.builder("workflow.runs.completed")
            .description("Total workflow runs completed successfully")
            .register(registry);
        this.runsFailed = Counter.builder("workflow.runs.failed")
            .description("Total workflow runs failed")
            .register(registry);
        this.runsCompensated = Counter.builder("workflow.runs.compensated")
            .description("Total workflow runs compensated")
            .register(registry);
        this.stepRetries = Counter.builder("workflow.step.retries")
            .description("Total step retry attempts")
            .register(registry);
    }

    @Override
    public void onEvent(@NotNull WorkflowLifecycleEvent event) {
        switch (event.phase()) {
            case WORKFLOW_STARTED -> runsStarted.increment();
            case WORKFLOW_COMPLETED -> runsCompleted.increment();
            case WORKFLOW_FAILED -> runsFailed.increment();
            case WORKFLOW_COMPENSATED -> runsCompensated.increment();

            case STEP_STARTED -> {
                if (event.stepId() != null) {
                    stepStartTimes.put(event.runId() + ":" + event.stepId(), event.timestamp());
                }
            }

            case STEP_COMPLETED, STEP_FAILED -> {
                if (event.stepId() != null) {
                    String key = event.runId() + ":" + event.stepId();
                    Instant start = stepStartTimes.remove(key);
                    if (start != null) {
                        Duration elapsed = Duration.between(start, event.timestamp());
                        Timer.builder("workflow.step.duration")
                            .tag("step", event.stepId())
                            .tag("status", event.phase().name())
                            .register(registry)
                            .record(elapsed);
                    }
                    if (event.phase() == WorkflowLifecycleEvent.Phase.STEP_FAILED) {
                        Counter.builder("workflow.step.failures")
                            .tag("step", event.stepId())
                            .register(registry)
                            .increment();
                    }
                }
            }

            case STEP_RETRYING -> stepRetries.increment();

            default -> { /* no metric for other phases */ }
        }
    }
}
