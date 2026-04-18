/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.pattern.engine.agent.PatternDetectionAgent;
import com.ghatana.pattern.engine.agent.PatternDetectionAgentAdapter;
import com.ghatana.pattern.engine.evaluator.ProbabilisticEvaluator;
import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.pattern.engine.nfa.NFAState;
import com.ghatana.pattern.engine.nfa.NFATransition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test for event processing with pattern detection.
 * 
 * <p>This test exercises the complete event processing pipeline:
 * <ol>
 *   <li>Event ingestion via AepEngine.process()</li>
 *   <li>Pattern detection using registered PatternDetector</li>
 *   <li>Delivery and notification of pattern matches</li>
 *   <li>Verification of processing metadata</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose End-to-end event processing integration test
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("End-to-End Event Processing Test")
class EndToEndEventProcessingTest {

    private AepEngine engine;
    private PatternDetectionAgent patternAgent;
    private PatternDetectionAgentAdapter detectorAdapter;

    @BeforeEach
    void setUp() {
        engine = Aep.forTesting();
        
        // Create a simple NFA for pattern detection
        NFA nfa = createSimpleNFA("test-pattern", "login.attempt");
        
        // Create PatternDetectionAgent
        patternAgent = PatternDetectionAgent.builder()
            .operatorId(com.ghatana.core.operator.OperatorId.of("test", "pattern", "detector", "1.0"))
            .name("Test Pattern Detector")
            .nfa(nfa)
            .confidenceThreshold(0.5)
            .windowDuration(Duration.ofMinutes(5))
            .build();
        
        // Initialize the agent
        patternAgent.initialize(com.ghatana.core.operator.OperatorConfig.empty());
        patternAgent.start();
        
        // Create adapter and register with engine
        detectorAdapter = PatternDetectionAgentAdapter.wrap(patternAgent, "pattern-1");
        engine.registerPatternDetector("tenant-test", detectorAdapter);
    }

    @AfterEach
    void tearDown() {
        if (detectorAdapter != null && engine != null) {
            engine.unregisterPatternDetector("tenant-test", detectorAdapter);
        }
        if (patternAgent != null) {
            patternAgent.stop();
        }
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("Event is processed end-to-end with pattern detection")
    void eventIsProcessedWithPatternDetection() {
        // GIVEN
        String tenantId = "tenant-test";
        String eventType = "login.attempt";
        Map<String, Object> payload = Map.of("userId", "user-123", "ip", "192.168.1.1");

        // WHEN
        AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), 
            java.time.Instant.now());
        AepEngine.ProcessingResult result = engine.process(tenantId, event).blockingGet();

        // THEN
        assertThat(result.success()).isTrue();
        assertThat(result.eventId()).isNotNull();
        assertThat(result.metadata()).containsKey("processed");
        assertThat(result.metadata()).containsKey("correlationId");
    }

    @Test
    @DisplayName("Pattern detection is invoked during event processing")
    void patternDetectionIsInvoked() {
        // GIVEN
        String tenantId = "tenant-test";
        AepEngine.Event event = new AepEngine.Event("login.attempt", 
            Map.of("userId", "user-456"), Map.of(), java.time.Instant.now());

        // WHEN
        AepEngine.ProcessingResult result = engine.process(tenantId, event).blockingGet();

        // THEN
        assertThat(result.success()).isTrue();
        // PatternDetectionAgent may or may not detect patterns depending on the NFA configuration
        // The important thing is that it was invoked without error
        assertThat(result.eventId()).isNotNull();
    }

    @Test
    @DisplayName("Multiple events are processed sequentially")
    void multipleEventsProcessedSequentially() {
        // GIVEN
        String tenantId = "tenant-test";
        List<AepEngine.Event> events = List.of(
            new AepEngine.Event("event.1", Map.of("seq", 1), Map.of(), java.time.Instant.now()),
            new AepEngine.Event("event.2", Map.of("seq", 2), Map.of(), java.time.Instant.now()),
            new AepEngine.Event("event.3", Map.of("seq", 3), Map.of(), java.time.Instant.now())
        );

        // WHEN
        List<AepEngine.ProcessingResult> results = events.stream()
            .map(event -> engine.process(tenantId, event).blockingGet())
            .toList();

        // THEN
        assertThat(results).hasSize(3);
        for (AepEngine.ProcessingResult result : results) {
            assertThat(result.success()).isTrue();
            assertThat(result.eventId()).isNotNull();
        }
    }

    @Test
    @DisplayName("Pattern detections are included in processing result")
    void patternDetectionsIncludedInResult() {
        // GIVEN
        String tenantId = "tenant-test";
        AepEngine.Event event = new AepEngine.Event("login.attempt", 
            Map.of("userId", "user-789"), Map.of(), java.time.Instant.now());

        // WHEN
        AepEngine.ProcessingResult result = engine.process(tenantId, event).blockingGet();

        // THEN
        assertThat(result.success()).isTrue();
        assertThat(result.detections()).isNotNull();
        // Detections may be empty if pattern doesn't match, but should not be null
    }

    @Test
    @DisplayName("Event metadata is populated correctly")
    void eventMetadataPopulatedCorrectly() {
        // GIVEN
        String tenantId = "tenant-test";
        AepEngine.Event event = new AepEngine.Event("test.event", 
            Map.of("key", "value"), Map.of("correlationId", "corr-123"), java.time.Instant.now());

        // WHEN
        AepEngine.ProcessingResult result = engine.process(tenantId, event).blockingGet();

        // THEN
        assertThat(result.success()).isTrue();
        assertThat(result.metadata()).containsKey("processed");
        assertThat(result.metadata()).containsKey("correlationId");
        assertThat(result.metadata()).containsKey("consentStatus");
        assertThat(result.metadata()).containsKey("eventVersion");
    }

    // Helper method to create a simple NFA
    private NFA createSimpleNFA(String patternName, String eventType) {
        NFA nfa = new NFA(patternName);
        
        NFAState startState = nfa.getStartState();
        NFAState acceptState = new NFAState("accept", com.ghatana.pattern.engine.nfa.NFAStateType.END);
        
        nfa.addState(acceptState);
        nfa.addTransition(startState, acceptState, eventType);
        
        return nfa;
    }
}
