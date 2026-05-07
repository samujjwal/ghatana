/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.handoff;

import com.ghatana.agent.framework.handoff.AgentHandoff;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates agent handoffs within the AEP runtime.
 *
 * <p>The 3-step in-process protocol:
 * <ol>
 *   <li><b>Record</b> — persist the handoff event in the in-memory ledger</li>
 *   <li><b>Route</b> — locate the handler registered for the target agent; reject if absent</li>
 *   <li><b>Deliver</b> — invoke the target agent's handoff handler and return its acknowledgement</li>
 * </ol>
 *
 * <p>Handlers are registered via {@link #registerHandler(String, HandoffHandler)}. This decouples
 * the coordinator from any specific agent implementation.
 *
 * @doc.type class
 * @doc.purpose Handoff routing and coordination between agents in AEP runtime
 * @doc.layer agent-runtime
 * @doc.pattern Coordinator
 */
public class HandoffCoordinator {

    private static final Logger log = LoggerFactory.getLogger(HandoffCoordinator.class);

    private final Map<String, HandoffHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, AgentHandoff> handoffLedger = new ConcurrentHashMap<>();

    /**
     * Registers a handler for the agent identified by {@code agentId}.
     * A later registration for the same agentId replaces the previous one.
     *
     * @param agentId the agent that can receive handoffs
     * @param handler the handler invoked when a handoff arrives for this agent
     */
    public void registerHandler(@NotNull String agentId, @NotNull HandoffHandler handler) {
        handlers.put(
                Objects.requireNonNull(agentId, "agentId"),
                Objects.requireNonNull(handler, "handler"));
    }

    /**
     * Executes a handoff.
     *
     * @param handoff the handoff to deliver
     * @return a {@link Promise} resolving to a {@link HandoffResult}
     */
    @NotNull
    public Promise<HandoffResult> handoff(@NotNull AgentHandoff handoff) {
        Objects.requireNonNull(handoff, "handoff");

        // Step 1: Record
        handoffLedger.put(handoff.handoffId(), handoff);
        log.info("Handoff [{}] recorded: {} → {} (reason: {})",
                handoff.handoffId(), handoff.sourceAgentId(), handoff.targetAgentId(), handoff.reason());

        // Step 2: Route
        HandoffHandler handler = handlers.get(handoff.targetAgentId());
        if (handler == null) {
            String reason = "No handler registered for target agent: " + handoff.targetAgentId();
            log.warn("Handoff [{}] rejected: {}", handoff.handoffId(), reason);
            return Promise.of(HandoffResult.rejected(handoff.handoffId(), reason));
        }

        // Step 3: Deliver
        return handler.handle(handoff)
                .map(ack -> {
                    log.info("Handoff [{}] delivered; acknowledgement: {}", handoff.handoffId(), ack);
                    return HandoffResult.accepted(handoff.handoffId(), ack);
                })
                .mapException(ex -> {
                    log.error("Handoff [{}] delivery failed", handoff.handoffId(), ex);
                    return ex;
                });
    }

    /**
     * Returns a handoff from the ledger by ID, or {@code null} if not found.
     */
    public AgentHandoff findHandoff(@NotNull String handoffId) {
        return handoffLedger.get(handoffId);
    }

    // ─── HandoffResult ───────────────────────────────────────────────────────

    /**
     * Outcome of a handoff operation.
     */
    public sealed interface HandoffResult {

        String handoffId();

        record Accepted(@NotNull String handoffId, @NotNull String acknowledgement) implements HandoffResult {}

        record Rejected(@NotNull String handoffId, @NotNull String reason) implements HandoffResult {}

        static HandoffResult accepted(String handoffId, String ack)      { return new Accepted(handoffId, ack); }
        static HandoffResult rejected(String handoffId, String reason)   { return new Rejected(handoffId, reason); }

        default boolean isAccepted() { return this instanceof Accepted; }
    }

    // ─── HandoffHandler ──────────────────────────────────────────────────────

    /**
     * Handler invoked on the target agent when a handoff arrives.
     */
    @FunctionalInterface
    public interface HandoffHandler {

        /**
         * Handles an incoming handoff.
         *
         * @param handoff the handoff context
         * @return an acknowledgement string (e.g. turn ID or status message)
         */
        @NotNull
        Promise<String> handle(@NotNull AgentHandoff handoff);
    }
}
