/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service — Proactive Phase Gate Scheduler
 */
package com.ghatana.yappc.services.lifecycle.scheduler;

import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.services.lifecycle.gate.PhaseGateValidator;
import com.ghatana.yappc.services.metrics.BusinessMetrics;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Background scheduler that proactively checks lifecycle gate status for all active projects.
 *
 * <p>This service runs on a configurable interval (default: 5 minutes) and validates phase gates
 * for all active projects. Gate failures are emitted as metrics and can trigger alerts via the
 * observability stack.</p>
 *
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>{@code yappc.scheduler.phase-gate.interval} - Check interval in seconds (default: 300)</li>
 *   <li>{@code yappc.scheduler.phase-gate.enabled} - Enable/disable scheduler (default: true)</li>
 * </ul>
 *
 * <p><b>Metrics Emitted:</b></p>
 * <ul>
 *   <li>{@code yappc_phase_gate_check_total} - Total checks performed</li>
 *   <li>{@code yappc_phase_gate_check_failures_total} - Total gate failures detected</li>
 *   <li>{@code yappc_phase_gate_check_duration_seconds} - Check duration histogram</li>
 *   <li>{@code yappc_phase_gate_blocked_projects} - Gauge of currently blocked projects</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Proactive phase gate validation scheduler
 * @doc.layer product
 * @doc.pattern Scheduled Service
 */
