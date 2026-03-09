/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import com.ghatana.agent.*;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Deterministic agent — produces the same output for the same input, guaranteed.
 *
 * <p>Supports multiple evaluation strategies via {@link DeterministicSubtype}:
 * <ul>
 *   <li><b>RULE_BASED</b> — condition → action rule evaluation</li>
 *   <li><b>THRESHOLD</b> — numeric threshold with hysteresis</li>
 *   <li><b>FSM</b> — finite-state-machine transitions</li>
 *   <li><b>EXACT_MATCH</b> — hash-lookup on a field value</li>
 *   <li><b>PATTERN</b> — delegate to the NFA pattern detection subsystem</li>
 * </ul>
 *
 * <p>Characteristics:
 * <ul>
 *   <li>Sub-millisecond latency (no external calls or ML inference)</li>
 *   <li>100% reproducible output</li>
 *   <li>Zero per-invocation cost</li>
 *   <li>Full auditability — every decision traceable to a rule/threshold/transition</li>
 * </ul>
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Agent with deterministic rule-based decision making
 * @doc.layer platform
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class DeterministicAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private final AgentDescriptor descriptor;

    // Strategy components (initialised from config)
    private volatile RuleEngine ruleEngine;
    private volatile List<ThresholdEvaluator> thresholdEvaluators;
    private volatile FiniteStateMachine fsm;
    private volatile DeterministicAgentConfig detConfig;

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public DeterministicAgent(@NotNull String agentId) {
        this(agentId, null, null);
    }

    public DeterministicAgent(@NotNull String agentId,
                              String name,
                              String description) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(name != null ? name : agentId)
                .description(description != null ? description : "Deterministic agent")
                .version("1.0.0")
                .type(AgentType.DETERMINISTIC)
                .determinism(DeterminismGuarantee.FULL)
                .stateMutability(StateMutability.STATELESS)
                .failureMode(FailureMode.FAIL_FAST)
                .latencySla(Duration.ofMillis(1))
                .throughputTarget(100_000)
                .build();
    }

    public DeterministicAgent(@NotNull AgentDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TypedAgent Contract
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    @NotNull
    public AgentDescriptor descriptor() { return descriptor; }

    @Override
    @NotNull
    protected Promise<Void> doInitialize(@NotNull AgentConfig config) {
        if (!(config instanceof DeterministicAgentConfig dc)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Expected DeterministicAgentConfig but got " + config.getClass().getSimpleName()));
        }

        this.detConfig = dc;

        switch (dc.getSubtype()) {
            case RULE_BASED -> {
                this.ruleEngine = RuleEngine.builder()
                        .rules(dc.getRules())
                        .evaluateAll(dc.isEvaluateAllRules())
                        .build();
                log.info("Initialized RULE_BASED agent: {} with {} rules",
                        descriptor.getAgentId(), dc.getRules().size());
            }
            case THRESHOLD -> {
                this.thresholdEvaluators = new ArrayList<>(dc.getThresholds());
                log.info("Initialized THRESHOLD agent: {} with {} thresholds",
                        descriptor.getAgentId(), dc.getThresholds().size());
            }
            case FSM -> {
                if (dc.getFsmDefinition() == null) {
                    return Promise.ofException(new IllegalArgumentException(
                            "FSM subtype requires fsmDefinition"));
                }
                this.fsm = new FiniteStateMachine(dc.getFsmDefinition());
                log.info("Initialized FSM agent: {} with {} states, {} transitions",
                        descriptor.getAgentId(),
                        dc.getFsmDefinition().getStates().size(),
                        dc.getFsmDefinition().getTransitions().size());
            }
            case EXACT_MATCH -> {
                if (dc.getExactMatchField() == null) {
                    return Promise.ofException(new IllegalArgumentException(
                            "EXACT_MATCH subtype requires exactMatchField"));
                }
                log.info("Initialized EXACT_MATCH agent: {} on field '{}' with {} entries",
                        descriptor.getAgentId(), dc.getExactMatchField(),
                        dc.getExactMatchTable().size());
            }
            case PATTERN -> {
                log.info("Initialized PATTERN agent: {} (delegates to NFA subsystem)",
                        descriptor.getAgentId());
            }
        }

        return Promise.complete();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {

        if (detConfig == null) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Agent not configured"),
                    descriptor.getAgentId(), Duration.ZERO));
        }

        return switch (detConfig.getSubtype()) {
            case RULE_BASED -> evaluateRules(ctx, input);
            case THRESHOLD  -> evaluateThresholds(ctx, input);
            case FSM        -> evaluateFSM(ctx, input);
            case EXACT_MATCH-> evaluateExactMatch(ctx, input);
            case PATTERN    -> evaluatePattern(ctx, input);
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Strategy Implementations
    // ═══════════════════════════════════════════════════════════════════════════

    private Promise<AgentResult<Map<String, Object>>> evaluateRules(
            AgentContext ctx, Map<String, Object> input) {

        RuleEngine.RuleEvaluationResult result = ruleEngine.evaluate(input);
        ctx.recordMetric("agent.rules.evaluated", result.getRulesEvaluated());
        ctx.recordMetric("agent.rules.matched", result.getMatchedRules().size());

        if (!result.isMatched()) {
            if (!detConfig.getDefaultActions().isEmpty()) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                        .output(new LinkedHashMap<>(detConfig.getDefaultActions()))
                        .confidence(1.0)
                        .status(AgentResultStatus.SUCCESS)
                        .explanation("No rule matched; returned default actions")
                        .build());
            }
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(1.0)
                    .status(AgentResultStatus.SKIPPED)
                    .explanation("No rule matched and no default configured")
                    .build());
        }

        Map<String, Object> output = new LinkedHashMap<>(result.getActions());
        output.put("_matchedRules", result.getMatchedRules().stream()
                .map(Rule::getId).toList());

        return Promise.of(AgentResult.success(output, descriptor.getAgentId(),
                result.getEvaluationTime()));
    }

    private Promise<AgentResult<Map<String, Object>>> evaluateThresholds(
            AgentContext ctx, Map<String, Object> input) {

        Map<String, Object> output = new LinkedHashMap<>();
        List<String> activeThresholds = new ArrayList<>();
        List<String> stateChanges = new ArrayList<>();

        for (ThresholdEvaluator evaluator : thresholdEvaluators) {
            try {
                ThresholdEvaluator.ThresholdResult result = evaluator.evaluate(input);
                output.put("threshold." + evaluator.getId() + ".active", result.isActive());
                output.put("threshold." + evaluator.getId() + ".value", result.getEvaluatedValue());

                if (result.isActive()) activeThresholds.add(evaluator.getId());
                if (result.isStateChanged()) stateChanges.add(evaluator.getId());
            } catch (IllegalArgumentException e) {
                log.warn("Threshold evaluation skipped: id={}, error={}",
                        evaluator.getId(), e.getMessage());
            }
        }

        output.put("_activeThresholds", activeThresholds);
        output.put("_stateChanges", stateChanges);

        ctx.recordMetric("agent.thresholds.active", activeThresholds.size());
        ctx.recordMetric("agent.thresholds.stateChanges", stateChanges.size());

        return Promise.of(AgentResult.success(output, descriptor.getAgentId(), Duration.ZERO));
    }

    private Promise<AgentResult<Map<String, Object>>> evaluateFSM(
            AgentContext ctx, Map<String, Object> input) {

        // Extract entity key from input
        Object entityKey = RuleCondition.resolve(detConfig.getFsmEntityKeyField(), input);
        if (entityKey == null) {
            return Promise.of(AgentResult.failure(
                    new IllegalArgumentException("Missing FSM entity key field: "
                            + detConfig.getFsmEntityKeyField()),
                    descriptor.getAgentId(), Duration.ZERO));
        }

        String entityId = entityKey.toString();
        FiniteStateMachine.TransitionResult result = fsm.process(entityId, input);

        Map<String, Object> output = new LinkedHashMap<>(result.getActions());
        output.put("_fsm.transitioned", result.isTransitioned());
        output.put("_fsm.previousState", result.getPreviousState());
        output.put("_fsm.currentState", result.getCurrentState());
        output.put("_fsm.isFinalState", result.isFinalState());
        if (result.getTransitionName() != null) {
            output.put("_fsm.transitionName", result.getTransitionName());
        }

        ctx.recordMetric("agent.fsm.transitioned", result.isTransitioned() ? 1 : 0);
        ctx.recordMetric("agent.fsm.entities", fsm.getTrackedEntityCount());

        String explanation = result.isTransitioned()
                ? "FSM: " + result.getPreviousState() + " → " + result.getCurrentState()
                        + " via " + result.getTransitionName()
                : "FSM: no transition from " + result.getCurrentState();

        return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(output)
                .confidence(1.0)
                .status(AgentResultStatus.SUCCESS)
                .explanation(explanation)
                .build());
    }

    private Promise<AgentResult<Map<String, Object>>> evaluateExactMatch(
            AgentContext ctx, Map<String, Object> input) {

        Object fieldValue = RuleCondition.resolve(detConfig.getExactMatchField(), input);
        if (fieldValue == null) {
            if (!detConfig.getDefaultActions().isEmpty()) {
                return Promise.of(AgentResult.<Map<String, Object>>builder()
                        .output(new LinkedHashMap<>(detConfig.getDefaultActions()))
                        .confidence(1.0)
                        .status(AgentResultStatus.SUCCESS)
                        .explanation("Exact match field is null; returned default")
                        .build());
            }
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(Map.of())
                    .confidence(1.0)
                    .status(AgentResultStatus.SKIPPED)
                    .explanation("Exact match field is null")
                    .build());
        }

        String key = fieldValue.toString();
        Map<String, Object> matched = detConfig.getExactMatchTable().get(key);

        if (matched != null) {
            Map<String, Object> output = new LinkedHashMap<>(matched);
            output.put("_exactMatch.field", detConfig.getExactMatchField());
            output.put("_exactMatch.key", key);
            ctx.recordMetric("agent.exactMatch.hit", 1);
            return Promise.of(AgentResult.success(output, descriptor.getAgentId(), Duration.ZERO));
        }

        ctx.recordMetric("agent.exactMatch.miss", 1);
        if (!detConfig.getDefaultActions().isEmpty()) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(new LinkedHashMap<>(detConfig.getDefaultActions()))
                    .confidence(1.0)
                    .status(AgentResultStatus.SUCCESS)
                    .explanation("No exact match for '" + key + "'; returned default")
                    .build());
        }
        return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(Map.of())
                .confidence(1.0)
                .status(AgentResultStatus.SKIPPED)
                .explanation("No exact match for '" + key + "'")
                .build());
    }

    private Promise<AgentResult<Map<String, Object>>> evaluatePattern(
            AgentContext ctx, Map<String, Object> input) {
        // Pattern subtype delegates to the NFA pattern detection subsystem
        // (implemented in Phase 1 — PatternDetectionAgent).
        // For now, this is a placeholder that returns the input as-is.
        log.warn("PATTERN subtype not yet wired to NFA subsystem; returning input as-is");
        return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(new LinkedHashMap<>(input))
                .confidence(0.5)
                .status(AgentResultStatus.DEGRADED)
                .explanation("PATTERN subtype: NFA subsystem not yet wired")
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Accessors
    // ═══════════════════════════════════════════════════════════════════════════

    /** Returns the active rule engine (may be null unless subtype is RULE_BASED). */
    public RuleEngine getRuleEngine() { return ruleEngine; }

    /** Returns the FSM (may be null unless subtype is FSM). */
    public FiniteStateMachine getFsm() { return fsm; }

    /** Returns the deterministic config. */
    public DeterministicAgentConfig getDeterministicConfig() { return detConfig; }
}
