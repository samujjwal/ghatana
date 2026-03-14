package com.ghatana.appplatform.eventstore.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled monitor that detects saga steps stuck in {@code STEP_PENDING} beyond their
 * configured timeout and triggers compensation via {@link SagaOrchestrator#onStepFailed}.
 *
 * <p>The monitor runs on a single daemon thread polling the {@link SagaStore} at a
 * configurable interval. Compensation is triggered synchronously within the poll loop
 * so failures are captured in the same thread context.
 *
 * <p>Step-level timeout is expressed as a single global {@code stepTimeoutDuration}
 * applied to <em>all</em> STEP_PENDING instances. Per-step override will be added
 * in a future sprint when the step registry supports it.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SagaTimeoutMonitor monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
 *     Duration.ofMinutes(5), Duration.ofSeconds(30));
 * monitor.start();
 * // ...
 * monitor.stop();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Detects timed-out saga steps and triggers compensation (STORY-K05-019)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SagaTimeoutMonitor {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutMonitor.class);

    private final SagaStore sagaStore;
    private final SagaOrchestrator orchestrator;
    /** How long a step may remain STEP_PENDING before it is considered timed out. */
    private final Duration stepTimeout;
    /** How often the monitor polls for timed-out instances. */
    private final Duration pollInterval;

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledTask;

    /**
     * @param sagaStore    persistence port to query for timed-out instances
     * @param orchestrator called with the saga ID when a timeout is detected
     * @param stepTimeout  maximum allowed time for a step to remain STEP_PENDING
     * @param pollInterval how often the monitor checks for timed-out steps
     */
    public SagaTimeoutMonitor(SagaStore sagaStore,
                              SagaOrchestrator orchestrator,
                              Duration stepTimeout,
                              Duration pollInterval) {
        this.sagaStore    = Objects.requireNonNull(sagaStore,    "sagaStore");
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.stepTimeout  = Objects.requireNonNull(stepTimeout,  "stepTimeout");
        this.pollInterval = Objects.requireNonNull(pollInterval, "pollInterval");
        this.scheduler    = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "saga-timeout-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Convenience constructor using a 5-minute step timeout and 30-second poll interval.
     */
    public SagaTimeoutMonitor(SagaStore sagaStore, SagaOrchestrator orchestrator) {
        this(sagaStore, orchestrator, Duration.ofMinutes(5), Duration.ofSeconds(30));
    }

    /** Starts the background poll loop. Safe to call once. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[SagaTimeoutMonitor] Already running — ignoring duplicate start()");
            return;
        }
        scheduledTask = scheduler.scheduleAtFixedRate(
            this::checkForTimeouts,
            0,
            pollInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
        log.info("[SagaTimeoutMonitor] Started — stepTimeout={} pollInterval={}",
            stepTimeout, pollInterval);
    }

    /** Stops the monitor. Waits up to 10 seconds for the current check to complete. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[SagaTimeoutMonitor] Stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Single poll cycle: finds timed-out instances and triggers compensation for each.
     * Errors on individual sagas are logged and skipped so one bad saga does not block others.
     */
    void checkForTimeouts() {
        Instant cutoff = Instant.now().minus(stepTimeout);
        List<SagaInstance> timedOut;
        try {
            timedOut = sagaStore.findTimedOutInstances(cutoff);
        } catch (Exception e) {
            log.error("[SagaTimeoutMonitor] Failed to query timed-out sagas", e);
            return;
        }

        if (timedOut.isEmpty()) {
            return;
        }

        log.info("[SagaTimeoutMonitor] Found {} timed-out saga step(s) at cutoff={}",
            timedOut.size(), cutoff);

        for (SagaInstance instance : timedOut) {
            try {
                log.warn("[SagaTimeoutMonitor] Triggering compensation for timed-out saga sagaId={} type={} step={}",
                    instance.sagaId(), instance.sagaType(), instance.currentStepOrder());
                orchestrator.onStepFailed(instance.sagaId(), "Step timed out after " + stepTimeout);
            } catch (Exception e) {
                log.error("[SagaTimeoutMonitor] Failed to trigger compensation for sagaId={}",
                    instance.sagaId(), e);
            }
        }
    }
}
