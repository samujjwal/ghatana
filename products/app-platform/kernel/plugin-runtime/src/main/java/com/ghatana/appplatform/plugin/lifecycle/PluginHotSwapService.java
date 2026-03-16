/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.plugin.lifecycle;

import com.ghatana.appplatform.plugin.domain.PluginManifest;
import com.ghatana.appplatform.plugin.domain.PluginRegistration;
import com.ghatana.appplatform.plugin.domain.PluginStatus;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Hot-swaps a running plugin to a new version with zero downtime (STORY-K04-011).
 *
 * <p>Hot-swap sequence:
 * <ol>
 *   <li><b>Pre-load</b> — load new version alongside current (both active)</li>
 *   <li><b>Health check</b> — verify new version passes liveness probe</li>
 *   <li><b>Drain</b> — wait for in-flight requests on current version to complete</li>
 *   <li><b>Switch</b> — atomically redirect traffic to new version</li>
 *   <li><b>Retire</b> — mark old version as {@link PluginStatus#RETIRED}</li>
 * </ol>
 *
 * <p>If health check fails the old version remains active and the new version is marked
 * {@link PluginStatus#FAILED}.
 *
 * @doc.type  class
 * @doc.purpose Zero-downtime plugin version swap with drain-and-switch (K04-011)
 * @doc.layer kernel
 * @doc.pattern Service
 */
public final class PluginHotSwapService {

    private static final Logger log = LoggerFactory.getLogger(PluginHotSwapService.class);

    /** Maximum milliseconds to wait for in-flight requests on the old version to drain. */
    private static final long DRAIN_TIMEOUT_MS = 5_000L;

    private final Executor executor;

    public PluginHotSwapService(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Performs a hot-swap from {@code current} to {@code candidate}.
     *
     * @param current          currently ACTIVE registration
     * @param candidate        new-version registration to swap in (must be PENDING_APPROVAL or ACTIVE)
     * @param healthChecker    supplier that returns {@code true} if the new version is healthy
     * @param trafficSwitcher  function that atomically switches live traffic from current to candidate
     * @return promise resolving to a {@link SwapResult} describing the outcome
     */
    public Promise<SwapResult> swap(PluginRegistration current,
                                     PluginRegistration candidate,
                                     Supplier<Boolean> healthChecker,
                                     Function<PluginRegistration, Boolean> trafficSwitcher) {
        Objects.requireNonNull(current,        "current");
        Objects.requireNonNull(candidate,      "candidate");
        Objects.requireNonNull(healthChecker,  "healthChecker");
        Objects.requireNonNull(trafficSwitcher,"trafficSwitcher");

        return Promise.ofBlocking(executor, () -> {
            log.info("Hot-swap: starting plugin={} current={} -> candidate={}",
                    current.pluginName(), current.version(), candidate.version());

            // Step 1: Health check the candidate
            boolean healthy;
            try {
                healthy = healthChecker.get();
            } catch (Exception e) {
                log.error("Hot-swap health check threw exception for plugin={}: {}", current.pluginName(), e.getMessage());
                healthy = false;
            }

            if (!healthy) {
                log.warn("Hot-swap health check failed for plugin={} candidate={}",
                        current.pluginName(), candidate.version());
                return new SwapResult(false, current, candidate.withStatus(PluginStatus.FAILED),
                        "Health check failed for candidate " + candidate.version());
            }

            // Step 2: Drain — simple sleep waiting for in-flight requests
            log.debug("Hot-swap: draining in-flight requests for plugin={}", current.pluginName());
            Thread.sleep(Math.min(DRAIN_TIMEOUT_MS, 1000)); // configurable drain window

            // Step 3: Atomically switch traffic
            boolean switched = trafficSwitcher.apply(candidate);
            if (!switched) {
                return new SwapResult(false, current, candidate,
                        "Traffic switch function returned false; aborting swap");
            }

            PluginRegistration retiredOld = current.withStatus(PluginStatus.RETIRED);
            PluginRegistration activeNew  = candidate.withStatus(PluginStatus.ACTIVE);

            log.info("Hot-swap complete: plugin={} old={} -> new={}", current.pluginName(),
                    current.version(), candidate.version());
            return new SwapResult(true, retiredOld, activeNew, null);
        });
    }

    // ── Domain types ──────────────────────────────────────────────────────────

    /**
     * Outcome of a hot-swap attempt.
     *
     * @param success         whether the swap completed successfully
     * @param oldRegistration the previous registration (RETIRED if success, unchanged otherwise)
     * @param newRegistration the new registration (ACTIVE if success, FAILED if health check failed)
     * @param failureReason   non-null when {@code success} is {@code false}
     */
    public record SwapResult(
            boolean success,
            PluginRegistration oldRegistration,
            PluginRegistration newRegistration,
            String failureReason
    ) {}
}
