/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.consolidation;

import com.ghatana.agent.memory.model.episode.EnhancedEpisode;
import com.ghatana.agent.memory.model.fact.EnhancedFact;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.testing.RecordingMetricsCollector;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Validates that {@link DefaultLLMFactExtractor} surfaces learning quality state
 * through metrics and structured ERROR-level logging instead of silently degrading.
 *
 * <p>Every test exercises a failure mode and asserts that the corresponding metric
 * is incremented so that the degradation is observable in dashboards/alerts.
 *
 * @doc.type class
 * @doc.purpose Regression tests for learning-quality observability in DefaultLLMFactExtractor
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("DefaultLLMFactExtractor learning quality metrics tests")
@ExtendWith(MockitoExtension.class)
class DefaultLLMFactExtractorQualityMetricsTest extends EventloopTestBase {

    @Mock
    private LLMGateway llmGateway;

    private EnhancedEpisode buildEpisode() {
        return EnhancedEpisode.builder()
            .id("episode-1")
            .agentId("agent-test")
            .tenantId("tenant-test")
            .turnId("turn-1")
            .input("What is the capital of France?")
            .output("The capital of France is Paris.")
            .build();
    }

    @Test
    @DisplayName("increments facts_extracted metric on successful extraction")
    void incrementsFactsExtractedMetricOnSuccess() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        DefaultLLMFactExtractor extractor = new DefaultLLMFactExtractor(llmGateway, collector);
        EnhancedEpisode episode = buildEpisode();

        CompletionResult result = CompletionResult.builder()
            .text("{\"facts\":[" +
                  "{\"subject\":\"France\",\"predicate\":\"has capital\",\"object\":\"Paris\",\"confidence\":0.99}" +
                  "]}")
            .tokensUsed(80).promptTokens(50).completionTokens(30)
            .finishReason("stop").modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class))).thenReturn(Promise.of(result));

        List<EnhancedFact> facts = runPromise(() -> extractor.extractFacts(episode));

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).getSubject()).isEqualTo("France");
        assertThat(counterTotal(collector, "llm.fact_extractor.facts_extracted")).isEqualTo(1);
    }

    @Test
    @DisplayName("increments empty_response metric when LLM returns zero facts")
    void incrementsEmptyResponseMetricWhenZeroFacts() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        DefaultLLMFactExtractor extractor = new DefaultLLMFactExtractor(llmGateway, collector);
        EnhancedEpisode episode = buildEpisode();

        CompletionResult result = CompletionResult.builder()
            .text("{\"facts\":[]}")
            .tokensUsed(20).promptTokens(15).completionTokens(5)
            .finishReason("stop").modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class))).thenReturn(Promise.of(result));

        List<EnhancedFact> facts = runPromise(() -> extractor.extractFacts(episode));

        assertThat(facts).isEmpty();
        assertThat(counterTotal(collector, "llm.fact_extractor.empty_response")).isEqualTo(1);
    }

    @Test
    @DisplayName("increments parse_failure metric on malformed JSON response")
    void incrementsParseFailureMetricOnMalformedJson() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        DefaultLLMFactExtractor extractor = new DefaultLLMFactExtractor(llmGateway, collector);
        EnhancedEpisode episode = buildEpisode();

        CompletionResult result = CompletionResult.builder()
            .text("This is not valid JSON at all - the LLM went off-script.")
            .tokensUsed(20).promptTokens(10).completionTokens(10)
            .finishReason("stop").modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class))).thenReturn(Promise.of(result));

        List<EnhancedFact> facts = runPromise(() -> extractor.extractFacts(episode));

        assertThat(facts).isNotNull().isEmpty();
        assertThat(counterTotal(collector, "llm.fact_extractor.parse_failure")).isEqualTo(1);
    }

    @Test
    @DisplayName("increments failure metric on LLM gateway exception")
    void incrementsFailureMetricOnLlmException() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        DefaultLLMFactExtractor extractor = new DefaultLLMFactExtractor(llmGateway, collector);
        EnhancedEpisode episode = buildEpisode();

        when(llmGateway.complete(any(CompletionRequest.class)))
            .thenThrow(new RuntimeException("LLM gateway unavailable"));

        List<EnhancedFact> facts = runPromise(() -> extractor.extractFacts(episode));

        assertThat(facts).isNotNull().isEmpty();
        assertThat(counterTotal(collector, "llm.fact_extractor.failure")).isEqualTo(1);
    }

    @Test
    @DisplayName("extracts multiple facts and reports correct count metric")
    void extractsMultipleFactsAndReportsCount() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        DefaultLLMFactExtractor extractor = new DefaultLLMFactExtractor(llmGateway, collector);
        EnhancedEpisode episode = buildEpisode();

        CompletionResult result = CompletionResult.builder()
            .text("{\"facts\":[" +
                  "{\"subject\":\"France\",\"predicate\":\"has capital\",\"object\":\"Paris\",\"confidence\":0.99}," +
                  "{\"subject\":\"Paris\",\"predicate\":\"is located in\",\"object\":\"Europe\",\"confidence\":0.95}," +
                  "{\"subject\":\"France\",\"predicate\":\"is a\",\"object\":\"country\",\"confidence\":0.98}" +
                  "]}")
            .tokensUsed(150).promptTokens(80).completionTokens(70)
            .finishReason("stop").modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class))).thenReturn(Promise.of(result));

        List<EnhancedFact> facts = runPromise(() -> extractor.extractFacts(episode));

        assertThat(facts).hasSize(3);
        assertThat(counterTotal(collector, "llm.fact_extractor.facts_extracted")).isEqualTo(3);
    }

    @Test
    @DisplayName("skips malformed triples and does not count them")
    void skipsMalformedTriplesWithoutCounting() {
        RecordingMetricsCollector collector = new RecordingMetricsCollector();

        DefaultLLMFactExtractor extractor = new DefaultLLMFactExtractor(llmGateway, collector);
        EnhancedEpisode episode = buildEpisode();

        CompletionResult result = CompletionResult.builder()
            .text("{\"facts\":[" +
                  "{\"subject\":\"France\",\"predicate\":\"has capital\",\"object\":\"Paris\",\"confidence\":0.99}," +
                  "{\"subject\":\"\",\"predicate\":\"broken\",\"object\":\"triple\",\"confidence\":0.5}," +
                  "{\"subject\":\"Paris\",\"predicate\":\"\",\"object\":\"Europe\",\"confidence\":0.8}" +
                  "]}")
            .tokensUsed(80).promptTokens(50).completionTokens(30)
            .finishReason("stop").modelUsed("gpt-4")
            .build();

        when(llmGateway.complete(any(CompletionRequest.class))).thenReturn(Promise.of(result));

        List<EnhancedFact> facts = runPromise(() -> extractor.extractFacts(episode));

        assertThat(facts).hasSize(1);
        assertThat(counterTotal(collector, "llm.fact_extractor.facts_extracted")).isEqualTo(1);
        assertThat(counterTotal(collector, "llm.fact_extractor.empty_response")).isEqualTo(0);
    }

    private static int counterTotal(RecordingMetricsCollector collector, String metricName) {
        return collector.getCounters(metricName).stream()
            .map(RecordingMetricsCollector.MetricRecord::value)
            .filter(Number.class::isInstance)
            .map(Number.class::cast)
            .mapToInt(Number::intValue)
            .sum();
    }
}
