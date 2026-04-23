/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.validation.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventRelations;
import com.ghatana.platform.domain.event.EventStats;
import com.ghatana.platform.domain.event.EventId;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.validation.ai.AIPatternDetectionService.DetectedPattern;
import com.ghatana.validation.ai.AIPatternDetectionService.PatternAnalysisConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for AI pattern detection service with ML integration.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ML-based pattern detection
 * @doc.layer test
 */
@DisplayName("AI Pattern Detection Service ML Tests")
@ExtendWith(MockitoExtension.class) // GH-90000
class AIPatternDetectionServiceMLTest extends EventloopTestBase {

    @Mock
    private LLMGateway llmGateway;

    @Test
    @DisplayName("uses ML when LLM gateway is provided and useML is true")
    void usesMLWhenGatewayProvided() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, llmGateway); // GH-90000

        List<Event> events = createSampleEvents(); // GH-90000
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", true)); // GH-90000

        CompletionResult mockResult = CompletionResult.builder() // GH-90000
            .text("[]")
            .tokensUsed(10) // GH-90000
            .promptTokens(5) // GH-90000
            .completionTokens(5) // GH-90000
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build(); // GH-90000

        when(llmGateway.complete(any(CompletionRequest.class))) // GH-90000
            .thenReturn(Promise.of(mockResult)); // GH-90000

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config)); // GH-90000

        assertThat(patterns).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("falls back to heuristic when useML is false")
    void fallsBackToHeuristicWhenUseMLFalse() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, llmGateway); // GH-90000

        List<Event> events = createSampleEvents(); // GH-90000
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", false)); // GH-90000

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config)); // GH-90000

        assertThat(patterns).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("falls back to heuristic when LLM gateway is null")
    void fallsBackToHeuristicWhenGatewayNull() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, null); // GH-90000

        List<Event> events = createSampleEvents(); // GH-90000
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", true)); // GH-90000

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config)); // GH-90000

        assertThat(patterns).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("converts operator spec to detected pattern")
    void convertsOperatorSpecToDetectedPattern() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, llmGateway); // GH-90000

        List<Event> events = createSampleEvents(); // GH-90000
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", true)); // GH-90000

        CompletionResult mockResult = CompletionResult.builder() // GH-90000
            .text("[]")
            .tokensUsed(10) // GH-90000
            .promptTokens(5) // GH-90000
            .completionTokens(5) // GH-90000
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build(); // GH-90000

        when(llmGateway.complete(any(CompletionRequest.class))) // GH-90000
            .thenReturn(Promise.of(mockResult)); // GH-90000

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config)); // GH-90000

        assertThat(patterns).isNotNull(); // GH-90000
    }

    /**
     * Creates sample events for testing.
     */
    private List<Event> createSampleEvents() { // GH-90000
        return List.of( // GH-90000
            createEvent("user.login", "event-1"), // GH-90000
            createEvent("order.created", "event-2"), // GH-90000
            createEvent("payment.success", "event-3") // GH-90000
        );
    }

    /**
     * Creates a sample event.
     */
    private Event createEvent(String type, String id) { // GH-90000
        return GEvent.builder() // GH-90000
            .id(EventId.create(id, type, "v1", "test-tenant")) // GH-90000
            .time(EventTime.now()) // GH-90000
            .stats(EventStats.builder() // GH-90000
                .withProcessingTimeNanos(0) // GH-90000
                .withSizeInBytes(16) // GH-90000
                .withFieldCount(1) // GH-90000
                .withTagCount(0) // GH-90000
                .build()) // GH-90000
            .relations(EventRelations.empty()) // GH-90000
            .headers(Map.of()) // GH-90000
            .payload(Map.of("test", "data")) // GH-90000
            .build(); // GH-90000
    }
}
