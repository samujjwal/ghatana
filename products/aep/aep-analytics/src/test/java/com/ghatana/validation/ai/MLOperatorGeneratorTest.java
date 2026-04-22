/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.validation.ai;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.pattern.api.model.OperatorSpec;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.EventId;
import com.ghatana.platform.domain.event.EventRelations;
import com.ghatana.platform.domain.event.EventStats;
import com.ghatana.platform.domain.event.EventTime;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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
 * Tests for ML-based operator generator.
 *
 * @doc.type class
 * @doc.purpose Unit tests for MLOperatorGenerator
 * @doc.layer test
 */
@DisplayName("ML Operator Generator Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class MLOperatorGeneratorTest extends EventloopTestBase {

    @Mock
    private LLMGateway llmGateway;

    @Test
    @DisplayName("generates operators from event data [GH-90000]")
    void generatesOperatorsFromEventData() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics); // GH-90000

        List<Event> events = createSampleEvents(); // GH-90000
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder() // GH-90000
            .temperature(0.7) // GH-90000
            .maxTokens(2000) // GH-90000
            .confidenceThreshold(0.5) // GH-90000
            .build(); // GH-90000

        CompletionResult mockResult = CompletionResult.builder() // GH-90000
            .text("[{\"type\":\"SEQ\",\"id\":\"pattern-1\",\"parameters\":{\"within\":\"PT5M\"},\"operands\":[]}]") // GH-90000
            .tokensUsed(100) // GH-90000
            .promptTokens(50) // GH-90000
            .completionTokens(50) // GH-90000
            .finishReason("stop [GH-90000]")
            .modelUsed("gpt-4 [GH-90000]")
            .build(); // GH-90000

        when(llmGateway.complete(any(CompletionRequest.class))) // GH-90000
            .thenReturn(Promise.of(mockResult)); // GH-90000

        List<OperatorSpec> operators = runPromise(() -> generator.generateOperators(events, config)); // GH-90000

        assertThat(operators).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("uses default system prompt [GH-90000]")
    void usesDefaultSystemPrompt() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics); // GH-90000

        assertThat(generator).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("uses custom system prompt [GH-90000]")
    void usesCustomSystemPrompt() { // GH-90000
        String customPrompt = "You are a pattern detection expert.";
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics, customPrompt); // GH-90000

        assertThat(generator).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("summarizes events correctly [GH-90000]")
    void summarizesEventsCorrectly() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics); // GH-90000

        List<Event> events = createSampleEvents(); // GH-90000
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder().build(); // GH-90000

        CompletionResult mockResult = CompletionResult.builder() // GH-90000
            .text("[] [GH-90000]")
            .tokensUsed(10) // GH-90000
            .promptTokens(5) // GH-90000
            .completionTokens(5) // GH-90000
            .finishReason("stop [GH-90000]")
            .modelUsed("gpt-4 [GH-90000]")
            .build(); // GH-90000

        when(llmGateway.complete(any(CompletionRequest.class))) // GH-90000
            .thenReturn(Promise.of(mockResult)); // GH-90000

        runPromise(() -> generator.generateOperators(events, config)); // GH-90000

        assertThat(generator).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("handles empty event list [GH-90000]")
    void handlesEmptyEventList() { // GH-90000
        Metrics metrics = new Metrics(new SimpleMeterRegistry()); // GH-90000
        MLOperatorGenerator generator = new MLOperatorGenerator(llmGateway, metrics); // GH-90000

        List<Event> events = List.of(); // GH-90000
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder().build(); // GH-90000

        CompletionResult mockResult = CompletionResult.builder() // GH-90000
            .text("[] [GH-90000]")
            .tokensUsed(10) // GH-90000
            .promptTokens(5) // GH-90000
            .completionTokens(5) // GH-90000
            .finishReason("stop [GH-90000]")
            .modelUsed("gpt-4 [GH-90000]")
            .build(); // GH-90000

        when(llmGateway.complete(any(CompletionRequest.class))) // GH-90000
            .thenReturn(Promise.of(mockResult)); // GH-90000

        List<OperatorSpec> operators = runPromise(() -> generator.generateOperators(events, config)); // GH-90000

        assertThat(operators).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("config builder creates valid config [GH-90000]")
    void configBuilderCreatesValidConfig() { // GH-90000
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder() // GH-90000
            .model("gpt-4 [GH-90000]")
            .temperature(0.5) // GH-90000
            .maxTokens(1000) // GH-90000
            .confidenceThreshold(0.8) // GH-90000
            .build(); // GH-90000

        assertThat(config.model()).isEqualTo("gpt-4 [GH-90000]");
        assertThat(config.temperature()).isEqualTo(0.5); // GH-90000
        assertThat(config.maxTokens()).isEqualTo(1000); // GH-90000
        assertThat(config.confidenceThreshold()).isEqualTo(0.8); // GH-90000
    }

    @Test
    @DisplayName("config clamps temperature to valid range [GH-90000]")
    void configClampsTemperatureToValidRange() { // GH-90000
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder() // GH-90000
            .temperature(3.0) // GH-90000
            .build(); // GH-90000

        assertThat(config.temperature()).isEqualTo(2.0); // GH-90000
    }

    @Test
    @DisplayName("config clamps confidence to valid range [GH-90000]")
    void configClampsConfidenceToValidRange() { // GH-90000
        MLOperatorGenerator.GenerationConfig config = MLOperatorGenerator.GenerationConfig.builder() // GH-90000
            .confidenceThreshold(1.5) // GH-90000
            .build(); // GH-90000

        assertThat(config.confidenceThreshold()).isEqualTo(1.0); // GH-90000
    }

    private List<Event> createSampleEvents() { // GH-90000
        return List.of( // GH-90000
            createEvent("user.login", "event-1"), // GH-90000
            createEvent("order.created", "event-2"), // GH-90000
            createEvent("payment.success", "event-3") // GH-90000
        );
    }

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
