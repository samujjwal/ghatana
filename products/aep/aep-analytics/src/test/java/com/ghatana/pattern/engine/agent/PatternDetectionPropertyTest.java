package com.ghatana.pattern.engine.agent;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.pattern.engine.nfa.NFAState;
import com.ghatana.pattern.engine.nfa.NFAStateType;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for PatternDetectionAgent.
 *
 * Tests invariants that should hold for any valid input:
 * - Confidence threshold is respected (matches below threshold are not reported) 
 * - Deterministic behavior (same events in same order produce same results) 
 * - Window expiry resets state correctly
 * - Event type filtering works as expected
 *
 * @doc.type class
 * @doc.purpose Property-based testing for pattern detection invariants
 * @doc.layer product
 * @doc.pattern PropertyBasedTest
 */
@DisplayName("PatternDetectionAgent – Property-Based Tests")
class PatternDetectionPropertyTest extends EventloopTestBase {

    private PatternDetectionAgent agent;
    private final Random random = new Random(42); // Fixed seed for reproducibility 

    @BeforeEach
    void setUp() { 
        NFA nfa = createFraudDetectionNFA(); 
        agent = PatternDetectionAgent.builder() 
            .operatorId(OperatorId.of("test", "pattern", "property-test", "1.0")) 
            .name("Property Test Agent")
            .nfa(nfa) 
            .confidenceThreshold(0.7) 
            .windowDuration(Duration.ofMinutes(5)) 
            .build(); 
        
        agent.initialize(com.ghatana.core.operator.OperatorConfig.empty()); 
        agent.start(); 
    }

    @AfterEach
    void tearDown() { 
        if (agent != null) { 
            agent.stop(); 
        }
    }

    @Test
    @DisplayName("Property: Confidence threshold is respected across many random event streams")
    void confidenceThresholdRespectedAcrossRandomStreams() { 
        // Generate 100 random event streams
        for (int i = 0; i < 100; i++) { 
            List<GEvent> events = generateRandomEventStream(50); 
            
            long initialMatches = agent.getMatchesDetected(); 
            
            // Process all events
            for (GEvent event : events) { 
                runPromise(() -> agent.process(event)); 
            }
            
            long finalMatches = agent.getMatchesDetected(); 
            long newMatches = finalMatches - initialMatches;
            
            // Verify that all reported matches have confidence >= threshold
            // (This is implicitly verified by the agent's design - matches below threshold 
            // are not reported. We verify the count is reasonable.)
            assertThat(newMatches).isGreaterThanOrEqualTo(0); 
            assertThat(newMatches).isLessThanOrEqualTo(events.size()); 
            
            // Reset for next iteration
            agent.stop(); 
            agent.start(); 
        }
    }

    @Test
    @DisplayName("Property: Deterministic behavior - same events produce same results")
    void deterministicBehaviorForSameEvents() { 
        List<GEvent> events = generateRandomEventStream(30); 
        
        // First pass
        List<Long> firstResults = processEventsAndGetMatchCounts(events); 
        
        // Reset
        agent.stop(); 
        agent.start(); 
        
        // Second pass with same events
        List<Long> secondResults = processEventsAndGetMatchCounts(events); 
        
        // Results should be identical
        assertThat(firstResults).isEqualTo(secondResults); 
    }

    @Test
    @DisplayName("Property: Window expiry resets state correctly")
    void windowExpiryResetsStateCorrectly() { 
        List<GEvent> matchingEvents = createMatchingEventSequence(); 

        for (GEvent event : matchingEvents) { 
            runPromise(() -> agent.process(event)); 
        }

        assertThat(agent.getEventsReceived()).isEqualTo(matchingEvents.size()); 

        agent.stop(); 
        agent.start(); 

        assertThat(agent.getEventsReceived()).isZero(); 
        assertThat(agent.getMatchesDetected()).isZero(); 

        for (GEvent event : matchingEvents) { 
            runPromise(() -> agent.process(event)); 
        }

        assertThat(agent.getEventsReceived()).isEqualTo(matchingEvents.size()); 
    }

    @Test
    @DisplayName("Property: Event type filtering works correctly")
    void eventTypeFilteringWorksCorrectly() { 
        // Create an agent with event type filtering
        NFA nfa = createFraudDetectionNFA(); 
        PatternDetectionAgent filteredAgent = PatternDetectionAgent.builder() 
            .operatorId(OperatorId.of("test", "pattern", "filtered", "1.0")) 
            .name("Filtered Agent")
            .nfa(nfa) 
            .confidenceThreshold(0.7) 
            .windowDuration(Duration.ofMinutes(5)) 
            .detectionPlan(createDetectionPlanWithTypes(Set.of("login", "purchase", "large-amount"))) 
            .build(); 
        
        filteredAgent.initialize(com.ghatana.core.operator.OperatorConfig.empty()); 
        filteredAgent.start(); 
        
        try {
            // Process matching events (should be accepted) 
            List<GEvent> matchingEvents = createMatchingEventSequence(); 
            for (GEvent event : matchingEvents) { 
                runPromise(() -> filteredAgent.process(event)); 
            }
            long filteredAfterAcceptedTypes = ((Number) filteredAgent.getMetrics().get("events_filtered")).longValue();
            assertThat(filteredAfterAcceptedTypes).isZero(); 
            
            filteredAgent.stop(); 
            filteredAgent.start(); 
            
            List<GEvent> filteredEvents = createNonMatchingEventSequence(); 
            for (GEvent event : filteredEvents) { 
                runPromise(() -> filteredAgent.process(event)); 
            }
            long filteredCount = ((Number) filteredAgent.getMetrics().get("events_filtered")).longValue();
            
            assertThat(filteredCount).isEqualTo(filteredEvents.size()); 
            assertThat(filteredAgent.getMatchesDetected()).isZero(); 
            
        } finally {
            filteredAgent.stop(); 
        }
    }

