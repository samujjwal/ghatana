/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.reactive;

import com.ghatana.agent.*;
import com.ghatana.agent.deterministic.RuleCondition;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Reactive agent — evaluates triggers and fires actions with cooldown/debounce support.
 *
 * <p>Features:
 * <ul>
 *   <li>Sub-millisecond trigger evaluation</li>
 *   <li>Cooldown/debounce per trigger</li>
 *   <li>Sliding window counters for threshold-based triggers</li>
 *   <li>Priority-based rule evaluation</li>
 * </ul>
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Event-driven agent that reacts to stimuli with minimal latency
 * @doc.layer platform
 * @doc.pattern Service
 * @doc.gaa.lifecycle perceive
 */
public class ReactiveAgent
        extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {

    private final AgentDescriptor descriptor;
    private volatile ReactiveAgentConfig reactiveConfig;

    /** Last fire time per trigger name — for cooldown enforcement. */
    private final ConcurrentHashMap<String, Instant> lastFired = new ConcurrentHashMap<>();

    /** Sliding window event counts per trigger name. */
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<Instant>> eventWindows =
            new ConcurrentHashMap<>();

    // ═══════════════════════════════════════════════════════════════════════════
    // Construction
    // ═══════════════════════════════════════════════════════════════════════════

    public ReactiveAgent(@NotNull String agentId) {
        this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .description("Reactive agent — trigger-based event processing")
                .version("1.0.0")
                .type(AgentType.REACTIVE)
                .determinism(DeterminismGuarantee.FULL)
                .stateMutability(StateMutability.LOCAL_STATE)
                .failureMode(FailureMode.FAIL_FAST)
                .latencySla(Duration.ofMillis(1))
                .throughputTarget(100_000)
                .build();
    }

    public ReactiveAgent(@NotNull AgentDescriptor descriptor) {
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
        if (!(config instanceof ReactiveAgentConfig rc)) {
            return Promise.ofException(new IllegalArgumentException(
                    "Expected ReactiveAgentConfig but got " + config.getClass().getSimpleName()));
        }

        this.reactiveConfig = rc;
        lastFired.clear();
        eventWindows.clear();

        log.info("Initialized reactive agent: {} triggers={}",
                descriptor.getAgentId(), rc.getTriggers().size());
        return Promise.complete();
    }

    @Override
    @NotNull
    protected Promise<AgentResult<Map<String, Object>>> doProcess(
            @NotNull AgentContext ctx, @NotNull Map<String, Object> input) {

        if (reactiveConfig == null) {
            return Promise.of(AgentResult.failure(
                    new IllegalStateException("Not configured"),
                    descriptor.getAgentId(), Duration.ZERO));
        }

        Instant now = Instant.now();
        List<ReactiveAgentConfig.TriggerDefinition> sorted = reactiveConfig.getTriggers().stream()
                .sorted(Comparator.comparingInt(ReactiveAgentConfig.TriggerDefinition::getPriority))
                .toList();

        List<String> firedTriggers = new ArrayList<>();
        Map<String, Object> mergedActions = new LinkedHashMap<>();

        for (ReactiveAgentConfig.TriggerDefinition trigger : sorted) {
            if (evaluateTrigger(trigger, input, now)) {
                firedTriggers.add(trigger.getName());
                mergedActions.putAll(trigger.getActions());
                lastFired.put(trigger.getName(), now);
                ctx.recordMetric("agent.reactive.trigger.fired." + trigger.getName(), 1);
            }
        }

        ctx.recordMetric("agent.reactive.triggers.evaluated", sorted.size());
        ctx.recordMetric("agent.reactive.triggers.fired", firedTriggers.size());

        if (firedTriggers.isEmpty()) {
            return Promise.of(AgentResult.<Map<String, Object>>builder()
                    .output(Map.of("_reactive.firedTriggers", List.of()))
                    .confidence(1.0)
                    .status(AgentResultStatus.SKIPPED)
                    .explanation("No triggers matched")
                    .build());
        }

        mergedActions.put("_reactive.firedTriggers", firedTriggers);
        return Promise.of(AgentResult.<Map<String, Object>>builder()
                .output(mergedActions)
                .confidence(1.0)
                .status(AgentResultStatus.SUCCESS)
                .explanation("Fired triggers: " + firedTriggers)
                .build());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Trigger Evaluation
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean evaluateTrigger(ReactiveAgentConfig.TriggerDefinition trigger,
                                    Map<String, Object> input, Instant now) {

        // 1. Check event type match
        Object eventType = RuleCondition.resolve(trigger.getEventTypeField(), input);
        if (eventType == null || !eventType.toString().equals(trigger.getEventTypeValue())) {
            return false;
        }

        // 2. Check additional condition if present
        if (trigger.getConditionField() != null && trigger.getConditionOperator() != null) {
            Object actual = RuleCondition.resolve(trigger.getConditionField(), input);
            if (!evaluateSimpleCondition(actual, trigger.getConditionOperator(),
                    trigger.getConditionValue())) {
                return false;
            }
        }

        // 3. Check cooldown
        if (!trigger.getCooldown().isZero()) {
            Instant lastFire = lastFired.get(trigger.getName());
            if (lastFire != null && now.isBefore(lastFire.plus(trigger.getCooldown()))) {
                return false; // Still in cooldown
            }
        }

        // 4. Check threshold (sliding window)
        if (trigger.getThreshold() > 0) {
            ConcurrentLinkedDeque<Instant> window = eventWindows.computeIfAbsent(
                    trigger.getName(), k -> new ConcurrentLinkedDeque<>());

            window.addLast(now);

            // Evict old events outside the counting window
            Instant cutoff = now.minus(trigger.getCountingWindow());
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }

            return window.size() >= trigger.getThreshold();
        }

        return true; // All checks passed
    }

    private boolean evaluateSimpleCondition(Object actual, String operator, Object expected) {
        if (actual == null) return false;
        return switch (operator.toLowerCase()) {
            case ">" -> toDouble(actual) > toDouble(expected);
            case ">=" -> toDouble(actual) >= toDouble(expected);
            case "<" -> toDouble(actual) < toDouble(expected);
            case "<=" -> toDouble(actual) <= toDouble(expected);
            case "==" -> actual.toString().equals(String.valueOf(expected));
            case "!=" -> !actual.toString().equals(String.valueOf(expected));
            case "contains" -> actual.toString().contains(String.valueOf(expected));
            default -> {
                log.warn("Unknown operator: {}", operator);
                yield false;
            }
        };
    }

    private static double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // State Management
    // ═══════════════════════════════════════════════════════════════════════════

    /** Resets all cooldowns and sliding windows. */
    public void resetState() {
        lastFired.clear();
        eventWindows.clear();
    }

    /** Returns the number of active sliding windows. */
    public int getActiveWindowCount() {
        return eventWindows.size();
    }
}
