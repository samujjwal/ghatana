/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.event.GEvent;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flexible pattern matcher that supports multiple matching strategies.
 * Delegates to the appropriate strategy based on configuration.
 *
 * @doc.type class
 * @doc.purpose Flexible pattern matcher with strategy selection
 * @doc.layer product
 * @doc.pattern Strategy, Facade
 */
public final class FlexiblePatternMatcher {

    private final Map<PatternMatchingStrategy.StrategyType, PatternMatchingStrategy> strategies;
    private PatternMatchingStrategy.StrategyType activeStrategy;

    public FlexiblePatternMatcher() {
        this.strategies = new ConcurrentHashMap<>();
        this.activeStrategy = PatternMatchingStrategy.StrategyType.DETERMINISTIC;
        
        // Register default strategies
        strategies.put(PatternMatchingStrategy.StrategyType.DETERMINISTIC, new DeterministicMatchingStrategy());
        strategies.put(PatternMatchingStrategy.StrategyType.RULE_BASED, new RuleBasedMatchingStrategy());
        strategies.put(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED, new ProbabilisticRuleBasedMatchingStrategy());
    }

    /**
     * Registers a custom strategy for a given type.
     *
     * @param strategyType the strategy type
     * @param strategy the strategy implementation
     * @return this matcher for chaining
     */
    public FlexiblePatternMatcher registerStrategy(PatternMatchingStrategy.StrategyType strategyType,
                                                   PatternMatchingStrategy strategy) {
        strategies.put(strategyType, strategy);
        return this;
    }

    /**
     * Sets the active matching strategy.
     *
     * @param strategyType the strategy type to activate
     * @return this matcher for chaining
     */
    public FlexiblePatternMatcher setActiveStrategy(PatternMatchingStrategy.StrategyType strategyType) {
        this.activeStrategy = strategyType;
        return this;
    }

    /**
     * Gets the active strategy type.
     *
     * @return active strategy type
     */
    public PatternMatchingStrategy.StrategyType getActiveStrategy() {
        return activeStrategy;
    }

    /**
     * Evaluates an event using the active strategy.
     *
     * @param event the event to evaluate
     * @param nfa the NFA defining the pattern
     * @param context optional matching context
     * @return optional match result
     */
    public Optional<PatternMatchingStrategy.PatternMatch> evaluate(GEvent event, NFA nfa,
                                                                    PatternMatchingStrategy.MatchingContext context) {
        PatternMatchingStrategy strategy = strategies.get(activeStrategy);
        if (strategy == null) {
            throw new IllegalStateException("No strategy registered for type: " + activeStrategy);
        }
        return strategy.evaluate(event, nfa, context);
    }

    /**
     * Resets the active strategy's state.
     */
    public void reset() {
        PatternMatchingStrategy strategy = strategies.get(activeStrategy);
        if (strategy != null) {
            strategy.reset();
        }
    }

    /**
     * Resets all strategies.
     */
    public void resetAll() {
        strategies.values().forEach(PatternMatchingStrategy::reset);
    }

    /**
     * Gets a registered strategy by type.
     *
     * @param strategyType the strategy type
     * @return the strategy, or empty if not registered
     */
    public Optional<PatternMatchingStrategy> getStrategy(PatternMatchingStrategy.StrategyType strategyType) {
        return Optional.ofNullable(strategies.get(strategyType));
    }

    /**
     * Gets the rule-based strategy for configuration.
     *
     * @return the rule-based strategy
     */
    public RuleBasedMatchingStrategy getRuleBasedStrategy() {
        return (RuleBasedMatchingStrategy) strategies.get(PatternMatchingStrategy.StrategyType.RULE_BASED);
    }

    /**
     * Gets the probabilistic rule-based strategy for configuration.
     *
     * @return the probabilistic rule-based strategy
     */
    public ProbabilisticRuleBasedMatchingStrategy getProbabilisticRuleBasedStrategy() {
        return (ProbabilisticRuleBasedMatchingStrategy) strategies.get(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED);
    }
}
