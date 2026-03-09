package com.ghatana.virtualorg.framework.norm;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Monitors and enforces organizational norms in real-time.
 *
 * <p><b>Purpose</b><br>
 * The NormativeMonitor is the "police" of the organization. It watches
 * for norm violations and triggers appropriate responses (alerts, penalties,
 * or automatic remediation).
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * NormativeMonitor monitor = new NormativeMonitor(normRegistry);
 * monitor.addViolationListener(violation -> {
 *     alertService.send("Norm violation: " + violation.description());
 * });
 *
 * // Track an obligation
 * monitor.trackObligation("agent-1", "dept-1", respondToP1Norm);
 *
 * // Report action completion
 * monitor.reportAction("agent-1", "acknowledge", context);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Real-time norm monitoring and enforcement
 * @doc.layer platform
 * @doc.pattern Observer
 */
public class NormativeMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(NormativeMonitor.class);

    private final NormRegistry normRegistry;
    private final Map<String, TrackedObligation> activeObligations = new ConcurrentHashMap<>();
    private final List<Consumer<NormViolation>> violationListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<NormViolation> violationHistory = Collections.synchronizedList(new ArrayList<>());
    private final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
    private java.util.concurrent.ScheduledFuture<?> monitoringTask;

    public NormativeMonitor(NormRegistry normRegistry) {
        this.normRegistry = normRegistry;
    }

    /**
     * Starts the automatic monitoring of deadlines.
     *
     * @param interval the check interval
     */
    public void startMonitoring(Duration interval) {
        if (monitoringTask != null && !monitoringTask.isCancelled()) {
            return;
        }
        monitoringTask = scheduler.scheduleAtFixedRate(
                this::checkDeadlines,
                interval.toMillis(),
                interval.toMillis(),
                java.util.concurrent.TimeUnit.MILLISECONDS
        );
        LOG.info("Normative monitoring started with interval: {}", interval);
    }

    /**
     * Stops the automatic monitoring.
     */
    public void stopMonitoring() {
        if (monitoringTask != null) {
            monitoringTask.cancel(false);
            monitoringTask = null;
            LOG.info("Normative monitoring stopped");
        }
    }

    /**
     * Adds a listener for norm violations.
     *
     * @param listener the listener to add
     */
    public void addViolationListener(Consumer<NormViolation> listener) {
        violationListeners.add(listener);
    }

    /**
     * Tracks an obligation that must be fulfilled.
     *
     * @param agentId the agent responsible
     * @param departmentId the department
     * @param norm the obligation norm
     * @return the tracking ID
     */
    public String trackObligation(String agentId, String departmentId, Norm norm) {
        if (!norm.isObligation()) {
            throw new IllegalArgumentException("Norm must be an obligation");
        }

        String trackingId = UUID.randomUUID().toString();
        Instant deadline = norm.deadline()
                .map(d -> Instant.now().plus(d))
                .orElse(Instant.MAX);

        TrackedObligation tracked = new TrackedObligation(
                trackingId, agentId, departmentId, norm, deadline, false
        );
        activeObligations.put(trackingId, tracked);

        LOG.debug("Tracking obligation '{}' for agent '{}', deadline: {}",
                norm.id(), agentId, deadline);

        return trackingId;
    }

    /**
     * Reports that an action was performed by an agent.
     *
     * @param agentId the agent that performed the action
     * @param action the action that was performed
     * @param context additional context
     * @return promise with any violations triggered
     */
    public Promise<List<NormViolation>> reportAction(String agentId, String action, Map<String, Object> context) {
        List<NormViolation> violations = new ArrayList<>();

        // Check for prohibition violations
        return normRegistry.getProhibitions(null).map(prohibitions -> {
            for (Norm prohibition : prohibitions) {
                if (prohibition.action().equals(action)) {
                    NormViolation violation = NormViolation.brokenProhibition(
                            prohibition, agentId, context.getOrDefault("departmentId", "unknown").toString()
                    );
                    recordViolation(violation);
                    violations.add(violation);
                }
            }

            // Fulfill any matching obligations
            activeObligations.values().stream()
                    .filter(o -> o.agentId.equals(agentId) && o.norm.action().equals(action) && !o.fulfilled)
                    .forEach(o -> {
                        activeObligations.remove(o.trackingId);
                        LOG.debug("Obligation '{}' fulfilled by agent '{}'", o.norm.id(), agentId);
                    });

            return violations;
        });
    }

    /**
     * Checks for deadline violations on active obligations.
     *
     * @return promise with list of new violations
     */
    public Promise<List<NormViolation>> checkDeadlines() {
        List<NormViolation> violations = new ArrayList<>();
        Instant now = Instant.now();

        Iterator<Map.Entry<String, TrackedObligation>> it = activeObligations.entrySet().iterator();
        while (it.hasNext()) {
            TrackedObligation tracked = it.next().getValue();
            if (!tracked.fulfilled && now.isAfter(tracked.deadline)) {
                NormViolation violation = NormViolation.missedObligation(
                        tracked.norm, tracked.agentId, tracked.departmentId
                );
                recordViolation(violation);
                violations.add(violation);
                it.remove();
            }
        }

        return Promise.of(violations);
    }

    /**
     * Gets the violation history.
     *
     * @param limit maximum number of entries
     * @return list of recent violations
     */
    public List<NormViolation> getViolationHistory(int limit) {
        int start = Math.max(0, violationHistory.size() - limit);
        return new ArrayList<>(violationHistory.subList(start, violationHistory.size()));
    }

    /**
     * Gets violation count by agent.
     *
     * @param agentId the agent ID
     * @return number of violations
     */
    public long getViolationCount(String agentId) {
        return violationHistory.stream()
                .filter(v -> v.agentId().equals(agentId))
                .count();
    }

    private void recordViolation(NormViolation violation) {
        violationHistory.add(violation);
        LOG.warn("Norm violation detected: {} by agent '{}'",
                violation.description(), violation.agentId());

        for (Consumer<NormViolation> listener : violationListeners) {
            try {
                listener.accept(violation);
            } catch (Exception e) {
                LOG.error("Violation listener threw exception", e);
            }
        }
    }

    /**
     * Internal record for tracking active obligations.
     */
    private record TrackedObligation(
            String trackingId,
            String agentId,
            String departmentId,
            Norm norm,
            Instant deadline,
            boolean fulfilled
    ) {}
}
