package com.ghatana.agent.learning.retention;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Schedules periodic retention runs.
 * Default: every 24 hours.
 *
 * @doc.type class
 * @doc.purpose Retention scheduling
 * @doc.layer agent-learning
 */
public class RetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

    private final RetentionManager retentionManager;
    private final ScheduledExecutorService executor;
    private final String agentId;
    private final Duration interval;
    private final RetentionConfig config;
    private volatile ScheduledFuture<?> scheduledFuture;

    public RetentionScheduler(
            @NotNull RetentionManager retentionManager,
            @NotNull ScheduledExecutorService executor,
            @NotNull String agentId,
            @NotNull RetentionConfig config,
            @NotNull Duration interval) {
        this.retentionManager = Objects.requireNonNull(retentionManager);
        this.executor = Objects.requireNonNull(executor);
        this.agentId = Objects.requireNonNull(agentId);
        this.config = Objects.requireNonNull(config);
        this.interval = Objects.requireNonNull(interval);
    }

    public RetentionScheduler(
            @NotNull RetentionManager retentionManager,
            @NotNull ScheduledExecutorService executor,
            @NotNull String agentId,
            @NotNull RetentionConfig config) {
        this(retentionManager, executor, agentId, config, Duration.ofHours(24));
    }

    public void start() {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            log.warn("Retention scheduler already running for agent {}", agentId);
            return;
        }
        log.info("Starting retention scheduler for agent {} (interval={})", agentId, interval);
        scheduledFuture = executor.scheduleAtFixedRate(
                this::runRetention,
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
            log.info("Stopped retention scheduler for agent {}", agentId);
        }
    }

    @NotNull
    public Promise<RetentionResult> triggerNow() {
        return retentionManager.applyRetention(agentId, config);
    }

    private void runRetention() {
        try {
            retentionManager.applyRetention(agentId, config)
                    .whenResult(result -> log.info("Retention complete for agent {}: kept={}, decayed={}, evicted={}",
                            agentId, result.getKept(), result.getDecayed(), result.getEvicted()))
                    .whenException(e -> log.error("Retention failed for agent {}", agentId, e));
        } catch (Exception e) {
            log.error("Error in retention scheduler for agent {}", agentId, e);
        }
    }
}
