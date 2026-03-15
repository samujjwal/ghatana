/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Zero-downtime hot-reload of OPA policy bundles without service restart (STORY-K03-007).
 *
 * <p>Reload sequence:
 * <ol>
 *   <li>Snapshot the currently active bundle (needed for rollback).</li>
 *   <li>Activate the requested new bundle via {@link PolicyBundleService#activateBundle}.</li>
 *   <li>Execute the caller-supplied {@link OpaHealthCheck} to verify the new bundle is
 *       healthy (e.g. OPA responds with HTTP 200 on its bundle endpoint).</li>
 *   <li>If health check <b>passes</b>: emit a success {@link ReloadResult} to listeners
 *       and return. The store now has the new bundle as active.</li>
 *   <li>If health check <b>fails</b>: rollback by re-activating the previous bundle via
 *       {@link PolicyBundleService#activateBundle}. Emit a rollback {@link ReloadResult}.</li>
 * </ol>
 *
 * <h2>Concurrency guarantee</h2>
 * <p>The {@link PolicyBundleStore} always holds exactly one active bundle throughout the
 * operation. Requests served by OPA use the active bundle at query time; since the store
 * rolls back atomically on health-check failure, in-flight OPA evaluations are never
 * left without a valid active bundle.
 *
 * <h2>Observability</h2>
 * <p>Every reload attempt (success or rollback) emits a {@link ReloadResult} to all
 * registered {@link ReloadEventListener}s, enabling metrics and audit (K-06 integration).
 *
 * @doc.type class
 * @doc.purpose Zero-downtime OPA bundle hot-reload with automatic rollback on failure (K03-007)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PolicyBundleReloadService {

    private final PolicyBundleService bundleService;
    private final List<ReloadEventListener> listeners = new ArrayList<>();

    /**
     * @param bundleService policy bundle service owning activation and integrity checks
     */
    public PolicyBundleReloadService(PolicyBundleService bundleService) {
        this.bundleService = Objects.requireNonNull(bundleService, "bundleService");
    }

    // ── Hot-reload ────────────────────────────────────────────────────────────

    /**
     * Performs a zero-downtime hot-reload to the specified bundle version.
     *
     * <p>If the health check fails after activation, the previous bundle is automatically
     * re-activated (rollback) and the result records {@link ReloadResult#rolledBack()} = {@code true}.
     *
     * @param newBundleId   ID of the bundle version to activate; must exist in the store
     * @param healthCheck   verifies that OPA successfully loaded the new bundle
     * @return reload outcome describing whether the switch succeeded or was rolled back
     * @throws IllegalArgumentException if the bundle ID is not found
     */
    public ReloadResult reload(String newBundleId, OpaHealthCheck healthCheck) {
        Objects.requireNonNull(newBundleId, "newBundleId");
        Objects.requireNonNull(healthCheck, "healthCheck");

        // Capture current active bundle before switching (needed for rollback)
        Optional<PolicyBundle> previousActive = bundleService.getActiveBundle();
        String previousBundleId = previousActive.map(PolicyBundle::bundleId).orElse(null);

        // Activate the new bundle (deactivates the previous one in the store)
        bundleService.activateBundle(newBundleId);

        // Health check: verify OPA successfully loaded the new bundle
        boolean healthy = healthCheck.isHealthy();

        if (healthy) {
            ReloadResult result = ReloadResult.success(newBundleId, previousBundleId);
            notifyListeners(result);
            return result;
        }

        // Health check failed → rollback to previous bundle
        if (previousBundleId != null) {
            bundleService.activateBundle(previousBundleId);
        }
        ReloadResult result = ReloadResult.rolledBack(newBundleId, previousBundleId);
        notifyListeners(result);
        return result;
    }

    /**
     * Registers a listener to receive notifications on each reload attempt.
     *
     * @param listener reload event recipient; not null
     */
    public void addReloadEventListener(ReloadEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * Health check executed against the OPA instance after activating a new bundle.
     *
     * <p>Implementations typically issue an HTTP GET to the OPA bundle status endpoint
     * (e.g. {@code /health?bundles}) and verify the bundle is in the {@code ACTIVE} state.
     * Implementations must not throw checked exceptions; return {@code false} on failure.
     */
    @FunctionalInterface
    public interface OpaHealthCheck {
        /**
         * @return {@code true} if OPA reports the activated bundle as healthy
         */
        boolean isHealthy();
    }

    /**
     * Result of a hot-reload attempt.
     *
     * @param success          true if the new bundle is now active and healthy
     * @param newBundleId      ID of the bundle that was requested for activation
     * @param previousBundleId ID of the bundle that was active before the reload;
     *                         null if there was no previously active bundle
     * @param rolledBack       true if the health check failed and the previous bundle
     *                         was re-activated
     */
    public record ReloadResult(
            boolean success,
            String newBundleId,
            String previousBundleId,
            boolean rolledBack
    ) {
        /** Creates a successful reload result (new bundle is active). */
        public static ReloadResult success(String newBundleId, String previousBundleId) {
            return new ReloadResult(true, newBundleId, previousBundleId, false);
        }

        /** Creates a rolled-back result (health check failed; previous bundle restored). */
        public static ReloadResult rolledBack(String newBundleId, String previousBundleId) {
            return new ReloadResult(false, newBundleId, previousBundleId, true);
        }
    }

    /**
     * Listener notified after each reload attempt (success or rollback).
     * Use to emit metrics, audit log entries, or alert on repeated rollbacks (K-06 integration).
     */
    @FunctionalInterface
    public interface ReloadEventListener {
        /**
         * @param result the outcome of the reload attempt; not null
         */
        void onReload(ReloadResult result);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void notifyListeners(ReloadResult result) {
        for (ReloadEventListener listener : listeners) {
            listener.onReload(result);
        }
    }
}
