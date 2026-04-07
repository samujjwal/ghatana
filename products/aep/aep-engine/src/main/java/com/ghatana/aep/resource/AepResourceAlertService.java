/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Proactive resource exhaustion alert service for AEP (AEP-005.4).
 *
 * <p>Evaluates {@link AepMemoryMonitor.MemorySnapshot} and
 * {@link AepCpuOptimizer.CpuSnapshot} together and fires registered
 * {@link ResourceAlertListener}s when thresholds are breached.  Fires
 * at most once per breach window to avoid listener spam.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepResourceAlertService alerts = AepResourceAlertService.builder()
 *     .heapAlertThreshold(0.85)
 *     .cpuAlertThreshold(0.80)
 *     .build();
 *
 * alerts.addListener(alert -> LOG.warn("ALERT: {}", alert));
 *
 * alerts.evaluate(memMonitor.currentSnapshot(), cpuOptimizer.currentSnapshot());
 * }</pre>
 *
 * @doc.type    class
 * @doc.purpose Proactive resource exhaustion alert service
 * @doc.layer   product
 * @doc.pattern Observer, Alert
 */
public final class AepResourceAlertService {

    private static final Logger LOG = LoggerFactory.getLogger(AepResourceAlertService.class);

    private final double heapAlertThreshold;
    private final double cpuAlertThreshold;

    private final CopyOnWriteArrayList<ResourceAlertListener> listeners = new CopyOnWriteArrayList<>();
    private final List<ResourceAlert> alertHistory = Collections.synchronizedList(new ArrayList<>());

    private volatile boolean heapAlertActive = false;
    private volatile boolean cpuAlertActive  = false;

    private AepResourceAlertService(Builder builder) {
        this.heapAlertThreshold = builder.heapAlertThreshold;
        this.cpuAlertThreshold  = builder.cpuAlertThreshold;
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers a listener that is called synchronously when an alert fires.
     *
     * @param listener alert listener
     */
    public void addListener(ResourceAlertListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Evaluates current memory and CPU snapshots and fires alerts as needed.
     *
     * @param memSnap memory snapshot from {@link AepMemoryMonitor}
     * @param cpuSnap CPU snapshot from {@link AepCpuOptimizer}
     */
    public void evaluate(AepMemoryMonitor.MemorySnapshot memSnap,
                         AepCpuOptimizer.CpuSnapshot cpuSnap) {
        Objects.requireNonNull(memSnap, "memSnap must not be null");
        Objects.requireNonNull(cpuSnap, "cpuSnap must not be null");

        evaluateHeap(memSnap);
        evaluateCpu(cpuSnap);
    }

    private void evaluateHeap(AepMemoryMonitor.MemorySnapshot snap) {
        boolean high = snap.heapRatio() >= heapAlertThreshold;
        if (high && !heapAlertActive) {
            heapAlertActive = true;
            ResourceAlert alert = new ResourceAlert(
                    AlertType.HIGH_HEAP_USAGE,
                    String.format("Heap usage %.1f%% exceeds threshold %.1f%%",
                            snap.heapRatio() * 100, heapAlertThreshold * 100),
                    snap.heapRatio(),
                    heapAlertThreshold,
                    Instant.now()
            );
            fire(alert);
        } else if (!high && heapAlertActive) {
            heapAlertActive = false;
            LOG.info("Heap alert cleared — usage dropped to {:.1f}%", snap.heapRatio() * 100);
        }
    }

    private void evaluateCpu(AepCpuOptimizer.CpuSnapshot snap) {
        boolean high = snap.processCpuLoad() >= cpuAlertThreshold;
        if (high && !cpuAlertActive) {
            cpuAlertActive = true;
            ResourceAlert alert = new ResourceAlert(
                    AlertType.HIGH_CPU_USAGE,
                    String.format("CPU usage %.1f%% exceeds threshold %.1f%%",
                            snap.processCpuPercent(), cpuAlertThreshold * 100),
                    snap.processCpuLoad(),
                    cpuAlertThreshold,
                    Instant.now()
            );
            fire(alert);
        } else if (!high && cpuAlertActive) {
            cpuAlertActive = false;
            LOG.info("CPU alert cleared — usage dropped to {:.1f}%", snap.processCpuPercent());
        }
    }

    private void fire(ResourceAlert alert) {
        LOG.warn("Resource alert: {} — {}", alert.type(), alert.message());
        alertHistory.add(alert);
        for (ResourceAlertListener listener : listeners) {
            try {
                listener.onAlert(alert);
            } catch (Exception e) {
                LOG.error("Alert listener threw exception: {}", e.getMessage(), e);
            }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns all alerts fired since this service was created.
     *
     * @return immutable list of historical alerts
     */
    public List<ResourceAlert> alertHistory() {
        return Collections.unmodifiableList(new ArrayList<>(alertHistory));
    }

    /** Returns whether a heap alert is currently active. */
    public boolean isHeapAlertActive() { return heapAlertActive; }

    /** Returns whether a CPU alert is currently active. */
    public boolean isCpuAlertActive() { return cpuAlertActive; }

    // ── Nested types ──────────────────────────────────────────────────────────

    /** Alert category. */
    public enum AlertType { HIGH_HEAP_USAGE, HIGH_CPU_USAGE }

    /**
     * Immutable resource alert.
     *
     * @param type        alert category
     * @param message     human-readable message
     * @param currentValue current measurement [0, 1]
     * @param threshold   configured threshold that was breached [0, 1]
     * @param firedAt     when the alert was fired
     */
    public record ResourceAlert(
            AlertType type,
            String message,
            double currentValue,
            double threshold,
            Instant firedAt
    ) {}

    /** Listener for resource alerts. */
    @FunctionalInterface
    public interface ResourceAlertListener extends Consumer<ResourceAlert> {
        void onAlert(ResourceAlert alert);

        default void accept(ResourceAlert alert) { onAlert(alert); }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link AepResourceAlertService}.
     */
    public static final class Builder {
        private double heapAlertThreshold = 0.85;
        private double cpuAlertThreshold  = 0.80;

        private Builder() {}

        public Builder heapAlertThreshold(double threshold) {
            if (threshold < 0 || threshold > 1)
                throw new IllegalArgumentException("threshold must be in [0, 1]");
            this.heapAlertThreshold = threshold;
            return this;
        }

        public Builder cpuAlertThreshold(double threshold) {
            if (threshold < 0 || threshold > 1)
                throw new IllegalArgumentException("threshold must be in [0, 1]");
            this.cpuAlertThreshold = threshold;
            return this;
        }

        public AepResourceAlertService build() { return new AepResourceAlertService(this); }
    }
}

