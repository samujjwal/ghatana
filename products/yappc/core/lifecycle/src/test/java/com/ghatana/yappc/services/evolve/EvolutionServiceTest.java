package com.ghatana.yappc.services.evolve;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.learn.Insights;
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
 * @doc.purpose Tests for EvolutionService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
class EvolutionServiceTest extends EventloopTestBase {
    
    @Test
    void shouldProposeEvolutionPlan() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Recommendation: Optimize database queries")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics);
        Insights insights = Insights.builder()
                .id("insights-123")
                .observationRef("obs-123")
                .patterns(List.of())
                .anomalies(List.of())
                .recommendations(List.of())
                .build();
        
        // WHEN
        EvolutionPlan result = runPromise(() -> service.propose(insights));
        
        // THEN
        assertNotNull(result);
        assertNotNull(result.id());
        assertEquals("insights-123", result.insightsRef());
        assertNotNull(result.tasks());
        assertNotNull(result.createdAt());
        
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class));
        verify(auditLogger, times(1)).log(any(Map.class));
    }
    
    @Test
    void shouldPrioritizeTasks() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("High priority: Fix memory leak\nMedium priority: Optimize queries")
                        .modelUsed("gpt-4")
                        .build()));
        
        when(auditLogger.log(any(Map.class)))
                .thenReturn(Promise.complete());
        
        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics);
        Insights insights = Insights.builder()
                .id("insights-123")
                .observationRef("obs-123")
                .patterns(List.of())
                .anomalies(List.of())
                .recommendations(List.of())
                .build();
        
        // WHEN
        EvolutionPlan result = runPromise(() -> service.propose(insights));
        
        // THEN
        assertNotNull(result);
        assertFalse(result.tasks().isEmpty());
        assertFalse(result.tasks().isEmpty());
    }
    
    @Test
    void shouldHandleProposalFailure() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Proposal failed")));
        
        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics);
        Insights insights = Insights.builder()
                .id("insights-123")
                .observationRef("obs-123")
                .build();
        
        // WHEN/THEN
        try {
            runPromise(() -> service.propose(insights));
            fail("Expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Proposal failed"));
        }
        
        verify(metrics, times(1)).incrementCounter(eq("yappc.evolve.propose.error"), any(Map.class));
    }
}
