/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.learning;

import com.ghatana.aep.learning.EpisodeLearningPipeline;
import io.activej.eventloop.Eventloop;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Schedules periodic learning reflection runs on the ActiveJ event loop.
 *
 * <p>Must be initialised after the eventloop is created by calling
 * {@link #init(Eventloop)}. When the loop is stopped, scheduling ceases
 * naturally because {@code eventloop.delay()} callbacks will no longer fire.
 *
 * <p>Configuration is controlled by two constructor arguments:
 * <ul>
 *   <li>{@code intervalMs} — how often to trigger a reflection cycle (default 3 600 000 ms = 1 h)</li>
 *   <li>{@code tenants}    — explicit tenant list to reflect on; {@code null} defaults to
 *                           {@code ["default"]}</li>
 * </ul>
 *
 * <p>Reflection errors are caught per-tenant and logged; they do not abort the schedule.
 *
 * @doc.type class
 * @doc.purpose Periodic background scheduler for the AEP learning pipeline
 * @doc.layer product
 * @doc.pattern Scheduler
 */
public final class LearningScheduler {

    private static final Logger log = LoggerFactory.getLogger(LearningScheduler.class);

    /** Default reflection interval: 1 hour. */
    public static final long DEFAULT_INTERVAL_MS = 3_600_000L;

    private final EpisodeLearningPipeline pipeline;
    private final long intervalMs;
    private final List<String> tenants;

    @Nullable
    private Eventloop eventloop;
    private volatile boolean stopped = false;

    /**
     * Creates a scheduler with the default 1-hour interval and a single {@code "default"} tenant.
     *
     * @param pipeline the learning pipeline to invoke on each cycle
     */
    public LearningScheduler(EpisodeLearningPipeline pipeline) {
        this(pipeline, DEFAULT_INTERVAL_MS, null);
    }

    /**
     * Creates a scheduler with an explicit interval and tenant list.
     *
     * @param pipeline   the learning pipeline to invoke on each cycle
     * @param intervalMs how often to run the cycle in milliseconds (&gt; 0)
     * @param tenants    tenant IDs to reflect on; {@code null} defaults to {@code ["default"]}
     */
    public LearningScheduler(
            EpisodeLearningPipeline pipeline,
            long intervalMs,
            @Nullable List<String> tenants) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (intervalMs <= 0) throw new IllegalArgumentException("intervalMs must be > 0, got: " + intervalMs);
        this.intervalMs = intervalMs;
        this.tenants = (tenants == null || tenants.isEmpty()) ? List.of("default") : List.copyOf(tenants);
    }

    /**
     * Registers this scheduler with the given eventloop and schedules the first reflection cycle.
     * Must be called from the event-loop thread (or before the loop starts) — same contract as
     * {@link com.ghatana.aep.server.http.controllers.SseController#init(Eventloop)}.
     *
     * @param eventloop the running ActiveJ eventloop
     */
    public void init(Eventloop eventloop) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
        log.info("[learning-scheduler] scheduled; interval={}ms tenants={}", intervalMs, tenants);
        scheduleNext();
    }

    /** Signals the scheduler to stop issuing new cycles after the current one completes. */
    public void stop() {
        stopped = true;
        log.info("[learning-scheduler] stopped");
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void scheduleNext() {
        if (eventloop == null || stopped) return;
        eventloop.delay(intervalMs, this::runCycle);
    }

    private void runCycle() {
        if (stopped) return;
        log.info("[learning-scheduler] starting reflection cycle for {} tenant(s)", tenants.size());
        runForTenants(0);
    }

    /**
     * Recursively runs reflection for each tenant in order, then re-schedules.
     * Using tail-recursive chaining keeps the event loop non-blocking.
     */
    private void runForTenants(int index) {
        if (index >= tenants.size()) {
            log.info("[learning-scheduler] reflection cycle complete");
            scheduleNext();
            return;
        }

        String tenantId = tenants.get(index);
        pipeline.run(tenantId)
                .whenResult(result -> {
                    if (result.success()) {
                        log.info("[learning-scheduler] tenant={} episodes={} queued={}",
                                tenantId, result.episodesRead(), result.policiesQueued());
                    } else {
                        log.warn("[learning-scheduler] tenant={} reflection failed: {}",
                                tenantId, result.errorMessage());
                    }
                    runForTenants(index + 1);
                })
                .whenException(e -> {
                    log.error("[learning-scheduler] tenant={} reflection threw: {}",
                            tenantId, e.getMessage(), e);
                    runForTenants(index + 1);
                });
    }
}
