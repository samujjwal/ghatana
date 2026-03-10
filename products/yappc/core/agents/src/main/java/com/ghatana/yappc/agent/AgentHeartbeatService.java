/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Core Agents — Agent Heartbeat Service
 */
package com.ghatana.yappc.agent;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodic heartbeat service that monitors all agents registered in
 * {@link YAPPCAgentRegistry} and updates their health status.
 *
 * <p>Runs on the ActiveJ event loop to remain non-blocking. Every
 * {@code heartbeatIntervalMs} milliseconds the service:
 * <ol>
 *   <li>Snapshots the current {@link YAPPCAgentRegistry.AgentStatus} of every agent.</li>
 *   <li>Records each agent's last-seen timestamp.</li>
 *   <li>Logs a warning for any agent that has not transitioned out of
 *       {@code FAILED} after three consecutive cycles.</li>
 * </ol>
 *
 * <p>This service is an {@link io.activej.net.socket.tcp.ReactiveSocketServer EventloopService}
 * analogue — it must be started via {@link #start()} and cleanly stopped via
 * {@link #stop()}.
 *
 * @doc.type class
 * @doc.purpose Periodic health-monitoring service for all YAPPC agents
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public class AgentHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(AgentHeartbeatService.class);

    /** Default heartbeat period: 30 seconds. */
    public static final long DEFAULT_INTERVAL_MS = 30_000L;

    /** Number of consecutive FAILED cycles before we escalate to a WARN-level alert. */
    private static final int FAILURE_ALERT_THRESHOLD = 3;

    private final YAPPCAgentRegistry registry;
    private final Eventloop eventloop;
    private final long heartbeatIntervalMs;

    /** Tracks how many consecutive heartbeat cycles each agent has been in FAILED state. */
    private final Map<String, Integer> consecutiveFailures = new ConcurrentHashMap<>();

    /** Wall-clock time of the last successful heartbeat cycle. */
    private volatile Instant lastHeartbeat;

    private volatile boolean running;

    // ─── Construction ─────────────────────────────────────────────────────────

    /**
     * Creates a heartbeat service with the default 30-second interval.
     *
     * @param registry  the agent registry to monitor
     * @param eventloop the ActiveJ eventloop on which to schedule ticks
     */
    public AgentHeartbeatService(
            @NotNull YAPPCAgentRegistry registry,
            @NotNull Eventloop eventloop) {
        this(registry, eventloop, DEFAULT_INTERVAL_MS);
    }

    /**
     * Creates a heartbeat service with a custom interval.
     *
     * @param registry            the agent registry to monitor
     * @param eventloop           the ActiveJ eventloop on which to schedule ticks
     * @param heartbeatIntervalMs poll interval in milliseconds (must be &gt; 0)
     */
    public AgentHeartbeatService(
            @NotNull YAPPCAgentRegistry registry,
            @NotNull Eventloop eventloop,
            long heartbeatIntervalMs) {
        if (heartbeatIntervalMs <= 0) {
            throw new IllegalArgumentException("heartbeatIntervalMs must be positive, got: " + heartbeatIntervalMs);
        }
        this.registry = registry;
        this.eventloop = eventloop;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Starts the heartbeat service.
     *
     * <p>Must be called from within the eventloop thread or before the
     * eventloop begins processing (e.g., in an {@code @Provides}
     * sequence before the server starts).
     *
     * @return a completed Promise (the service starts asynchronously)
     */
    @NotNull
    public Promise<Void> start() {
        log.info("AgentHeartbeatService starting with interval={}ms, {} agents registered",
                heartbeatIntervalMs, registry.getAgentCount());
        running = true;
        scheduleNextTick();
        return Promise.complete();
    }

    /**
     * Stops the heartbeat service gracefully.
     *
     * @return a completed Promise once the flag is cleared
     */
    @NotNull
    public Promise<Void> stop() {
        log.info("AgentHeartbeatService stopping");
        running = false;
        return Promise.complete();
    }

    // ─── Heartbeat tick ───────────────────────────────────────────────────────

    private void scheduleNextTick() {
        eventloop.scheduleBackground(
                eventloop.currentTimeMillis() + heartbeatIntervalMs,
                this::tick);
    }

    private void tick() {
        if (!running) {
            return;
        }

        try {
            performHeartbeat();
        } catch (Exception ex) {
            log.error("Heartbeat tick failed unexpectedly", ex);
        } finally {
            scheduleNextTick();
        }
    }

    private void performHeartbeat() {
        Map<String, YAPPCAgentRegistry.AgentStatus> statuses = registry.getHealthStatus();
        lastHeartbeat = Instant.now();

        if (statuses.isEmpty()) {
            log.debug("AgentHeartbeatService: no agents registered");
            return;
        }

        int healthy = 0;
        int failed = 0;

        for (Map.Entry<String, YAPPCAgentRegistry.AgentStatus> entry : statuses.entrySet()) {
            String agentId = entry.getKey();
            YAPPCAgentRegistry.AgentStatus status = entry.getValue();

            if (status == YAPPCAgentRegistry.AgentStatus.FAILED
                    || status == YAPPCAgentRegistry.AgentStatus.STOPPED) {
                int count = consecutiveFailures.merge(agentId, 1, Integer::sum);
                failed++;

                if (count >= FAILURE_ALERT_THRESHOLD) {
                    log.warn("AgentHeartbeatService: agent {} has been in {} state for {} consecutive cycles",
                            agentId, status, count);
                }
            } else {
                consecutiveFailures.remove(agentId);
                if (status == YAPPCAgentRegistry.AgentStatus.READY) {
                    healthy++;
                }
            }
        }

        log.info("AgentHeartbeatService: heartbeat complete — total={}, healthy={}, failed/stopped={}",
                statuses.size(), healthy, failed);
    }

    // ─── Observable state ─────────────────────────────────────────────────────

    /**
     * Returns the wall-clock instant of the last completed heartbeat cycle,
     * or {@code null} if no cycle has completed yet.
     *
     * @return last heartbeat time
     */
    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    /**
     * Whether the service is currently active.
     *
     * @return {@code true} if started and not stopped
     */
    public boolean isRunning() {
        return running;
    }
}
