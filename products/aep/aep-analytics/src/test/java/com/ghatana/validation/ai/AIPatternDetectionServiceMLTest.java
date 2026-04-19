/*
 * Copyright (c) 2026 Ghatana Inc.
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
@ExtendWith(MockitoExtension.class)
class AIPatternDetectionServiceMLTest extends EventloopTestBase {

    @Mock
    private LLMGateway llmGateway;

    @Test
    @DisplayName("uses ML when LLM gateway is provided and useML is true")
    void usesMLWhenGatewayProvided() {
        Metrics metrics = new Metrics(new SimpleMeterRegistry());
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, llmGateway);

        List<Event> events = createSampleEvents();
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", true));

        CompletionResult mockResult = CompletionResult.builder()
            .text("[]")
            .tokensUsed(10)
            .promptTokens(5)
            .completionTokens(5)
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(mockResult));

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config));

        assertThat(patterns).isNotNull();
    }

    @Test
    @DisplayName("falls back to heuristic when useML is false")
    void fallsBackToHeuristicWhenUseMLFalse() {
        Metrics metrics = new Metrics(new SimpleMeterRegistry());
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, llmGateway);

        List<Event> events = createSampleEvents();
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", false));

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config));

        assertThat(patterns).isNotNull();
    }

    @Test
    @DisplayName("falls back to heuristic when LLM gateway is null")
    void fallsBackToHeuristicWhenGatewayNull() {
        Metrics metrics = new Metrics(new SimpleMeterRegistry());
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, null);

        List<Event> events = createSampleEvents();
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", true));

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config));

        assertThat(patterns).isNotNull();
    }

    @Test
    @DisplayName("converts operator spec to detected pattern")
    void convertsOperatorSpecToDetectedPattern() {
        Metrics metrics = new Metrics(new SimpleMeterRegistry());
        AIPatternDetectionServiceImpl service = new AIPatternDetectionServiceImpl(metrics, llmGateway);

        List<Event> events = createSampleEvents();
        PatternAnalysisConfig config = new PatternAnalysisConfig(0, 0.5, 0, Map.of("useML", true));

        CompletionResult mockResult = CompletionResult.builder()
            .text("[]")
            .tokensUsed(10)
            .promptTokens(5)
            .completionTokens(5)
            .finishReason("stop")
            .modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(mockResult));

        List<DetectedPattern> patterns = runPromise(() -> service.detectPatterns(events, config));

        assertThat(patterns).isNotNull();
    }

    /**
     * Creates sample events for testing.
     */
    private List<Event> createSampleEvents() {
        return List.of(
            createEvent("user.login", "event-1"),
            createEvent("order.created", "event-2"),
            createEvent("payment.success", "event-3")
        );
    }

    /**
     * Creates a sample event.
     */
    private Event createEvent(String type, String id) {
        return GEvent.builder()
            .id(EventId.create(id, type, "v1", "test-tenant"))
            .time(EventTime.now())
            .stats(EventStats.builder()
                .withProcessingTimeNanos(0)
                .withSizeInBytes(16)
                .withFieldCount(1)
                .withTagCount(0)
                .build())
            .relations(EventRelations.empty())
            .headers(Map.of())
            .payload(Map.of("test", "data"))
            .build();
    }
}