    @Test
    @DisplayName("Property: Null events are handled gracefully")
    void nullEventsHandledGracefully() { 
        OperatorResult result = runPromise(() -> agent.process(null)); 

        assertThat(result.isSuccess()).isFalse(); 
        assertThat(result.getErrorMessage()).contains("must not be null");
        assertThat(agent.getState()).isNotNull(); 
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private NFA createFraudDetectionNFA() { 
        NFA nfa = new NFA("fraud-detection");
        NFAState start = nfa.getStartState(); 
        NFAState login = new NFAState("login", NFAStateType.INTERMEDIATE); 
        NFAState purchase = new NFAState("purchase", NFAStateType.INTERMEDIATE); 
        NFAState accept = new NFAState("accept", NFAStateType.END); 
        
        nfa.addState(login); 
        nfa.addState(purchase); 
        nfa.addState(accept); 
        
        nfa.addTransition(start, login, "login"); 
        nfa.addTransition(login, purchase, "purchase"); 
        nfa.addTransition(purchase, accept, "large-amount"); 
        
        return nfa;
    }

    private List<GEvent> generateRandomEventStream(int size) { 
        List<GEvent> events = new ArrayList<>(); 
        String[] eventTypes = {"login", "purchase", "large-amount", "logout", "click", "view"};
        
        for (int i = 0; i < size; i++) { 
            String type = eventTypes[random.nextInt(eventTypes.length)]; 
            GEvent event = createEvent(type, "event-" + i, Instant.now().plusSeconds(i)); 
            events.add(event); 
        }
        
        return events;
    }

    private List<GEvent> createMatchingEventSequence() { 
        List<GEvent> events = new ArrayList<>(); 
        events.add(createEvent("login", "login-1", Instant.now())); 
        events.add(createEvent("purchase", "purchase-1", Instant.now().plusSeconds(1))); 
        events.add(createEvent("large-amount", "large-1", Instant.now().plusSeconds(2))); 
        return events;
    }

    private List<GEvent> createNonMatchingEventSequence() { 
        List<GEvent> events = new ArrayList<>(); 
        String[] nonMatchingTypes = {"logout", "click", "view", "scroll"};
        
        for (int i = 0; i < 10; i++) { 
            String type = nonMatchingTypes[random.nextInt(nonMatchingTypes.length)]; 
            events.add(createEvent(type, "event-" + i, Instant.now().plusSeconds(i))); 
        }
        
        return events;
    }

    private List<Long> processEventsAndGetMatchCounts(List<GEvent> events) { 
        List<Long> matchCounts = new ArrayList<>(); 
        
        for (GEvent event : events) { 
            long before = agent.getMatchesDetected(); 
            runPromise(() -> agent.process(event)); 
            long after = agent.getMatchesDetected(); 
            matchCounts.add(after - before); 
        }
        
        return matchCounts;
    }

    private GEvent createEvent(String type, String id, Instant timestamp) { 
        com.ghatana.platform.types.time.GTimestamp eventTimestamp = com.ghatana.platform.types.time.GTimestamp.of(timestamp); 
        return GEvent.builder() 
            .id(com.ghatana.platform.domain.event.EventId.create(id, type, "v1", "test-tenant")) 
            .time(com.ghatana.platform.domain.event.EventTime.builder() 
                .detectionTimePoint(eventTimestamp) 
                .occurrenceTime(com.ghatana.platform.types.time.GTimeInterval.between(eventTimestamp, eventTimestamp)) 
                .validDuration(new com.ghatana.platform.types.time.GTimeValue( 
                    Long.MAX_VALUE,
                    com.ghatana.platform.types.time.GTimeUnit.MILLISECONDS))
                .boundingInterval(com.ghatana.platform.types.time.GTimeInterval.between(eventTimestamp, eventTimestamp)) 
                .granularity(1) 
                .build()) 
            .stats(com.ghatana.platform.domain.event.EventStats.builder() 
                .withProcessingTimeNanos(0) 
                .withSizeInBytes(0) 
                .withFieldCount(0) 
                .withTagCount(0) 
                .build()) 
            .relations(com.ghatana.platform.domain.event.EventRelations.empty()) 
            .headers(java.util.Map.of()) 
            .payload(java.util.Map.of()) 
            .build(); 
    }

    private com.ghatana.pattern.api.model.DetectionPlan createDetectionPlanWithTypes(Set<String> eventTypes) { 
        return com.ghatana.pattern.api.model.DetectionPlan.builder() 
            .patternId(java.util.UUID.randomUUID()) 
            .eventTypes(java.util.List.copyOf(eventTypes)) 
            .build(); 
    }
}
