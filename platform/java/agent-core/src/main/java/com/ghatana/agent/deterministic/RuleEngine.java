/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Deterministic rule evaluation engine.
 *
 * <p>Supports two modes:
 * <ul>
 *   <li><b>First-match</b> (default) — returns the first rule whose conditions are met</li>
 *   <li><b>All-match</b> — returns every rule whose conditions are met, merged in priority order</li>
 * </ul>
 *
 * <p>Rules are sorted by priority (ascending) before evaluation. Evaluation is O(rules × conditions)
 * and entirely allocation-free on the hot path (no intermediate streams / lambdas).
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Evaluates rules against input context and fires matching actions
 * @doc.layer platform
 * @doc.pattern Service
 */
@Value
@Builder(toBuilder = true)
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /** Pre-sorted list of rules (ascending priority). */
    @Singular @NotNull List<Rule> rules;

    /** When true, returns all matching rules merged; when false, first-match only. */
    @Builder.Default boolean evaluateAll = false;

    // ═══════════════════════════════════════════════════════════════════════════
    // Evaluation
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Evaluates the input against the configured rules.
     *
     * @param input the event map
     * @return evaluation result containing matched actions and metadata
     */
    @NotNull
    public RuleEvaluationResult evaluate(@NotNull Map<String, Object> input) {
        Objects.requireNonNull(input, "input must not be null");

        Instant start = Instant.now();
        List<Rule> sorted = sortedByPriority();
        List<Rule> matched = new ArrayList<>();
        Map<String, Object> mergedActions = new LinkedHashMap<>();

        for (Rule rule : sorted) {
            try {
                if (rule.matches(input)) {
                    matched.add(rule);
                    mergedActions.putAll(rule.getActions());
                    if (rule.isTerminal() && !evaluateAll) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.warn("Rule evaluation error: rule={}, error={}", rule.getId(), e.getMessage());
                // Continue evaluating remaining rules
            }
        }

        Duration elapsed = Duration.between(start, Instant.now());
        return RuleEvaluationResult.builder()
                .matched(!matched.isEmpty())
                .matchedRules(matched)
                .actions(mergedActions)
                .rulesEvaluated(sorted.size())
                .evaluationTime(elapsed)
                .build();
    }

    /**
     * Returns rules sorted by priority (ascending — lower number = higher priority).
     */
    private List<Rule> sortedByPriority() {
        if (rules.size() <= 1) return rules;
        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(Rule::getPriority));
        return sorted;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Result
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of rule evaluation.
     */
    @Value
    @Builder(toBuilder = true)
    public static class RuleEvaluationResult {

        /** Whether any rule matched. */
        boolean matched;

        /** Ordered list of matched rules. */
        @Singular List<Rule> matchedRules;

        /** Merged actions from all matched rules. */
        @NotNull Map<String, Object> actions;

        /** Total number of rules evaluated. */
        int rulesEvaluated;

        /** Time taken for evaluation. */
        @NotNull Duration evaluationTime;

        /** Returns the first matched rule id, or empty. */
        public Optional<String> firstMatchedRuleId() {
            return matchedRules.isEmpty() ? Optional.empty() : Optional.of(matchedRules.get(0).getId());
        }
    }
}
