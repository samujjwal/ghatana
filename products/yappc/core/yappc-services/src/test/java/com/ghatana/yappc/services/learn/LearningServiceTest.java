package com.ghatana.yappc.services.learn;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.observe.Observation;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for LearningService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class LearningServiceTest extends EventloopTestBase {

    @Test
    void shouldAnalyzeObservations() { // GH-90000
        // GIVEN
        CompletionService aiService = mock(CompletionService.class); // GH-90000
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text("Pattern detected: High latency during peak hours [GH-90000]")
                        .modelUsed("gpt-4 [GH-90000]")
                        .build())); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        LearningService service = new LearningServiceImpl(aiService, auditLogger, metrics); // GH-90000
        Observation observation = Observation.builder() // GH-90000
                .id("obs-123 [GH-90000]")
                .runRef("run-123 [GH-90000]")
                .metrics(List.of()) // GH-90000
                .logs(List.of()) // GH-90000
                .traces(List.of()) // GH-90000
                .build(); // GH-90000

        // WHEN
        Insights result = runPromise(() -> service.analyze(observation)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertNotNull(result.id()); // GH-90000
        assertEquals("obs-123", result.observationRef()); // GH-90000
        assertNotNull(result.patterns()); // GH-90000
        assertNotNull(result.anomalies()); // GH-90000
        assertNotNull(result.recommendations()); // GH-90000

        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class)); // GH-90000
        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    void shouldDetectPatterns() { // GH-90000
        // GIVEN
        CompletionService aiService = mock(CompletionService.class); // GH-90000
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text("Recurring pattern: Memory leak in service X [GH-90000]")
                        .modelUsed("gpt-4 [GH-90000]")
                        .build())); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        LearningService service = new LearningServiceImpl(aiService, auditLogger, metrics); // GH-90000
        Observation observation = Observation.builder() // GH-90000
                .id("obs-123 [GH-90000]")
                .runRef("run-123 [GH-90000]")
                .metrics(List.of()) // GH-90000
                .logs(List.of()) // GH-90000
                .traces(List.of()) // GH-90000
                .build(); // GH-90000

        // WHEN
        Insights result = runPromise(() -> service.analyze(observation)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertFalse(result.patterns().isEmpty()); // GH-90000
    }

    @Test
    void shouldHandleAnalysisFailure() { // GH-90000
        // GIVEN
        CompletionService aiService = mock(CompletionService.class); // GH-90000
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Analysis failed [GH-90000]")));

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        LearningService service = new LearningServiceImpl(aiService, auditLogger, metrics); // GH-90000
        Observation observation = Observation.builder() // GH-90000
                .id("obs-123 [GH-90000]")
                .runRef("run-123 [GH-90000]")
                .build(); // GH-90000

                // WHEN
                Insights result = runPromise(() -> service.analyze(observation)); // GH-90000

                // THEN
                assertNotNull(result); // GH-90000
                assertEquals("obs-123", result.observationRef()); // GH-90000

                verify(metrics, times(1)).incrementCounter(eq("yappc.ai.learn.analyze.fallback [GH-90000]"), any(Map.class));
    }
}
