/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.rules.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Canary deployment service for OPA rule packs (STORY-K03-008).
 *
 * <p>Enables zero-risk progressive rollout of new bundle versions:
 * <ol>
 *   <li>Start a canary with a target percentage ({@link #startCanary}).</li>
 *   <li>Route requests: {@link #shouldUseCanary} returns {@code true} with probability
 *       equal to the configured percentage (deterministic when a seeded {@link Random}
 *       is injected).</li>
 *   <li>Record each shadow decision pair via {@link #recordDecision}.</li>
 *   <li>Evaluate mismatch rate: {@link #getMismatchRate}.</li>
 *   <li>Based on the rate: {@link #promote} or {@link #rollback}.</li>
 * </ol>
 *
 * <h2>Shadow mode</h2>
 * <p>Both bundles evaluate independently; only the primary (stable) bundle result
 * is served to the caller. The canary result is compared in-shadow to accumulate
 * the mismatch statistic. No caller traffic is affected until promotion.
 *
 * <h2>Promotion / rollback</h2>
 * <ul>
 *   <li><b>Promote</b>: if mismatch rate ≤ threshold, the canary bundle is activated
 *       in the store via {@link PolicyBundleService#activateBundle}.</li>
 *   <li><b>Rollback</b>: transitions state to {@code ROLLED_BACK} without changing
 *       the active bundle in the store.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Canary rollout for OPA rule packs with shadow comparison and auto-rollback (K03-008)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PolicyBundleCanaryService {

    private final PolicyBundleService bundleService;
    private final Random random;

    /** Currently active canary bundle id; null when not in canary state. */
    private volatile String canaryBundleId;

    /** Routing percentage in [0, 100]. */
    private volatile double canaryPercentage;

    /** Current canary lifecycle state. */
    private volatile CanaryState state = CanaryState.NOT_STARTED;

    /** Mismatch threshold above which automatic rollback is warranted. */
    private volatile double mismatchThreshold = 0.05; // 5%

    /** Total shadow comparisons recorded. */
    private final AtomicInteger totalDecisions = new AtomicInteger(0);

    /** Number of decisions where active ≠ canary. */
    private final AtomicInteger mismatchCount = new AtomicInteger(0);

    private final List<CanaryEventListener> listeners = new ArrayList<>();

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Creates a canary service backed by the given bundle service and a platform-default
     * {@link Random} instance.
     *
     * @param bundleService owning service that manages activation in the store; not null
     */
    public PolicyBundleCanaryService(PolicyBundleService bundleService) {
        this(bundleService, new Random());
    }

    /**
     * Creates a canary service with an injectable {@link Random} for deterministic testing.
     *
     * @param bundleService owning service; not null
     * @param random        random source controlling canary routing; not null
     */
    public PolicyBundleCanaryService(PolicyBundleService bundleService, Random random) {
        this.bundleService = Objects.requireNonNull(bundleService, "bundleService");
        this.random        = Objects.requireNonNull(random, "random");
    }

    // ─── Canary management ────────────────────────────────────────────────────

    /**
     * Begins a canary rollout for the given bundle at the requested traffic percentage.
     *
     * <p>Resets decision counters from any previous run.
     *
     * @param newCanaryBundleId ID of the bundle to canary; must exist in the store
     * @param percentage        fraction of requests to route to the canary (0–100 inclusive)
     * @throws IllegalArgumentException if percentage is outside [0, 100]
     * @throws IllegalStateException    if a canary is already active
     */
    public void startCanary(String newCanaryBundleId, double percentage) {
        Objects.requireNonNull(newCanaryBundleId, "newCanaryBundleId");
        if (percentage < 0 || percentage > 100) {
            throw new IllegalArgumentException("percentage must be in [0, 100], got: " + percentage);
        }
        if (state == CanaryState.ACTIVE) {
            throw new IllegalStateException("A canary is already active; rollback or promote first.");
        }
        this.canaryBundleId  = newCanaryBundleId;
        this.canaryPercentage = percentage;
        this.totalDecisions.set(0);
        this.mismatchCount.set(0);
        this.state           = CanaryState.ACTIVE;
    }

    /**
     * Returns whether the current request should be evaluated by the canary bundle.
     *
     * <p>The probability is equal to {@code canaryPercentage / 100}. For deterministic
     * testing, inject a seeded {@link Random} via
     * {@link #PolicyBundleCanaryService(PolicyBundleService, Random)}.
     *
     * @return {@code true} if the caller should evaluate the canary bundle in shadow mode
     * @throws IllegalStateException if no canary is active
     */
    public boolean shouldUseCanary() {
        assertActive();
        return random.nextDouble() * 100.0 < canaryPercentage;
    }

    /**
     * Records a shadow comparison decision.
     *
     * <p>Both {@code activeDecision} and {@code canaryDecision} should be the policy
     * evaluation outcome returned by each respective bundle for the same request.
     * A mismatch is counted when the two differ (case-sensitive comparison).
     *
     * @param activeDecision  decision from the currently active (stable) bundle
     * @param canaryDecision  decision from the canary bundle
     * @throws IllegalStateException if no canary is active
     */
    public void recordDecision(String activeDecision, String canaryDecision) {
        Objects.requireNonNull(activeDecision, "activeDecision");
        Objects.requireNonNull(canaryDecision, "canaryDecision");
        assertActive();
        totalDecisions.incrementAndGet();
        if (!activeDecision.equals(canaryDecision)) {
            mismatchCount.incrementAndGet();
        }
    }

    /**
     * Returns the current mismatch rate as a fraction in [0.0, 1.0].
     *
     * <p>Returns {@code 0.0} when no decisions have been recorded yet.
     *
     * @throws IllegalStateException if no canary has been started
     */
    public double getMismatchRate() {
        if (state == CanaryState.NOT_STARTED) {
            throw new IllegalStateException("No canary has been started.");
        }
        int total = totalDecisions.get();
        if (total == 0) return 0.0;
        return (double) mismatchCount.get() / total;
    }

    /**
     * Sets the mismatch threshold used by automatic promotion checks.
     *
     * <p>Default is {@code 0.05} (5 %). Override before calling {@link #promote}.
     *
     * @param threshold fraction in [0.0, 1.0]
     */
    public void setMismatchThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("threshold must be in [0.0, 1.0], got: " + threshold);
        }
        this.mismatchThreshold = threshold;
    }

    // ─── Promotion and rollback ───────────────────────────────────────────────

    /**
     * Promotes the canary bundle to active if the mismatch rate is within threshold.
     *
     * <p>If mismatch rate ≤ threshold: activates the canary bundle in the store,
     * transitions state to {@code PROMOTED}, and notifies listeners.
     *
     * <p>If mismatch rate exceeds threshold: automatically rolls back (equivalent to
     * calling {@link #rollback}) and returns a {@code ROLLED_BACK} result.
     *
     * @return canary outcome
     * @throws IllegalStateException if no canary is active
     */
    public CanaryResult promote() {
        assertActive();
        double rate = getMismatchRate();
        if (rate <= mismatchThreshold) {
            bundleService.activateBundle(canaryBundleId);
            state = CanaryState.PROMOTED;
            CanaryResult result = new CanaryResult(CanaryState.PROMOTED, canaryBundleId, rate,
                    mismatchThreshold, totalDecisions.get(), mismatchCount.get());
            notifyListeners(result);
            return result;
        }
        // Auto-rollback: mismatch rate exceeds threshold
        return rollback();
    }

    /**
     * Rolls back the canary deployment without activating the canary bundle.
     *
     * <p>Transitions state to {@code ROLLED_BACK} and notifies listeners.
     *
     * @return canary outcome with {@link CanaryState#ROLLED_BACK}
     * @throws IllegalStateException if no canary is active
     */
    public CanaryResult rollback() {
        assertActive();
        double rate = getMismatchRate();
        state = CanaryState.ROLLED_BACK;
        CanaryResult result = new CanaryResult(CanaryState.ROLLED_BACK, canaryBundleId, rate,
                mismatchThreshold, totalDecisions.get(), mismatchCount.get());
        notifyListeners(result);
        return result;
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    /**
     * Registers a listener to receive canary outcome notifications.
     *
     * @param listener recipient; not null
     */
    public void addCanaryEventListener(CanaryEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ─── State accessors ─────────────────────────────────────────────────────

    /** Returns the current canary lifecycle state. */
    public CanaryState getState() { return state; }

    /** Returns the canary bundle ID currently under evaluation, or null if not started. */
    public String getCanaryBundleId() { return canaryBundleId; }

    /** Returns the percentage of traffic routed to the canary (0–100). */
    public double getCanaryPercentage() { return canaryPercentage; }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void assertActive() {
        if (state != CanaryState.ACTIVE) {
            throw new IllegalStateException("No active canary; state=" + state);
        }
    }

    private void notifyListeners(CanaryResult result) {
        listeners.forEach(l -> l.onCanaryResult(result));
    }

    // ─── Inner types ──────────────────────────────────────────────────────────

    /**
     * Canary deployment lifecycle states.
     */
    public enum CanaryState {
        /** No canary has been started. */
        NOT_STARTED,
        /** Canary is running; decisions are being accumulated. */
        ACTIVE,
        /** Canary was promoted: new bundle is now the active bundle. */
        PROMOTED,
        /** Canary was rolled back: stable bundle remains active. */
        ROLLED_BACK
    }

    /**
     * Immutable outcome of a canary promotion or rollback.
     *
     * @param state            final lifecycle state
     * @param canaryBundleId   bundle ID that was under evaluation
     * @param mismatchRate     observed fraction of mismatched decisions (0–1)
     * @param threshold        configured mismatch threshold at decision time
     * @param totalDecisions   total shadow comparisons recorded
     * @param mismatchCount    number of mismatched comparisons
     */
    public record CanaryResult(
            CanaryState state,
            String canaryBundleId,
            double mismatchRate,
            double threshold,
            int totalDecisions,
            int mismatchCount
    ) {
        /** @return true if the canary was promoted to active. */
        public boolean promoted() { return state == CanaryState.PROMOTED; }
        /** @return true if the canary was rolled back. */
        public boolean rolledBack() { return state == CanaryState.ROLLED_BACK; }
    }

    /**
     * Listener notified on every canary promotion or rollback.
     */
    @FunctionalInterface
    public interface CanaryEventListener {
        /** Invoked when a canary completes (promoted or rolled back). */
        void onCanaryResult(CanaryResult result);
    }
}
