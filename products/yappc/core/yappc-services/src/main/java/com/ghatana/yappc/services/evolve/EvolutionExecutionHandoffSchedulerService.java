package com.ghatana.yappc.services.evolve;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically dispatches queued evolve execution handoffs.
 *
 * @doc.type class
 * @doc.purpose Auto-dispatch queued evolve handoffs to lifecycle execution
 * @doc.layer service
 * @doc.pattern Scheduled Service
 */
public final class EvolutionExecutionHandoffSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(EvolutionExecutionHandoffSchedulerService.class);

    private static final String CONFIG_ENABLED = "yappc.scheduler.evolve-handoff.enabled";
    private static final String CONFIG_INTERVAL = "yappc.scheduler.evolve-handoff.interval";
    private static final String CONFIG_LIMIT = "yappc.scheduler.evolve-handoff.limit";
    private static final String CONFIG_TENANTS = "yappc.scheduler.evolve-handoff.tenants";

    private final Eventloop eventloop;
    private final EvolutionExecutionHandoffDispatcher dispatcher;
    private final boolean enabled;
    private final Duration interval;
    private final int dispatchLimit;
    private final List<String> tenants;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EvolutionExecutionHandoffSchedulerService(
            @NotNull Eventloop eventloop,
            @NotNull EvolutionExecutionHandoffDispatcher dispatcher,
            @NotNull Map<String, String> config
    ) {
        this.eventloop = eventloop;
        this.dispatcher = dispatcher;
        this.enabled = Boolean.parseBoolean(config.getOrDefault(CONFIG_ENABLED, "false"));
        this.interval = Duration.ofSeconds(Long.parseLong(config.getOrDefault(CONFIG_INTERVAL, "30")));
        this.dispatchLimit = Integer.parseInt(config.getOrDefault(CONFIG_LIMIT, "25"));

        String tenantsConfig = config.getOrDefault(CONFIG_TENANTS, "");
        this.tenants = Arrays.stream(tenantsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public void start() {
        if (!enabled) {
            log.info("Evolution handoff scheduler disabled via configuration");
            return;
        }
        if (tenants.isEmpty()) {
            log.warn("Evolution handoff scheduler enabled but no tenants configured; set {}", CONFIG_TENANTS);
            return;
        }

        if (running.compareAndSet(false, true)) {
            log.info("Starting evolution handoff scheduler: interval={}s, dispatchLimit={}, tenants={}",
                    interval.toSeconds(), dispatchLimit, tenants);
            scheduleNextTick();
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping evolution handoff scheduler");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void scheduleNextTick() {
        if (!running.get()) {
            return;
        }

        eventloop.delay(interval.toMillis(), () -> runDispatchCycle()
                .whenComplete(() -> {
                    if (running.get()) {
                        scheduleNextTick();
                    }
                }));
    }

    private Promise<Void> runDispatchCycle() {
        List<Promise<EvolutionExecutionHandoffDispatcher.DispatchSummary>> runs = tenants.stream()
                .map(tenant -> dispatcher.dispatchQueued(tenant, dispatchLimit)
                        .whenResult(summary -> log.info(
                                "Evolution handoff dispatch cycle: tenant={}, queued={}, dispatched={}, failed={}",
                                tenant, summary.queued(), summary.dispatched(), summary.failed()))
                        .whenException(error -> log.error(
                                "Evolution handoff dispatch cycle failed for tenant={}", tenant, error)))
                .toList();

        return Promises.toList(runs).toVoid();
    }
}
