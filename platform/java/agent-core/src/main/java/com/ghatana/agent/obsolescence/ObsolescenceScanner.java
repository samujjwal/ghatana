/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.obsolescence;

import com.ghatana.agent.environment.EnvironmentFingerprint;
import com.ghatana.agent.mastery.MasteryRegistry;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scanner for detecting obsolescence in mastery items.
 *
 * <p>Supports both periodic scheduled scans and event-triggered scans.
 * Periodic scans run on a fixed schedule to detect stale mastery items.
 * Event-triggered scans run in response to specific events like dependency updates.
 *
 * @doc.type class
 * @doc.purpose Scanner for obsolescence detection
 * @doc.layer agent-core
 * @doc.pattern Scanner
 */
public final class ObsolescenceScanner {

    private final ObsolescenceDetector detector;
    private final ObsolescenceRouter router;
    private final EnvironmentFingerprintProvider environmentFingerprintProvider;
    private final ScheduledExecutorService scheduler;

    /**
     * Provider for environment fingerprints.
     */
    @FunctionalInterface
    public interface EnvironmentFingerprintProvider {
        @NotNull
        EnvironmentFingerprint provide();
    }

    /**
     * Creates an obsolescence scanner.
     *
     * @param detector obsolescence detector
     * @param router obsolescence router
     * @param environmentFingerprintProvider provider for environment fingerprints
     * @param scheduler scheduled executor service for periodic scans
     */
    public ObsolescenceScanner(
            @NotNull ObsolescenceDetector detector,
            @NotNull ObsolescenceRouter router,
            @NotNull EnvironmentFingerprintProvider environmentFingerprintProvider,
            @NotNull ScheduledExecutorService scheduler) {
        this.detector = Objects.requireNonNull(detector, "detector must not be null");
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.environmentFingerprintProvider = Objects.requireNonNull(environmentFingerprintProvider, "environmentFingerprintProvider must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler must not be null");
    }

    /**
     * Performs a single scan for obsolescence and routes detected events.
     *
     * @return promise of scan results
     */
    @NotNull
    public Promise<ObsolescenceScanResult> scan() {
        EnvironmentFingerprint env = environmentFingerprintProvider.provide();
        return detector.scanAll(env)
                .then(events -> router.routeAll(events)
                        .map(transitionResults -> new ObsolescenceScanResult(
                                events,
                                transitionResults,
                                Instant.now()
                        )));
    }

    /**
     * Performs a scan triggered by a specific event (e.g., dependency update).
     *
     * @param triggerEvent description of the trigger event
     * @return promise of scan results
     */
    @NotNull
    public Promise<ObsolescenceScanResult> scanTriggered(@NotNull String triggerEvent) {
        return scan().then(result -> Promise.of(new ObsolescenceScanResult(
                result.events(),
                result.transitionResults(),
                result.scannedAt(),
                triggerEvent
        )));
    }

    /**
     * Starts periodic scanning with the specified interval.
     *
     * @param interval scan interval
     * @param unit time unit
     */
    public void startPeriodicScans(long interval, @NotNull TimeUnit unit) {
        scheduler.scheduleAtFixedRate(() -> {
            scan().whenException(e -> {
                // Log error but continue periodic scans
                System.err.println("Periodic obsolescence scan failed: " + e.getMessage());
            });
        }, 0, interval, unit);
    }

    /**
     * Stops periodic scanning.
     */
    public void stopPeriodicScans() {
        scheduler.shutdown();
    }

    /**
     * Result of an obsolescence scan.
     */
    public record ObsolescenceScanResult(
            @NotNull List<ObsolescenceEvent> events,
            @NotNull List<com.ghatana.agent.mastery.MasteryTransitionResult> transitionResults,
            @NotNull Instant scannedAt,
            @NotNull String triggerEvent
    ) {
        public ObsolescenceScanResult(
                @NotNull List<ObsolescenceEvent> events,
                @NotNull List<com.ghatana.agent.mastery.MasteryTransitionResult> transitionResults,
                @NotNull Instant scannedAt) {
            this(events, transitionResults, scannedAt, "scheduled");
        }
    }
}
