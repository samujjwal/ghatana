/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.event.GEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Probabilistic rule-based pattern matching strategy.
 * Evaluates events against rules with confidence scores and probabilistic outcomes.
 *
 * @doc.type class
 * @doc.purpose Probabilistic rule-based pattern matching with confidence scoring
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class ProbabilisticRuleBasedMatchingStrategy implements PatternMatchingStrategy {

    private final List<Rule> rules;
    private final List<GEvent> matchedEvents;
    private final List<Double> ruleScores;
    private int ruleIndex;
    private double overallConfidence;

    public ProbabilisticRuleBasedMatchingStrategy() {
        this.rules = new ArrayList<>();
        this.matchedEvents = new ArrayList<>();
        this.ruleScores = new ArrayList<>();
        this.ruleIndex = 0;
        this.overallConfidence = 0.0;
    }

    public ProbabilisticRuleBasedMatchingStrategy(List<Rule> rules) {
        this.rules = new ArrayList<>(rules);
        this.matchedEvents = new ArrayList<>();
        this.ruleScores = new ArrayList<>();
        this.ruleIndex = 0;
        this.overallConfidence = 0.0;
    }

    /**
     * Adds a rule with confidence scoring.
     *
     * @param rule the rule to add
     * @return this strategy for chaining
     */
    public ProbabilisticRuleBasedMatchingStrategy addRule(Rule rule) {
        rules.add(rule);
        return this;
    }

    @Override
    public Optional<PatternMatch> evaluate(GEvent event, NFA nfa, MatchingContext context) {
        if (rules.isEmpty()) {
            return Optional.empty();
        }

        // Check current rule
        if (ruleIndex < rules.size()) {
            Rule currentRule = rules.get(ruleIndex);
            double score = currentRule.score(event);

            if (score >= currentRule.threshold()) {
                matchedEvents.add(event);
                ruleScores.add(score);
                ruleIndex++;

                // Update overall confidence (average of rule scores)
                overallConfidence = ruleScores.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

                // All rules matched
                if (ruleIndex >= rules.size()) {
                    PatternMatch match = new PatternMatch(
                        nfa.getPatternName(),
                        overallConfidence,
                        List.copyOf(matchedEvents),
                        java.time.Instant.now(),
                        StrategyType.PROBABILISTIC_RULE_BASED
                    );

                    reset();
                    return Optional.of(match);
                }

                return Optional.empty();
            }

            // Rule failed - reset
            reset();
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public void reset() {
        matchedEvents.clear();
        ruleScores.clear();
        ruleIndex = 0;
        overallConfidence = 0.0;
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.PROBABILISTIC_RULE_BASED;
    }

    /**
     * Gets the current overall confidence.
     *
     * @return current confidence score
     */
    public double getOverallConfidence() {
        return overallConfidence;
    }

    /**
     * Rule with confidence scoring.
     *
     * @param predicate the rule predicate
     * @param scorer confidence scoring function
     * @param threshold minimum threshold for rule match
     */
    public record Rule(
        java.util.function.Predicate<GEvent> predicate,
        java.util.function.Function<GEvent, Double> scorer,
        double threshold
    ) {
        public Rule {
            threshold = Math.max(0.0, Math.min(1.0, threshold));
        }

        /**
         * Evaluates the rule against an event.
         *
         * @param event the event to evaluate
         * @return confidence score if predicate matches, 0.0 otherwise
         */
        public double score(GEvent event) {
            if (predicate.test(event)) {
                double score = scorer.apply(event);
                return Math.max(0.0, Math.min(1.0, score));
            }
            return 0.0;
        }
    }

    /**
     * Builder for creating rules.
     */
    public static class RuleBuilder {
        private java.util.function.Predicate<GEvent> predicate;
        private java.util.function.Function<GEvent, Double> scorer;
        private double threshold = 0.5;

        public RuleBuilder predicate(java.util.function.Predicate<GEvent> predicate) {
            this.predicate = predicate;
            return this;
        }

        public RuleBuilder scorer(java.util.function.Function<GEvent, Double> scorer) {
            this.scorer = scorer;
            return this;
        }

        public RuleBuilder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Rule build() {
            return new Rule(predicate, scorer, threshold);
        }
    }
}
