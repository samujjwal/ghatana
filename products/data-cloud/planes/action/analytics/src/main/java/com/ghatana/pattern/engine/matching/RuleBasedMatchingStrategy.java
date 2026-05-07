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
import java.util.function.Predicate;

/**
 * Rule-based pattern matching strategy.
 * Evaluates events against configurable rules with boolean outcomes.
 *
 * @doc.type class
 * @doc.purpose Rule-based pattern matching with configurable predicates
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class RuleBasedMatchingStrategy implements PatternMatchingStrategy {

    private final List<Predicate<GEvent>> rules;
    private final List<GEvent> matchedEvents;
    private int ruleIndex;

    public RuleBasedMatchingStrategy() {
        this.rules = new ArrayList<>();
        this.matchedEvents = new ArrayList<>();
        this.ruleIndex = 0;
    }

    public RuleBasedMatchingStrategy(List<Predicate<GEvent>> rules) {
        this.rules = new ArrayList<>(rules);
        this.matchedEvents = new ArrayList<>();
        this.ruleIndex = 0;
    }

    /**
     * Adds a rule to the matching strategy.
     *
     * @param rule the predicate rule to add
     * @return this strategy for chaining
     */
    public RuleBasedMatchingStrategy addRule(Predicate<GEvent> rule) {
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
            Predicate<GEvent> currentRule = rules.get(ruleIndex);
            if (currentRule.test(event)) {
                matchedEvents.add(event);
                ruleIndex++;

                // All rules matched
                if (ruleIndex >= rules.size()) {
                    PatternMatch match = new PatternMatch(
                        nfa.getPatternName(),
                        1.0, // Rule-based = 100% confidence when all rules match
                        List.copyOf(matchedEvents),
                        java.time.Instant.now(),
                        StrategyType.RULE_BASED
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
        ruleIndex = 0;
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.RULE_BASED;
    }

    /**
     * Gets the current rule index.
     *
     * @return current rule index
     */
    public int getRuleIndex() {
        return ruleIndex;
    }

    /**
     * Gets the number of rules configured.
     *
     * @return number of rules
     */
    public int getRuleCount() {
        return rules.size();
    }
}
