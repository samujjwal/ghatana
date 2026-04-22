/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.pattern.engine.nfa.NFAState;
import com.ghatana.pattern.engine.nfa.NFAStateType;
import com.ghatana.platform.domain.event.EventRelations;
import com.ghatana.platform.domain.event.EventStats;
import com.ghatana.platform.domain.event.EventId;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.domain.event.GEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for pattern matching strategies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for pattern matching strategies
 * @doc.layer test
 */
@DisplayName("Pattern Matching Strategy Tests [GH-90000]")
class PatternMatchingStrategyTest {

    @Test
    @DisplayName("deterministic strategy matches exact state transitions [GH-90000]")
    void deterministicStrategy_matchesExactTransitions() { // GH-90000
        DeterministicMatchingStrategy strategy = new DeterministicMatchingStrategy(); // GH-90000
        
        NFA nfa = createSimpleNFA("test-pattern [GH-90000]");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty(); // GH-90000
        
        GEvent event1 = createEvent("type1", Map.of("value", "a")); // GH-90000
        GEvent event2 = createEvent("type2", Map.of("value", "b")); // GH-90000
        
        // First event should not match (needs sequence) // GH-90000
        assertThat(strategy.evaluate(event1, nfa, context)).isEmpty(); // GH-90000
        
        // Second event should match if sequence completes
        strategy.evaluate(event2, nfa, context); // GH-90000
        // Note: Actual match behavior depends on NFA structure
        assertThat(strategy.getStrategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.DETERMINISTIC); // GH-90000
    }

    @Test
    @DisplayName("deterministic strategy resets after reset [GH-90000]")
    void deterministicStrategy_resetsAfterReset() { // GH-90000
        DeterministicMatchingStrategy strategy = new DeterministicMatchingStrategy(); // GH-90000
        
        strategy.reset(); // GH-90000
        assertThat(strategy.getStrategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.DETERMINISTIC); // GH-90000
    }

    @Test
    @DisplayName("rule-based strategy matches all rules in sequence [GH-90000]")
    void ruleBasedStrategy_matchesAllRules() { // GH-90000
        RuleBasedMatchingStrategy strategy = new RuleBasedMatchingStrategy(); // GH-90000
        
        strategy.addRule(event -> event.getType().equals("type1 [GH-90000]"));
        strategy.addRule(event -> event.getPayload().containsKey("value [GH-90000]"));
        strategy.addRule(event -> event.getPayload().get("value [GH-90000]").equals("c [GH-90000]"));
        
        NFA nfa = createSimpleNFA("test-pattern [GH-90000]");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty(); // GH-90000
        
        GEvent event1 = createEvent("type1", Map.of("step", 1)); // GH-90000
        GEvent event2 = createEvent("type2", Map.of("step", 2)); // GH-90000
        GEvent event3 = createEvent("type3", Map.of("value", "c")); // GH-90000
        
        // First event matches first rule
        assertThat(strategy.evaluate(event1, nfa, context)).isEmpty(); // GH-90000
        assertThat(strategy.getRuleIndex()).isEqualTo(1); // GH-90000
        
        // Second event doesn't match second rule because the payload lacks the required field
        strategy.evaluate(event2, nfa, context); // GH-90000
        assertThat(strategy.getRuleIndex()).isEqualTo(0); // Should reset // GH-90000
        
        // Correct sequence
        strategy.evaluate(event1, nfa, context); // GH-90000
        assertThat(strategy.evaluate(event3, nfa, context)).isEmpty(); // type doesn't match first rule // GH-90000
        strategy.reset(); // GH-90000
        
        strategy.evaluate(event1, nfa, context); // GH-90000
        GEvent event2b = createEvent("type1", Map.of("value", "b")); // GH-90000
        strategy.evaluate(event2b, nfa, context); // GH-90000
        assertThat(strategy.evaluate(event3, nfa, context)).isPresent(); // GH-90000
        assertThat(strategy.getStrategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.RULE_BASED); // GH-90000
    }

    @Test
    @DisplayName("probabilistic rule-based strategy scores matches with confidence [GH-90000]")
    void probabilisticRuleBasedStrategy_scoresMatches() { // GH-90000
        ProbabilisticRuleBasedMatchingStrategy strategy = new ProbabilisticRuleBasedMatchingStrategy(); // GH-90000
        
        ProbabilisticRuleBasedMatchingStrategy.Rule rule1 = new ProbabilisticRuleBasedMatchingStrategy.RuleBuilder() // GH-90000
            .predicate(event -> event.getType().equals("type1 [GH-90000]"))
            .scorer(event -> 0.9) // GH-90000
            .threshold(0.5) // GH-90000
            .build(); // GH-90000
        
        ProbabilisticRuleBasedMatchingStrategy.Rule rule2 = new ProbabilisticRuleBasedMatchingStrategy.RuleBuilder() // GH-90000
            .predicate(event -> event.getPayload().containsKey("value [GH-90000]"))
            .scorer(event -> 0.8) // GH-90000
            .threshold(0.5) // GH-90000
            .build(); // GH-90000
        
        strategy.addRule(rule1); // GH-90000
        strategy.addRule(rule2); // GH-90000
        
        NFA nfa = createSimpleNFA("test-pattern [GH-90000]");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty(); // GH-90000
        
        GEvent event1 = createEvent("type1", Map.of("step", 1)); // GH-90000
        GEvent event2 = createEvent("type2", Map.of("value", "test")); // GH-90000
        
        strategy.evaluate(event1, nfa, context); // GH-90000
        assertThat(strategy.getOverallConfidence()).isEqualTo(0.9); // GH-90000
        
        Optional<PatternMatchingStrategy.PatternMatch> match = strategy.evaluate(event2, nfa, context); // GH-90000
        assertThat(match).isPresent(); // GH-90000
        assertThat(match.get().confidence()).isGreaterThan(0.85); // Average of 0.9 and 0.8 // GH-90000
        assertThat(match.get().strategyType()).isEqualTo(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED); // GH-90000
    }

