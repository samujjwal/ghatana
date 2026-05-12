/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TraceEventType mastery/learning trace events.
 *
 * @doc.type class
 * @doc.purpose Test mastery and learning trace event types
 * @doc.layer test
 */
@DisplayName("TraceEventType Mastery/Learning Events Tests")
public class TraceEventTypeMasteryTest {

    @Test
    @DisplayName("Should include MASTERY_STATE_CHANGED trace event type")
    void testIncludeMasteryStateChangedEvent() {
        TraceEventType eventType = TraceEventType.valueOf("MASTERY_STATE_CHANGED");
        assertNotNull(eventType);
        assertEquals("MASTERY_STATE_CHANGED", eventType.name());
    }

    @Test
    @DisplayName("Should include LEARNING_DELTA_PROPOSED trace event type")
    void testIncludeLearningDeltaProposedEvent() {
        TraceEventType eventType = TraceEventType.valueOf("LEARNING_DELTA_PROPOSED");
        assertNotNull(eventType);
        assertEquals("LEARNING_DELTA_PROPOSED", eventType.name());
    }

    @Test
    @DisplayName("Should include LEARNING_DELTA_PROMOTED trace event type")
    void testIncludeLearningDeltaPromotedEvent() {
        TraceEventType eventType = TraceEventType.valueOf("LEARNING_DELTA_PROMOTED");
        assertNotNull(eventType);
        assertEquals("LEARNING_DELTA_PROMOTED", eventType.name());
    }

    @Test
    @DisplayName("Should include LEARNING_DELTA_REJECTED trace event type")
    void testIncludeLearningDeltaRejectedEvent() {
        TraceEventType eventType = TraceEventType.valueOf("LEARNING_DELTA_REJECTED");
        assertNotNull(eventType);
        assertEquals("LEARNING_DELTA_REJECTED", eventType.name());
    }

    @Test
    @DisplayName("Should include SKILL_BENCHMARK_COMPLETED trace event type")
    void testIncludeSkillBenchmarkCompletedEvent() {
        TraceEventType eventType = TraceEventType.valueOf("SKILL_BENCHMARK_COMPLETED");
        assertNotNull(eventType);
        assertEquals("SKILL_BENCHMARK_COMPLETED", eventType.name());
    }

    @Test
    @DisplayName("Should include all existing trace event types")
    void testIncludeAllExistingEventTypes() {
        // Verify existing event types are still present
        assertNotNull(TraceEventType.valueOf("ACTION_EXECUTED"));
        assertNotNull(TraceEventType.valueOf("ACTION_DENIED"));
        assertNotNull(TraceEventType.valueOf("APPROVAL_REQUESTED"));
        assertNotNull(TraceEventType.valueOf("APPROVAL_GRANTED"));
        assertNotNull(TraceEventType.valueOf("APPROVAL_REJECTED"));
        assertNotNull(TraceEventType.valueOf("POLICY_EVALUATED"));
        assertNotNull(TraceEventType.valueOf("MEMORY_MUTATION"));
        assertNotNull(TraceEventType.valueOf("DELEGATION"));
        assertNotNull(TraceEventType.valueOf("INVARIANT_PASSED"));
        assertNotNull(TraceEventType.valueOf("INVARIANT_VIOLATED"));
        assertNotNull(TraceEventType.valueOf("TURN_STARTED"));
        assertNotNull(TraceEventType.valueOf("TURN_COMPLETED"));
        assertNotNull(TraceEventType.valueOf("KILL_SWITCH_ACTIVATED"));
        assertNotNull(TraceEventType.valueOf("BUDGET_ALERT"));
    }

    @Test
    @DisplayName("Should have correct number of trace event types")
    void testCorrectNumberOfEventTypes() {
        TraceEventType[] eventTypes = TraceEventType.values();
        // Should have original 14 + 5 new mastery/learning events = 19
        assertEquals(19, eventTypes.length);
    }

    @Test
    @DisplayName("Should allow enum values() to include mastery events")
    void testEnumValuesIncludesMasteryEvents() {
        TraceEventType[] eventTypes = TraceEventType.values();
        
        assertTrue(java.util.Arrays.stream(eventTypes)
                .anyMatch(e -> e.name().equals("MASTERY_STATE_CHANGED")));
        assertTrue(java.util.Arrays.stream(eventTypes)
                .anyMatch(e -> e.name().equals("LEARNING_DELTA_PROPOSED")));
        assertTrue(java.util.Arrays.stream(eventTypes)
                .anyMatch(e -> e.name().equals("LEARNING_DELTA_PROMOTED")));
        assertTrue(java.util.Arrays.stream(eventTypes)
                .anyMatch(e -> e.name().equals("LEARNING_DELTA_REJECTED")));
        assertTrue(java.util.Arrays.stream(eventTypes)
                .anyMatch(e -> e.name().equals("SKILL_BENCHMARK_COMPLETED")));
    }
}
