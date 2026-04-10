/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.agent.planning;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Goal-directed planning agent: decomposes a high-level goal into steps and executes them.
 *
 * <h2>Planning Lifecycle</h2>
 * <ol>
 *   <li><b>PLAN</b>: Decompose the input goal into an ordered list of steps.</li>
 *   <li><b>EXECUTE</b>: Execute each step, potentially delegating to sub-agents.</li>
 *   <li><b>OBSERVE</b>: Collect step result and update plan state.</li>
 *   <li><b>REPLAN</b> (if enabled): Replan from current state when a step fails.</li>
 * </ol>
 *
 * <h2>When to Use</h2>
 * <ul>
 *   <li>Multi-step task orchestration where the full sequence is not known upfront</li>
 *   <li>YAPPC orchestrators managing long-horizon software development goals</li>
 *   <li>Any task requiring iterative reasoning + tool use (ReAct pattern)</li>
 * </ul>
 *
 * <h2>Subclassing</h2>
 * <p>Override {@link #decompose(AgentContext, Object)} to implement your planning logic.
 * Override {@link #executeStep(AgentContext, PlanStep)} to control step execution.
 * The base class handles the planning loop, retries, replanning, and observability.
 *
 * <pre>{@code
 * public class SoftwareBuildPlanner extends PlanningAgent<BuildGoal, BuildResult> {
 *
 *     @Override
 *     protected List<PlanStep> decompose(AgentContext ctx, BuildGoal goal) {
 *         return List.of(
 *             new PlanStep("analyze-requirements", goal.getRequirements()),
 *             new PlanStep("generate-design", null),
 *             new PlanStep("implement-code", null),
 *             new PlanStep("run-tests", null)
 *         );
 *     }
 *
 *     @Override
 *     protected Promise<StepResult> executeStep(AgentContext ctx, PlanStep step) {
 *         // delegate to capability agents based on step.name()
 *     }
 * }
 * }</pre>
 *
 * @param <I> input type (the goal or request)
 * @param <O> output type (the achieved result)
 *
 * @doc.type class
 * @doc.purpose Goal-directed planning agent base class
 * @doc.layer platform
 * @doc.pattern Template Method, Strategy
 * @doc.gaa.lifecycle perceive, reason, act, capture, reflect
 *
 * @author Ghatana AI Platform
 * @since 2.1.0
 */
public abstract class PlanningAgent<I, O>
        extends AbstractTypedAgent<I, O> {

    private static final Logger log = LoggerFactory.getLogger(PlanningAgent.class);

    private final AgentDescriptor descriptor;
    private volatile PlanningAgentConfig planningConfig;

    // ─────────────────────────────────────────────────────────────────────────
    // Construction
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a PlanningAgent with the given ID and default REACT subtype.
     *
     * @param agentId unique agent ID
     */
    protected PlanningAgent(@NotNull String agentId) {
        this(agentId, PlanningSubtype.REACT);
    }

    /**
     * Creates a PlanningAgent with the given ID and planning subtype.
     *
     * @param agentId  unique agent ID
     * @param subtype  the planning strategy
     */
    protected PlanningAgent(@NotNull String agentId, @NotNull PlanningSubtype subtype) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .description("Planning agent — " + subtype.name().toLowerCase() + " strategy")
                .type(AgentType.PLANNING)
                .subtype(subtype.name())
                .determinism(DeterminismGuarantee.NONE)
                .stateMutability(StateMutability.LOCAL_STATE)
                .failureMode(FailureMode.RETRY)
                .build();
        this.planningConfig = PlanningAgentConfig.builder().subtype(subtype).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TypedAgent contract
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public @NotNull AgentDescriptor descriptor() {
        return descriptor;
    }

    @Override
    protected @NotNull Promise<Void> doInitialize(@NotNull AgentConfig config) {
        this.planningConfig = PlanningAgentConfig.from(config);
        log.info("PlanningAgent [{}] initialized: subtype={}, maxSteps={}",
                descriptor.getAgentId(),
                planningConfig.getSubtype(),
                planningConfig.getMaxPlanningSteps());
        return Promise.complete();
    }

    @Override
    protected @NotNull Promise<AgentResult<O>> doProcess(@NotNull AgentContext ctx, @NotNull I input) {
        Instant start = Instant.now();
        String agentId = descriptor.getAgentId();

        log.debug("PlanningAgent [{}] starting plan for input: {}", agentId, input);

        List<PlanStep> plan = decompose(ctx, input);
        log.info("PlanningAgent [{}] decomposed goal into {} steps", agentId, plan.size());

        List<StepResult> stepResults = new ArrayList<>();
        int replanningAttempts = 0;
        int stepIndex = 0;

        while (stepIndex < plan.size()) {
            if (stepIndex >= planningConfig.getMaxPlanningSteps()) {
                log.warn("PlanningAgent [{}] reached maxPlanningSteps limit ({})",
                        agentId, planningConfig.getMaxPlanningSteps());
                break;
            }

            PlanStep step = plan.get(stepIndex);
            StepResult stepResult = executeStepWithRetry(ctx, step);
            stepResults.add(stepResult);

            if (stepResult.isSuccess()) {
                stepIndex++;
            } else if (planningConfig.isEnableReplanning()
                    && replanningAttempts < planningConfig.getMaxReplanningAttempts()) {
                replanningAttempts++;
                log.info("PlanningAgent [{}] replanning (attempt {}/{})",
                        agentId, replanningAttempts, planningConfig.getMaxReplanningAttempts());
                List<PlanStep> revisedPlan = replan(ctx, input, step, stepResults);
                if (revisedPlan.isEmpty()) {
                    return Promise.of(AgentResult.failure(
                            new RuntimeException("Replanning returned empty plan after step: " + step.name()),
                            agentId,
                            Duration.between(start, Instant.now())));
                }
                plan = revisedPlan;
                stepIndex = 0;
            } else {
                return Promise.of(AgentResult.failure(
                        new RuntimeException("Step '" + step.name() + "' failed: " + stepResult.errorMessage()),
                        agentId,
                        Duration.between(start, Instant.now())));
            }
        }

        O result = synthesizeResult(ctx, input, stepResults);
        Duration elapsed = Duration.between(start, Instant.now());
        log.info("PlanningAgent [{}] completed plan in {} steps, elapsed={}ms",
                agentId, stepResults.size(), elapsed.toMillis());

        String explanation = String.format("Plan completed successfully in %d steps (%dms): %s",
                stepResults.size(), elapsed.toMillis(),
                stepResults.stream().map(StepResult::stepName).collect(java.util.stream.Collectors.joining(" -> ")));

        return Promise.of(AgentResult.<O>builder()
                .output(result)
                .confidence(1.0)
                .status(AgentResultStatus.SUCCESS)
                .agentId(agentId)
                .processingTime(elapsed)
                .explanation(explanation)
                .build());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Abstract hooks — subclasses must implement
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Decomposes the input goal into an ordered list of plan steps.
     *
     * @param ctx   the agent context
     * @param input the goal / request
     * @return ordered list of steps; must not be empty
     */
    @NotNull
    protected abstract List<PlanStep> decompose(@NotNull AgentContext ctx, @NotNull I input);

    /**
     * Executes a single plan step.
     *
     * @param ctx  the agent context
     * @param step the step to execute
     * @return the execution result
     */
    @NotNull
    protected abstract StepResult executeStep(@NotNull AgentContext ctx, @NotNull PlanStep step);

    /**
     * Synthesizes the final output from all completed step results.
     *
     * @param ctx         the agent context
     * @param originalInput the original goal
     * @param stepResults all step results in execution order
     * @return the synthesized output
     */
    @NotNull
    protected abstract O synthesizeResult(
            @NotNull AgentContext ctx,
            @NotNull I originalInput,
            @NotNull List<StepResult> stepResults);

    // ─────────────────────────────────────────────────────────────────────────
    // Optional hooks — subclasses may override
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Revises the plan after a step failure. Called only when replanning is enabled.
     *
     * <p>Default implementation returns the original plan starting from the failed step
     * (simple retry from failure point). Override for smarter replanning logic.
     *
     * @param ctx          the agent context
     * @param originalInput the original goal
     * @param failedStep   the step that failed
     * @param completedSoFar results of steps completed before the failure
     * @return revised plan; return empty list to abort planning
     */
    @NotNull
    protected List<PlanStep> replan(
            @NotNull AgentContext ctx,
            @NotNull I originalInput,
            @NotNull PlanStep failedStep,
            @NotNull List<StepResult> completedSoFar) {
        log.debug("PlanningAgent [{}] default replan: retrying from failed step '{}'",
                descriptor.getAgentId(), failedStep.name());
        return decompose(ctx, originalInput);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal execution with retry
    // ─────────────────────────────────────────────────────────────────────────

    private StepResult executeStepWithRetry(AgentContext ctx, PlanStep step) {
        StepResult result = null;
        for (int attempt = 0; attempt <= planningConfig.getMaxStepRetries(); attempt++) {
            result = executeStep(ctx, step);
            if (result.isSuccess()) {
                return result;
            }
            if (attempt < planningConfig.getMaxStepRetries()) {
                log.warn("PlanningAgent [{}] step '{}' failed on attempt {}, retrying",
                        descriptor.getAgentId(), step.name(), attempt + 1);
            }
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Value types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Represents a single step in a planning agent's execution plan.
     *
     * @param name   unique step identifier within the plan
     * @param input  optional input data for this step (null = use previous step's output)
     * @param metadata additional context for this step (tool name, agent ID, etc.)
     */
    public record PlanStep(
            @NotNull String name,
            Object input,
            Map<String, Object> metadata) {

        /** Convenience constructor for steps without metadata. */
        public PlanStep(@NotNull String name, Object input) {
            this(name, input, Map.of());
        }
    }

    /**
     * Result of executing a single plan step.
     *
     * @param stepName     the name of the step
     * @param output       the output produced (null on failure)
     * @param errorMessage error message if the step failed (null on success)
     * @param duration     time taken for this step
     */
    public record StepResult(
            @NotNull String stepName,
            Object output,
            String errorMessage,
            @NotNull Duration duration) {

        /** Returns true if this step completed successfully. */
        public boolean isSuccess() {
            return errorMessage == null;
        }

        /** Creates a successful step result. */
        public static StepResult success(@NotNull String stepName, Object output, @NotNull Duration duration) {
            return new StepResult(stepName, output, null, duration);
        }

        /** Creates a failed step result. */
        public static StepResult failure(@NotNull String stepName, @NotNull String errorMessage, @NotNull Duration duration) {
            return new StepResult(stepName, null, errorMessage, duration);
        }
    }
}
