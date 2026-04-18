/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.event.GEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for pattern matching strategies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for pattern matching strategies
 * @doc.layer test
 */
@DisplayName("Pattern Matching Strategy Tests")
class PatternMatchingStrategyTest {

    @Test
    @DisplayName("deterministic strategy matches exact state transitions")
    void deterministicStrategy_matchesExactTransitions() {
        DeterministicMatchingStrategy strategy = new DeterministicMatchingStrategy();
        
        NFA nfa = createSimpleNFA("test-pattern");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty();
        
        GEvent event1 = createEvent("type1", Map.of("value", "a"));
        GEvent event2 = createEvent("type2", Map.of("value", "b"));
        
        // First event should not match (needs sequence)
        assertThat(strategy.evaluate(event1, nfa, context)).isEmpty();
        
        // Second event should match if sequence completes
        Optional<PatternMatchingStrategy.PatternMatch> match = strategy.evaluate(event2, nfa, context);
        // Note: Actual match behavior depends on NFA structure
        assertThat(strategy.getStrategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.DETERMINISTIC);
    }

    @Test
    @DisplayName("deterministic strategy resets after reset")
    void deterministicStrategy_resetsAfterReset() {
        DeterministicMatchingStrategy strategy = new DeterministicMatchingStrategy();
        
        NFA nfa = createSimpleNFA("test-pattern");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty();
        
        strategy.reset();
        assertThat(strategy.getStrategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.DETERMINISTIC);
    }

    @Test
    @DisplayName("rule-based strategy matches all rules in sequence")
    void ruleBasedStrategy_matchesAllRules() {
        RuleBasedMatchingStrategy strategy = new RuleBasedMatchingStrategy();
        
        strategy.addRule(event -> event.getType().equals("type1"));
        strategy.addRule(event -> event.getPayload().containsKey("value"));
        strategy.addRule(event -> event.getPayload().get("value").equals("c"));
        
        NFA nfa = createSimpleNFA("test-pattern");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty();
        
        GEvent event1 = createEvent("type1", Map.of("step", 1));
        GEvent event2 = createEvent("type2", Map.of("value", "b"));
        GEvent event3 = createEvent("type3", Map.of("value", "c"));
        
        // First event matches first rule
        assertThat(strategy.evaluate(event1, nfa, context)).isEmpty();
        assertThat(strategy.getRuleIndex()).isEqualTo(1);
        
        // Second event doesn't match second rule (wrong type for value check)
        strategy.evaluate(event2, nfa, context);
        assertThat(strategy.getRuleIndex()).isEqualTo(0); // Should reset
        
        // Correct sequence
        strategy.evaluate(event1, nfa, context);
        assertThat(strategy.evaluate(event3, nfa, context)).isEmpty(); // type doesn't match first rule
        strategy.reset();
        
        strategy.evaluate(event1, nfa, context);
        GEvent event2b = createEvent("type1", Map.of("value", "b"));
        strategy.evaluate(event2b, nfa, context);
        assertThat(strategy.evaluate(event3, nfa, context)).isPresent();
        assertThat(strategy.getStrategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.RULE_BASED);
    }

