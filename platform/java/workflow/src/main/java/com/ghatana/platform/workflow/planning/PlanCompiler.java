/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.planning;

import com.ghatana.agent.planning.PlanGraph;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Compiles a natural-language or structured objective into an executable {@link PlanGraph}.
 *
 * <p>Implementations may delegate to an LLM, a rules engine, a retrieval-augmented
 * planner, or any combination thereof. Callers should treat the returned graph as
 * immutable and validate it before executing.
 *
 * <p>Compilation is performed asynchronously using ActiveJ {@link Promise}.
 *
 * @doc.type interface
 * @doc.purpose Contract for compiling agent objectives into PlanGraphs
 * @doc.layer platform
 * @doc.pattern SPI
 */
public interface PlanCompiler {

    /**
     * Compiles an objective into a {@link PlanGraph} for a given agent and tenant.
     *
     * @param agentId   the agent that will execute the plan; must not be blank
     * @param tenantId  the owning tenant; must not be blank
     * @param objective human-readable objective or prompt to plan for; must not be blank
     * @param context   optional key-value context forwarded to the planning backend (nullable values allowed)
     * @return a {@code Promise} of the compiled {@link PlanGraph}
     * @throws PlanCompilationException if compilation fails synchronously
     */
    @NotNull
    Promise<PlanGraph> compile(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String objective,
            @NotNull Map<String, Object> context);

    /**
     * Convenience overload with no additional context.
     *
     * @param agentId   the agent that will execute the plan
     * @param tenantId  the owning tenant
     * @param objective the objective to plan for
     * @return a {@code Promise} of the compiled {@link PlanGraph}
     */
    @NotNull
    default Promise<PlanGraph> compile(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String objective) {
        return compile(agentId, tenantId, objective, Map.of());
    }
}
