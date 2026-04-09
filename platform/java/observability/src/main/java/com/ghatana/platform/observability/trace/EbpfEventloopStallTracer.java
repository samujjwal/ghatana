package com.ghatana.platform.observability.trace;

import io.activej.common.time.Stopwatch;
import io.activej.eventloop.inspector.EventloopInspector;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * @doc.type class
 * @doc.purpose Employs eBPF proxy principles and EventloopInspector to track ActiveJ loop stalls.
 * @doc.layer platform
 * @doc.pattern Service
 */
public class EbpfEventloopStallTracer implements EventloopInspector {

    private static final Logger log = LoggerFactory.getLogger(EbpfEventloopStallTracer.class);

    private final MeterRegistry registry;
    private final Timer stallTimer;
    private final long stallThresholdMs;

    public EbpfEventloopStallTracer(MeterRegistry registry, Duration stallThreshold) {
        this.registry = registry;
        this.stallThresholdMs = stallThreshold.toMillis();

        this.stallTimer = Timer.builder("eventloop.stall.duration")
                .description("Measures durations where the ActiveJ Eventloop executes beyond threshold")
                .publishPercentiles(0.9, 0.99)
                .register(registry);
    }

    private void checkStall(@Nullable Stopwatch sw, String taskType) {
        if (sw != null) {
            long elapsedMs = sw.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS);
            if (elapsedMs > stallThresholdMs) {
                log.warn("eBPF Eventloop Trace Alert: Detected stall in {} task lasting {} ms", taskType, elapsedMs);
                stallTimer.record(Duration.ofMillis(elapsedMs));
                registry.counter("eventloop.stall.count").increment();
            }
        }
    }

    @Override
    public void onUpdateBusinessLogicTime(boolean taskOrKeyPresent, boolean externalTaskPresent, long businessLogicTime) { }

    @Override
    public void onUpdateSelectorSelectTime(long selectorSelectTime) { }

    @Override
    public void onUpdateSelectorSelectTimeout(long selectorSelectTimeout) { }

    @Override
    public void onUpdateSelectedKeyDuration(Stopwatch sw) {
        checkStall(sw, "selectedKey");
    }

    @Override
    public void onUpdateSelectedKeysStats(int lastSelectedKeys, int invalidKeys, int acceptKeys, int connectKeys, int readKeys, int writeKeys, long loopTime) { }

    @Override
    public void onUpdateLocalTaskDuration(Runnable runnable, @Nullable Stopwatch sw) {
        checkStall(sw, "localTask");
    }

    @Override
    public void onUpdateLocalTasksStats(int localTasks, long loopTime) { }

    @Override
    public void onUpdateConcurrentTaskDuration(Runnable runnable, @Nullable Stopwatch sw) {
        checkStall(sw, "concurrentTask");
    }

    @Override
    public void onUpdateConcurrentTasksStats(int newConcurrentTasks, long loopTime) { }

    @Override
    public void onUpdateScheduledTaskDuration(Runnable runnable, @Nullable Stopwatch sw, boolean background) {
        checkStall(sw, "scheduledTask");
    }

    @Override
    public void onUpdateScheduledTasksStats(int scheduledTasks, long loopTime, boolean background) { }

    @Override
    public void onFatalError(Throwable e, @Nullable Object context) { }

    @Override
    public void onScheduledTaskOverdue(long overdue, boolean background) {
        if (overdue > stallThresholdMs) {
            log.warn("eBPF Eventloop Trace Alert: Detected scheduled task overdue by {} ms", overdue);
            stallTimer.record(Duration.ofMillis(overdue));
            registry.counter("eventloop.stall.count").increment();
        }
    }

    @Override
    public <T extends EventloopInspector> @Nullable T lookup(Class<T> type) {
        return null; // Used for multi-inspector wrapping
    }
}