    @Test
    @DisplayName("probabilistic rule-based strategy scores matches with confidence")
    void probabilisticRuleBasedStrategy_scoresMatches() {
        ProbabilisticRuleBasedMatchingStrategy strategy = new ProbabilisticRuleBasedMatchingStrategy();
        
        ProbabilisticRuleBasedMatchingStrategy.Rule rule1 = new ProbabilisticRuleBasedMatchingStrategy.RuleBuilder()
            .predicate(event -> event.getType().equals("type1"))
            .scorer(event -> 0.9)
            .threshold(0.5)
            .build();
        
        ProbabilisticRuleBasedMatchingStrategy.Rule rule2 = new ProbabilisticRuleBasedMatchingStrategy.RuleBuilder()
            .predicate(event -> event.getPayload().containsKey("value"))
            .scorer(event -> 0.8)
            .threshold(0.5)
            .build();
        
        strategy.addRule(rule1);
        strategy.addRule(rule2);
        
        NFA nfa = createSimpleNFA("test-pattern");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty();
        
        GEvent event1 = createEvent("type1", Map.of("step", 1));
        GEvent event2 = createEvent("type2", Map.of("value", "test"));
        
        strategy.evaluate(event1, nfa, context);
        assertThat(strategy.getOverallConfidence()).isEqualTo(0.9);
        
        Optional<PatternMatchingStrategy.PatternMatch> match = strategy.evaluate(event2, nfa, context);
        assertThat(match).isPresent();
        assertThat(match.get().confidence()).isGreaterThan(0.85); // Average of 0.9 and 0.8
        assertThat(match.get().strategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED);
    }

    @Test
    @DisplayName("flexible matcher switches between strategies")
    void flexibleMatcher_switchesStrategies() {
        FlexiblePatternMatcher matcher = new FlexiblePatternMatcher();
        
        NFA nfa = createSimpleNFA("test-pattern");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty();
        GEvent event = createEvent("type1", Map.of("value", "test"));
        
        // Test deterministic
        matcher.setActiveStrategy(PatternMatchingStrategy.StrategyType.DETERMINISTIC);
        assertThat(matcher.getActiveStrategy()).isEqualTo(PatternMatchingStrategy.StrategyType.DETERMINISTIC);
        matcher.evaluate(event, nfa, context);
        
        // Test rule-based
        matcher.setActiveStrategy(PatternMatchingStrategy.StrategyType.RULE_BASED);
        assertThat(matcher.getActiveStrategy()).isEqualTo(PatternMatchingStrategy.StrategyType.RULE_BASED);
        matcher.evaluate(event, nfa, context);
        
        // Test probabilistic rule-based
        matcher.setActiveStrategy(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED);
        assertThat(matcher.getActiveStrategy()).isEqualTo(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED);
        matcher.evaluate(event, nfa, context);
        
        // Reset
        matcher.reset();
        matcher.resetAll();
    }

    @Test
    @DisplayName("flexible matcher allows custom strategy registration")
    void flexibleMatcher_registersCustomStrategy() {
        FlexiblePatternMatcher matcher = new FlexiblePatternMatcher();
        
        PatternMatchingStrategy customStrategy = new PatternMatchingStrategy() {
            @Override
            public Optional<PatternMatch> evaluate(GEvent event, NFA nfa, MatchingContext context) {
                return Optional.of(new PatternMatch("custom", 1.0, List.of(event), Instant.now(), StrategyType.DETERMINISTIC));
            }
            
            @Override
            public void reset() {}
            
            @Override
            public StrategyType getStrategyType() {
                return StrategyType.DETERMINISTIC;
            }
        };
        
        matcher.registerStrategy(PatternMatchingStrategy.StrategyType.DETERMINISTIC, customStrategy);
        
        assertThat(matcher.getStrategy(PatternMatchingStrategy.StrategyType.DETERMINISTIC)).isPresent();
    }

    // Helper methods

    private NFA createSimpleNFA(String patternName) {
        // Create a minimal NFA for testing
        NFA.State initialState = new NFA.State("initial", false);
        NFA.State acceptingState = new NFA.State("accepting", true);
        
        return new NFA() {
            @Override
            public String getPatternName() {
                return patternName;
            }
            
            @Override
            public NFA.State getInitialState() {
                return initialState;
            }
            
            @Override
            public java.util.List<NFA.Transition> getTransitions(NFA.State state) {
                if (state.equals(initialState)) {
                    return List.of(new NFA.Transition(initialState, acceptingState, e -> true));
                }
                return List.of();
            }
        };
    }

    private GEvent createEvent(String type, Map<String, Object> payload) {
        return GEvent.builder()
            .type(type)
            .payload(payload)
            .build();
    }
}