public final class PhaseGateSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(PhaseGateSchedulerService.class);

    private static final Duration DEFAULT_INTERVAL = Duration.ofMinutes(5);
    private static final String CONFIG_INTERVAL = "yappc.scheduler.phase-gate.interval";
    private static final String CONFIG_ENABLED = "yappc.scheduler.phase-gate.enabled";

    private final Eventloop eventloop;
    private final PhaseGateValidator validator;
    private final ProjectProvider projectProvider;
    @Nullable
    private final BusinessMetrics metrics;

    private final Duration checkInterval;
    private final boolean enabled;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger checkCounter = new AtomicInteger(0);
    private final AtomicInteger failureCounter = new AtomicInteger(0);
    private final Map<String, GateCheckResult> lastResults = new ConcurrentHashMap<>();

    /**
     * Constructs the phase gate scheduler service.
     *
     * @param eventloop        the ActiveJ eventloop for async operations
     * @param validator        the phase gate validator
     * @param projectProvider  provider of active projects to check
     * @param metrics          optional metrics publisher
     * @param config           configuration map
     */
    public PhaseGateSchedulerService(
            @NotNull Eventloop eventloop,
            @NotNull PhaseGateValidator validator,
            @NotNull ProjectProvider projectProvider,
            @Nullable BusinessMetrics metrics,
            @NotNull Map<String, String> config) {
        this.eventloop = Objects.requireNonNull(eventloop, "eventloop");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.projectProvider = Objects.requireNonNull(projectProvider, "projectProvider");
        this.metrics = metrics;
        this.enabled = Boolean.parseBoolean(config.getOrDefault(CONFIG_ENABLED, "true"));
        
        long intervalSeconds = Long.parseLong(config.getOrDefault(CONFIG_INTERVAL, "300"));
        this.checkInterval = Duration.ofSeconds(intervalSeconds);
    }

    /**
     * Starts the scheduler. Does nothing if already running.
     */
    public void start() {
        if (!enabled) {
            log.info("Phase gate scheduler disabled via configuration");
            return;
        }

        if (running.compareAndSet(false, true)) {
            log.info("Starting phase gate scheduler with interval: {}", checkInterval);
            scheduleNextCheck();
        }
    }

    /**
     * Stops the scheduler. Does nothing if not running.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping phase gate scheduler");
            // Eventloop will cancel scheduled tasks on shutdown
        }
    }

    /**
     * Checks if the scheduler is currently running.
     */
    public boolean isRunning() {
        return running.get();
    }

    // ── Internal scheduling ─────────────────────────────────────────────────────

    private void scheduleNextCheck() {
        if (!running.get()) {
            return;
        }

        eventloop.delay(checkInterval.toMillis(), () -> {
            runGateCheck()
                    .whenComplete(() -> {
                        if (running.get()) {
                            scheduleNextCheck();
                        }
                    });
        });
    }

    private Promise<Void> runGateCheck() {
        long startMs = System.currentTimeMillis();
        int checkNumber = checkCounter.incrementAndGet();

        log.debug("Running phase gate check #{}", checkNumber);

        return projectProvider.getActiveProjects()
                .then(projects -> {
                    log.debug("Checking {} active projects", projects.size());

                    List<Promise<GateCheckResult>> checks = projects.stream()
                            .map(this::checkProjectGate)
                            .toList();

                    return Promises.toList(checks)
                            .map(results -> {
                                int failures = (int) results.stream()
                                        .filter(r -> !r.passed)
                                        .count();
                                
                                failureCounter.addAndGet(failures);
                                lastResults.clear();
                                results.forEach(r -> lastResults.put(r.projectId, r));

                                log.info("Phase gate check #{} completed: {} projects checked, {} failures",
                                        checkNumber, projects.size(), failures);

                                emitCheckMetrics(startMs, projects.size(), failures);

                                return (Void) null;
                            });
                })
                .then(Promise::of, ex -> {
                    log.error("Phase gate check #{} failed", checkNumber, ex);
                    emitErrorMetrics(startMs);
                    return Promise.complete();
                });
    }

    private Promise<GateCheckResult> checkProjectGate(ProjectInfo project) {
        return validator.validate(project.projectId, project.currentPhase, Map.of())
                .map(result -> {
                    boolean passed = result.allClear();
                    GateCheckResult checkResult = new GateCheckResult(
                            project.projectId,
                            project.currentPhase,
                            passed,
                            result.blockers()
                    );

                    if (!passed) {
                        log.warn("Project {} blocked at phase {}: {}",
                                project.projectId, project.currentPhase, result.blockers());
                    }

                    return checkResult;
                })
                .then(Promise::of, ex -> {
                    log.error("Gate check failed for project {}", project.projectId, ex);
                    return Promise.of(new GateCheckResult(
                            project.projectId,
                            project.currentPhase,
                            false,
                            List.of("check-error: " + ex.getMessage())));
                });
    }

    // ── Metrics emission ─────────────────────────────────────────────────────────

    private void emitCheckMetrics(long startMs, int projectCount, int failures) {
        if (metrics == null) {
            return;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        String outcome = failures == 0 ? "PASS" : "BLOCK";

        metrics.setActiveProjects(Math.max(projectCount - failures, 0));
        metrics.recordPhaseGateValidation("scheduler", "all-projects", outcome, durationMs);
    }

    private void emitErrorMetrics(long startMs) {
        if (metrics == null) {
            return;
        }

        long durationMs = System.currentTimeMillis() - startMs;
        metrics.recordPhaseGateValidation("scheduler", "all-projects", "ERROR", durationMs);
    }

    // ── Public query API ────────────────────────────────────────────────────────

    /**
     * Gets the most recent gate check result for a project.
     *
     * @param projectId the project ID
     * @return the last check result, or null if not checked yet
     */
    @Nullable
    public GateCheckResult getLastCheckResult(String projectId) {
        return lastResults.get(projectId);
    }

    /**
     * Gets all projects currently blocked by gate failures.
     *
     * @return list of blocked project IDs
     */
    public List<String> getBlockedProjects() {
        return lastResults.values().stream()
                .filter(r -> !r.passed)
                .map(r -> r.projectId)
                .toList();
    }

    /**
     * Gets scheduler statistics.
     */
    public SchedulerStats getStats() {
        return new SchedulerStats(
                checkCounter.get(),
                failureCounter.get(),
                lastResults.size(),
                checkInterval,
                running.get()
        );
    }

    // ── Supporting types ────────────────────────────────────────────────────────

    /**
     * Provider of active projects for gate checking.
     */
    public interface ProjectProvider {
        /**
         * Returns all active projects that should have their gates checked.
         */
        Promise<List<ProjectInfo>> getActiveProjects();
    }

    /**
     * Basic project information needed for gate checking.
     */
    public record ProjectInfo(
            String projectId,
            PhaseType currentPhase,
            Instant lastUpdated
    ) {}

    /**
     * Result of a single project gate check.
     */
    public record GateCheckResult(
            String projectId,
            PhaseType phase,
            boolean passed,
            List<String> blockers
    ) {
        public Instant checkedAt() {
            return Instant.now();
        }
    }

    /**
     * Scheduler statistics.
     */
    public record SchedulerStats(
            int totalChecks,
            int totalFailures,
            int projectsTracked,
            Duration checkInterval,
            boolean running
    ) {}
}
