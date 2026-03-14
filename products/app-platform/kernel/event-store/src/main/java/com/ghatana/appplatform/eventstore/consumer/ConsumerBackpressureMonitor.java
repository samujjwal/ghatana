package com.ghatana.appplatform.eventstore.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Monitors downstream processing queue depth and automatically pauses/resumes
 * the upstream {@link EventConsumerBase} to avoid overwhelming slow consumers.
 *
 * <h2>Backpressure algorithm</h2>
 * <ul>
 *   <li>Pause Kafka consumption when queue depth exceeds {@code pauseThreshold}.</li>
 *   <li>Resume when queue depth drops below {@code resumeThreshold}.</li>
 *   <li>Hysteresis gap (pause &gt; resume) prevents oscillation.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * BlockingQueue<AggregateEventRecord> processingQueue = new ArrayBlockingQueue<>(1000);
 * ConsumerBackpressureMonitor monitor = new ConsumerBackpressureMonitor(
 *     consumer, processingQueue, 800, 200, Duration.ofMillis(500));
 * monitor.start();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Auto-pauses/resumes EventConsumerBase based on queue depth (STORY-K05-025)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ConsumerBackpressureMonitor {

    private static final Logger log = LoggerFactory.getLogger(ConsumerBackpressureMonitor.class);

    private final EventConsumerBase consumer;
    private final BlockingQueue<?> processingQueue;
    private final int pauseThreshold;
    private final int resumeThreshold;
    private final long pollIntervalMs;

    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused  = new AtomicBoolean(false);
    private ScheduledFuture<?> task;

    /** Optional callback invoked when backpressure state changes (for metrics and alerts). */
    private Consumer<Boolean> onBackpressureChange;

    /**
     * @param consumer          the Kafka consumer to pause/resume
     * @param processingQueue   the queue whose depth controls backpressure
     * @param pauseThreshold    pause when {@code queue.size() >= pauseThreshold}
     * @param resumeThreshold   resume when {@code queue.size() <= resumeThreshold}
     * @param pollIntervalMs    how often the monitor checks queue depth (milliseconds)
     */
    public ConsumerBackpressureMonitor(EventConsumerBase consumer,
                                       BlockingQueue<?> processingQueue,
                                       int pauseThreshold,
                                       int resumeThreshold,
                                       long pollIntervalMs) {
        if (resumeThreshold >= pauseThreshold) {
            throw new IllegalArgumentException(
                "resumeThreshold must be < pauseThreshold to avoid oscillation; got resume="
                    + resumeThreshold + " pause=" + pauseThreshold);
        }
        this.consumer         = Objects.requireNonNull(consumer,         "consumer");
        this.processingQueue  = Objects.requireNonNull(processingQueue,  "processingQueue");
        this.pauseThreshold   = pauseThreshold;
        this.resumeThreshold  = resumeThreshold;
        this.pollIntervalMs   = pollIntervalMs;
        this.scheduler        = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "backpressure-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Optional: register a callback invoked whenever backpressure activates ({@code true})
     * or deactivates ({@code false}). Use this to update Prometheus metrics.
     */
    public ConsumerBackpressureMonitor withBackpressureChangeCallback(Consumer<Boolean> callback) {
        this.onBackpressureChange = callback;
        return this;
    }

    /** Starts the monitoring loop. Safe to call once. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        task = scheduler.scheduleAtFixedRate(this::checkBackpressure,
            pollIntervalMs, pollIntervalMs, TimeUnit.MILLISECONDS);
        log.info("[ConsumerBackpressureMonitor] Started — pause@{} resume@{} poll={}ms",
            pauseThreshold, resumeThreshold, pollIntervalMs);
    }

    /** Stops the monitoring loop and resumes the consumer if it was paused. */
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (task != null) task.cancel(false);
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Always resume on shutdown to avoid leaving the consumer permanently paused
        if (paused.get()) {
            consumer.resume();
            paused.set(false);
        }
        log.info("[ConsumerBackpressureMonitor] Stopped");
    }

    /** Returns whether backpressure is currently active. */
    public boolean isBackpressureActive() {
        return paused.get();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    void checkBackpressure() {
        int depth = processingQueue.size();

        if (!paused.get() && depth >= pauseThreshold) {
            consumer.pause();
            paused.set(true);
            log.warn("[ConsumerBackpressureMonitor] Backpressure ACTIVATED — queueDepth={} threshold={}",
                depth, pauseThreshold);
            notifyCallback(true);
        } else if (paused.get() && depth <= resumeThreshold) {
            consumer.resume();
            paused.set(false);
            log.info("[ConsumerBackpressureMonitor] Backpressure DEACTIVATED — queueDepth={} threshold={}",
                depth, resumeThreshold);
            notifyCallback(false);
        }
    }

    private void notifyCallback(boolean active) {
        if (onBackpressureChange != null) {
            try {
                onBackpressureChange.accept(active);
            } catch (Exception e) {
                log.warn("[ConsumerBackpressureMonitor] Callback threw", e);
            }
        }
    }
}
