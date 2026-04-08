/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.workflow.planning;

import com.ghatana.agent.planning.PlanGraph;
import com.ghatana.agent.planning.PlannedAction;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Default {@link PlanCompiler} implementation that validates input contracts and
 * builds a {@link PlanGraph} from an ordered list of {@link PlannedAction} steps.
 *
 * <p>This implementation is intentionally simple: it does not call an LLM.
 * Instead it validates:
 * <ul>
 *   <li>Agent ID and tenant ID are non-blank.</li>
 *   <li>Objective is non-blank.</li>
 *   <li>Any pre-supplied actions in the context have valid {@code ActionClass}.</li>
 *   <li>Privileged actions that are irreversible are flagged for approval
 *       ({@code requiresApproval=true}).</li>
 * </ul>
 *
 * <p>When no actions are supplied in context the compiler produces a single
 * {@link com.ghatana.agent.framework.governance.ActionClass#DRAFT} action whose
 * specification mirrors the objective, acting as a planning stub for downstream
 * LLM-backed implementations.
 *
 * <p>Context key: {@code "actions"} — a {@code List<PlannedAction>} to include in
 * the compiled graph. If absent or empty, a stub action is generated.
 *
 * @doc.type class
 * @doc.purpose Default plan compiler that validates contracts and builds PlanGraphs
 * @doc.layer platform
 * @doc.pattern Strategy
 */
public final class DefaultPlanCompiler implements PlanCompiler {

    private static final Logger log = LoggerFactory.getLogger(DefaultPlanCompiler.class);
    private static final String CONTEXT_KEY_ACTIONS = "actions";

    @Override
    @NotNull
    public Promise<PlanGraph> compile(
            @NotNull String agentId,
            @NotNull String tenantId,
            @NotNull String objective,
            @NotNull Map<String, Object> context) {
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(objective, "objective");
        Objects.requireNonNull(context, "context");

        if (agentId.isBlank()) {
            return Promise.ofException(new PlanCompilationException("agentId must not be blank"));
        }
        if (tenantId.isBlank()) {
            return Promise.ofException(new PlanCompilationException("tenantId must not be blank"));
        }
        if (objective.isBlank()) {
            return Promise.ofException(new PlanCompilationException("objective must not be blank"));
        }

        try {
            List<PlannedAction> actions = resolveActions(objective, context);
            PlanGraph graph = new PlanGraph(
                    UUID.randomUUID().toString(),
                    agentId,
                    objective,
                    actions,
                    Instant.now());
            log.debug("Compiled plan {} for agent {} with {} actions", graph.planId(), agentId, actions.size());
            return Promise.of(graph);
        } catch (PlanCompilationException e) {
            return Promise.ofException(e);
        } catch (IllegalArgumentException e) {
            return Promise.ofException(new PlanCompilationException(e.getMessage()));
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────

    @NotNull
    @SuppressWarnings("unchecked")
    private List<PlannedAction> resolveActions(String objective, Map<String, Object> context) {
        Object raw = context.get(CONTEXT_KEY_ACTIONS);
        if (raw == null) {
            return List.of(stubAction(objective));
        }
        if (!(raw instanceof List<?> rawList)) {
            throw new PlanCompilationException(
                    "Context key '" + CONTEXT_KEY_ACTIONS + "' must be a List<PlannedAction>");
        }
        List<PlannedAction> result = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof PlannedAction action)) {
                throw new PlanCompilationException(
                        "Each element in '" + CONTEXT_KEY_ACTIONS + "' must be a PlannedAction");
            }
            PlannedAction validated = validateAndEnrich(action);
            result.add(validated);
        }
        return result.isEmpty() ? List.of(stubAction(objective)) : List.copyOf(result);
    }

    private PlannedAction validateAndEnrich(PlannedAction action) {
        if (action.actionClass() == null) {
            throw new PlanCompilationException(
                    "actionClass must not be null", action.actionId());
        }
        // Irreversible actions must require approval
        boolean shouldRequireApproval = action.requiresApproval()
                || action.actionClass().isIrreversible();
        if (shouldRequireApproval == action.requiresApproval()) {
            return action; // no change needed
        }
        return new PlannedAction(
                action.actionId(),
                action.specification(),
                action.toolId(),
                action.dependencies(),
                action.actionClass(),
                true);
    }

    private PlannedAction stubAction(String objective) {
        return new PlannedAction(
                "plan-stub-" + UUID.randomUUID(),
                "Stub plan step for: " + objective,
                null,
                java.util.Set.of(),
                com.ghatana.agent.framework.governance.ActionClass.DRAFT,
                false);
    }
}
