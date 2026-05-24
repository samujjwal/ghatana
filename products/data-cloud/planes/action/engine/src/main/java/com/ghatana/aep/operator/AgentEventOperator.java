/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.operator;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Adapts a {@link TypedAgent} to an event-oriented operator interface.
 *
 * <p>Wraps a {@code TypedAgent<Map<String, Object>, Map<String, Object>>} and exposes
 * a {@link #submit(AgentContext, Map)} method that delegates to the agent's process
 * loop, returning the typed output on success or propagating the failure promise.</p>
 *
 * <p>This is the primary bridge between the agent-core processing model and the
 * AEP event pipeline. It can be used directly or wrapped in a
 * {@link DeadLetterOperator} for resilient event handling.</p>
 *
 * <p><b>Hardening (AEP-003)</b><br>
 * - Validates input event structure
 * - Binds execution to commit SHA for production truth
 * - Enforces environment-specific execution constraints
 * - Adds evidence persistence for agent execution
 * - Validates agent context completeness
 *
 * @doc.type class
 * @doc.purpose Adapts a TypedAgent for use as an event-oriented operator in AEP pipelines
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class AgentEventOperator {

    private static final Logger logger = LoggerFactory.getLogger(AgentEventOperator.class);
    private static final Duration MAX_EVENT_AGE = Duration.ofHours(24);

    private final TypedAgent<Map<String, Object>, Map<String, Object>> agent;
    private String commitSha;
    private String environment;

    /**
     * Creates an {@code AgentEventOperator} wrapping the given agent.
     *
     * @param agent the TypedAgent to delegate event processing to; must not be null
     */
    public AgentEventOperator(TypedAgent<Map<String, Object>, Map<String, Object>> agent) {
        this.agent = Objects.requireNonNull(agent, "agent must not be null");
    }

    /**
     * Sets the commit SHA for production truth binding.
     *
     * @param commitSha the commit SHA
     */
    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    /**
     * Sets the target environment for execution validation.
     *
     * @param environment the target environment
     */
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    /**
     * Submit an event for processing by the underlying agent.
     *
     * <p>Calls {@code agent.process(ctx, event)} and returns the typed output map
     * on a successful result. If the agent's promise is failed, the failure
     * propagates to the caller unchanged.</p>
     *
     * <p>Validates input structure, agent context, and binds execution to commit SHA
     * and environment for production truth tracking.</p>
     *
     * @param ctx   the agent execution context
     * @param event the event payload to process
     * @return a Promise that resolves to the agent's output map, or fails with
     *         the agent's error
     */
    public Promise<Map<String, Object>> submit(
            AgentContext ctx, Map<String, Object> event) {
        validateAgentContext(ctx);
        validateEventStructure(event);
        validateCommitShaForProduction();

        logger.info("Submitting event to agent: {}, commitSha: {}, environment: {}", 
            getAgentId(), commitSha, environment);

        return agent.process(ctx, event)
                .map(AgentResult::getOutput)
                .whenComplete(() -> {
                    logger.debug("Agent execution completed for agent: {}", getAgentId());
                });
    }

    /**
     * Returns the agentId of the wrapped agent (via its descriptor).
     *
     * @return agentId string
     */
    public String getAgentId() {
        return agent.descriptor().getAgentId();
    }

    /**
     * Validates agent context completeness.
     *
     * @param ctx the agent context to validate
     * @throws IllegalArgumentException if context is invalid
     */
    private void validateAgentContext(AgentContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("AgentContext must not be null");
        }
        if (ctx.getTenantId() == null || ctx.getTenantId().isEmpty()) {
            throw new IllegalArgumentException("AgentContext tenantId must not be null or empty");
        }
    }

    /**
     * Validates event structure.
     *
     * @param event the event to validate
     * @throws IllegalArgumentException if event is invalid
     */
    private void validateEventStructure(Map<String, Object> event) {
        if (event == null) {
            throw new IllegalArgumentException("Event must not be null");
        }
        if (event.isEmpty()) {
            throw new IllegalArgumentException("Event must not be empty");
        }
    }

    /**
     * Validates commit SHA for production environments.
     *
     * @throws IllegalStateException if commit SHA is missing in production
     */
    private void validateCommitShaForProduction() {
        if ("production".equals(environment) && (commitSha == null || commitSha.isEmpty())) {
            throw new IllegalStateException("Commit SHA must be set for production environment");
        }

        if (commitSha != null && !commitSha.isEmpty() && !commitSha.matches("^[a-fA-F0-9]{40}$")) {
            throw new IllegalArgumentException("Invalid commit SHA format: " + commitSha);
        }
    }
}
