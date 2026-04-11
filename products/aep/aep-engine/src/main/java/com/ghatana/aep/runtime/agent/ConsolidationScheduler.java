package com.ghatana.agent.learning.consolidation;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules periodic consolidation runs for an agent.
 * Configurable interval (default: every 6 hours).
 *
 * @doc.type class
 * @doc.purpose Consolidation scheduling
 * @doc.layer agent-learning
 */
public class ConsolidationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationScheduler.class);

    private final ConsolidationPipeline pipeline;
    private final ScheduledExecutorService executor;
    private final Duration interval;
    private final String agentId;
    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile Instant lastConsolidation = Instant.EPOCH;

    public ConsolidationScheduler(
            @NotNull ConsolidationPipeline pipeline,
            @NotNull ScheduledExecutorService executor,
            @NotNull String agentId,
            @NotNull Duration interval) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        this.interval = Objects.requireNonNull(interval, "interval");
    }

    /**
     * Creates a scheduler with the default 6-hour interval.
     */
    public ConsolidationScheduler(
            @NotNull ConsolidationPipeline pipeline,
            @NotNull ScheduledExecutorService executor,
            @NotNull String agentId) {
        this(pipeline, executor, agentId, Duration.ofHours(6));
    }

    /**
     * Starts the periodic consolidation schedule.
     */
    public void start() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            log.warn("Consolidation scheduler already running for agent {}", agentId);
            return;
        }

        log.info("Starting consolidation scheduler for agent {} (interval={})", agentId, interval);
        scheduledFuture = executor.scheduleAtFixedRate(
                this::runConsolidation,
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the periodic consolidation schedule.
     */
    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("Stopped consolidation scheduler for agent {}", agentId);
        }
    }

    /**
     * Triggers an immediate consolidation run.
     */
    @NotNull
    public Promise<ConsolidationPipeline.ConsolidationResult> triggerNow() {
        Instant since = lastConsolidation;
        return pipeline.consolidate(agentId, since)
                .whenResult(result -> lastConsolidation = result.completedAt());
    }

    private void runConsolidation() {
        try {
            log.debug("Running scheduled consolidation for agent {}", agentId);
            Instant since = lastConsolidation;
            // Fire-and-forget in the scheduled context
            pipeline.consolidate(agentId, since)
                    .whenResult(result -> {
                        lastConsolidation = result.completedAt();
                        log.info("Consolidation complete for agent {}: {} facts, {} procedures",
                                agentId, result.factsExtracted(), result.proceduresInduced());
                    })
                    .whenException(e -> log.error("Consolidation failed for agent {}", agentId, e));
        } catch (Exception e) {
            log.error("Error in consolidation scheduler for agent {}", agentId, e);
        }
    }
}
