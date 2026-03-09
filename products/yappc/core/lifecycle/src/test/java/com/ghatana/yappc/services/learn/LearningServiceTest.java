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
    void shouldAnalyzeObservations() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Pattern detected: High latency during peak hours")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        LearningService service = new LearningServiceImpl(aiService, auditLogger, metrics);
        Observation observation = Observation.builder()
                .id("obs-123")
                .runRef("run-123")
                .metrics(List.of())
                .logs(List.of())
                .traces(List.of())
                .build();
        
        // WHEN
        Insights result = runPromise(() -> service.analyze(observation));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("obs-123", result.observationRef());
        assertNotNull(result.patterns());
        assertNotNull(result.anomalies());
        assertNotNull(result.recommendations());
        
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class));
        verify(auditLogger, times(1)).log(any(Map.class));
    }
    
    @Test
    void shouldDetectPatterns() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Recurring pattern: Memory leak in service X")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        LearningService service = new LearningServiceImpl(aiService, auditLogger, metrics);
        Observation observation = Observation.builder()
                .id("obs-123")
                .runRef("run-123")
                .metrics(List.of())
                .logs(List.of())
                .traces(List.of())
                .build();
        
        // WHEN
        Insights result = runPromise(() -> service.analyze(observation));
        
        // THEN
        assertNotNull(result);
        assertFalse(result.patterns().isEmpty());
    }
    
    @Test
    void shouldHandleAnalysisFailure() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Analysis failed")));
        
        LearningService service = new LearningServiceImpl(aiService, auditLogger, metrics);
        Observation observation = Observation.builder()
                .id("obs-123")
                .runRef("run-123")
                .build();
        
        // WHEN/THEN
        try {
            runPromise(() -> service.analyze(observation));
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Analysis failed"));
        }
        
        verify(metrics, times(1)).incrementCounter(eq("yappc.learn.analyze.error"), any(Map.class));
    }
}
