package com.ghatana.products.yappc.domain.workflow;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe registry for cooperative workflow cancellation signals.
 *
 * <p>Agents running inside a workflow should periodically call
 * {@link #isCancellationRequested(String, String)} at each step boundary.
 * When cancellation is detected, the agent must exit and call
 * {@link #notifyExit(String, String)} to release the signal.
 *
 * <p>If the agent does not call {@link #notifyExit} within the
 * {@link #hardKillTimeout()}, {@link #isHardKillRequired(String, String)}
 * returns {@code true} — allowing callers to force-cancel and record the
 * method as {@code "hard_kill"}.
 *
 * @doc.type class
 * @doc.purpose Cooperative workflow cancellation signal registry
 * @doc.layer product
 * @doc.pattern Registry
 */
public final class WorkflowCancellationRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowCancellationRegistry.class);

    /** Default cooperative-exit window before a hard-kill is required. */
    public static final Duration DEFAULT_HARD_KILL_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Immutable snapshot of a cancellation signal for a single workflow execution.
     *
     * @param requestedAt  Wall-clock time of the first cancellation signal.
     * @param attemptCount Number of times cancellation has been requested (idempotency guard).
     */
    record CancellationSignal(
        @NotNull Instant requestedAt,
        @NotNull AtomicInteger attemptCount
    ) {}

    private final ConcurrentHashMap<String, CancellationSignal> signals = new ConcurrentHashMap<>();
    private final Duration hardKillTimeout;

    /** Creates a registry with the default 30-second hard-kill timeout. */
    public WorkflowCancellationRegistry() {
        this(DEFAULT_HARD_KILL_TIMEOUT);
    }

    /**
     * Creates a registry with a custom hard-kill timeout.
     *
     * @param hardKillTimeout Maximum cooperative-exit window.
     */
    public WorkflowCancellationRegistry(@NotNull Duration hardKillTimeout) {
        this.hardKillTimeout = Objects.requireNonNull(hardKillTimeout, "hardKillTimeout");
    }

    // ==================== SIGNAL MANAGEMENT ====================

    /**
     * Signals cancellation for the given workflow.
     *
     * <p>Idempotent: if a signal already exists, the attempt counter is incremented
     * rather than resetting the {@code requestedAt} timestamp (so the hard-kill
     * window is measured from the first signal, not the most recent one).
     *
     * @param workflowId Workflow identifier.
     * @param tenantId   Tenant identifier.
     * @return The total number of signal attempts for this workflow (1-based).
     */
    public int signal(@NotNull String workflowId, @NotNull String tenantId) {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(tenantId, "tenantId");

        String k = registryKey(workflowId, tenantId);
        CancellationSignal existing = signals.get(k);
        if (existing != null) {
            int count = existing.attemptCount().incrementAndGet();
            LOG.info("Duplicate cancel signal for workflow={} tenant={} attempt={}", workflowId, tenantId, count);
            return count;
        }
        signals.put(k, new CancellationSignal(Instant.now(), new AtomicInteger(1)));
        LOG.info("Cancellation signalled for workflow={} tenant={}", workflowId, tenantId);
        return 1;
    }

    /**
     * Returns {@code true} if a cancellation has been requested for this workflow.
     *
     * <p>Running agents must poll this at each async step boundary and exit
     * cooperatively when it returns {@code true}.
     *
     * @param workflowId Workflow identifier.
     * @param tenantId   Tenant identifier.
     * @return {@code true} if cancellation has been requested.
     */
    public boolean isCancellationRequested(@NotNull String workflowId, @NotNull String tenantId) {
        return signals.containsKey(registryKey(workflowId, tenantId));
    }

    /**
     * Returns the total number of cancellation signal attempts, or {@code 0} if not signalled.
     *
     * @param workflowId Workflow identifier.
     * @param tenantId   Tenant identifier.
     * @return Attempt count (0 if no signal).
     */
    public int attemptCount(@NotNull String workflowId, @NotNull String tenantId) {
        CancellationSignal sig = signals.get(registryKey(workflowId, tenantId));
        return sig != null ? sig.attemptCount().get() : 0;
    }

    /**
     * Returns the {@link Instant} at which the first cancellation signal was recorded,
     * or {@code null} if no signal is currently registered.
     *
     * @param workflowId Workflow identifier.
     * @param tenantId   Tenant identifier.
     * @return Signal timestamp, or {@code null}.
     */
    @Nullable
    public Instant signalledAt(@NotNull String workflowId, @NotNull String tenantId) {
        CancellationSignal sig = signals.get(registryKey(workflowId, tenantId));
        return sig != null ? sig.requestedAt() : null;
    }

    /**
     * Returns {@code true} if the {@link #hardKillTimeout()} has elapsed since the
     * cancellation was first signalled for this workflow.
     *
     * <p>Callers should use this to decide whether to record the cancel method as
     * {@code "hard_kill"} vs. {@code "cooperative"}.
     *
     * @param workflowId Workflow identifier.
     * @param tenantId   Tenant identifier.
     * @return {@code true} if the cooperative-exit window has expired.
     */
    public boolean isHardKillRequired(@NotNull String workflowId, @NotNull String tenantId) {
        CancellationSignal sig = signals.get(registryKey(workflowId, tenantId));
        if (sig == null) {
            return false;
        }
        return Duration.between(sig.requestedAt(), Instant.now()).compareTo(hardKillTimeout) >= 0;
    }

    // ==================== EXIT NOTIFICATION ====================

    /**
     * Notifies that the agent has exited and the cancellation signal can be released.
     *
     * <p>This must be called after the workflow transitions to a terminal state so
     * that the registry does not retain stale entries indefinitely.
     *
     * @param workflowId Workflow identifier.
     * @param tenantId   Tenant identifier.
     */
    public void notifyExit(@NotNull String workflowId, @NotNull String tenantId) {
        Objects.requireNonNull(workflowId, "workflowId");
        Objects.requireNonNull(tenantId, "tenantId");

        CancellationSignal removed = signals.remove(registryKey(workflowId, tenantId));
        if (removed != null) {
            LOG.info("Cancellation signal released for workflow={} tenant={} totalAttempts={}",
                workflowId, tenantId, removed.attemptCount().get());
        }
    }

    // ==================== METADATA ====================

    /**
     * Returns the configured hard-kill timeout.
     *
     * @return Hard-kill timeout duration.
     */
    @NotNull
    public Duration hardKillTimeout() {
        return hardKillTimeout;
    }

    /**
     * Returns the number of workflows currently registered in this registry.
     * Primarily useful for diagnostics and testing.
     *
     * @return Number of active cancellation signals.
     */
    public int activeSignalCount() {
        return signals.size();
    }

    // ==================== INTERNAL ====================

    private static String registryKey(String workflowId, String tenantId) {
        return tenantId + ":" + workflowId;
    }
}