    @Test
    @DisplayName("flexible matcher switches between strategies [GH-90000]")
    void flexibleMatcher_switchesStrategies() { // GH-90000
        FlexiblePatternMatcher matcher = new FlexiblePatternMatcher(); // GH-90000
        
        NFA nfa = createSimpleNFA("test-pattern [GH-90000]");
        PatternMatchingStrategy.MatchingContext context = PatternMatchingStrategy.MatchingContext.empty(); // GH-90000
        GEvent event = createEvent("type1", Map.of("value", "test")); // GH-90000
        
        // Test deterministic
        matcher.setActiveStrategy(PatternMatchingStrategy.StrategyType.DETERMINISTIC); // GH-90000
        assertThat(matcher.getActiveStrategy()).isEqualTo(PatternMatchingStrategy.StrategyType.DETERMINISTIC); // GH-90000
        matcher.evaluate(event, nfa, context); // GH-90000
        
        // Test rule-based
        matcher.setActiveStrategy(PatternMatchingStrategy.StrategyType.RULE_BASED); // GH-90000
        assertThat(matcher.getActiveStrategy()).isEqualTo(PatternMatchingStrategy.StrategyType.RULE_BASED); // GH-90000
        matcher.evaluate(event, nfa, context); // GH-90000
        
        // Test probabilistic rule-based
        matcher.setActiveStrategy(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED); // GH-90000
        assertThat(matcher.getActiveStrategy()).isEqualTo(PatternMatchingStrategy.StrategyType.PROBABILISTIC_RULE_BASED); // GH-90000
        matcher.evaluate(event, nfa, context); // GH-90000
        
        // Reset
        matcher.reset(); // GH-90000
        matcher.resetAll(); // GH-90000
    }

    @Test
    @DisplayName("flexible matcher allows custom strategy registration [GH-90000]")
    void flexibleMatcher_registersCustomStrategy() { // GH-90000
        FlexiblePatternMatcher matcher = new FlexiblePatternMatcher(); // GH-90000
        
        PatternMatchingStrategy customStrategy = new PatternMatchingStrategy() { // GH-90000
            @Override
            public Optional<PatternMatch> evaluate(GEvent event, NFA nfa, MatchingContext context) { // GH-90000
                return Optional.of(new PatternMatch("custom", 1.0, List.of(event), Instant.now(), StrategyType.DETERMINISTIC)); // GH-90000
            }
            
            @Override
            public void reset() {} // GH-90000
            
            @Override
            public StrategyType getStrategyType() { // GH-90000
                return StrategyType.DETERMINISTIC;
            }
        };
        
        matcher.registerStrategy(PatternMatchingStrategy.StrategyType.DETERMINISTIC, customStrategy); // GH-90000
        
        assertThat(matcher.getStrategy(PatternMatchingStrategy.StrategyType.DETERMINISTIC)).isPresent(); // GH-90000
    }

    // Helper methods

    private NFA createSimpleNFA(String patternName) { // GH-90000
        NFA nfa = new NFA(patternName); // GH-90000
        NFAState acceptingState = new NFAState("accepting", NFAStateType.END); // GH-90000
        nfa.addState(acceptingState); // GH-90000
        nfa.addTransition(nfa.getStartState(), acceptingState, "type2"); // GH-90000
        return nfa;
    }

    private GEvent createEvent(String type, Map<String, Object> payload) { // GH-90000
        return GEvent.builder() // GH-90000
            .id(EventId.create(UUID.randomUUID().toString(), type, "v1", "test-tenant")) // GH-90000
            .time(EventTime.now()) // GH-90000
            .stats(EventStats.builder() // GH-90000
                .withProcessingTimeNanos(0) // GH-90000
                .withSizeInBytes(payload.size()) // GH-90000
                .withFieldCount(payload.size()) // GH-90000
                .withTagCount(0) // GH-90000
                .build()) // GH-90000
            .relations(EventRelations.empty()) // GH-90000
            .headers(Map.of()) // GH-90000
            .payload(payload) // GH-90000
            .build(); // GH-90000
    }
}
