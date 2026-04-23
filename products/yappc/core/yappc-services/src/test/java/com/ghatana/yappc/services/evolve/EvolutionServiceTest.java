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
    void shouldProposeEvolutionPlan() { // GH-90000
        // GIVEN
        CompletionService aiService = mock(CompletionService.class); // GH-90000
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text("Recommendation: Optimize database queries")
                        .modelUsed("gpt-4")
                        .build())); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics); // GH-90000
        Insights insights = Insights.builder() // GH-90000
                .id("insights-123")
                .observationRef("obs-123")
                .patterns(List.of()) // GH-90000
                .anomalies(List.of()) // GH-90000
                .recommendations(List.of()) // GH-90000
                .build(); // GH-90000

        // WHEN
        EvolutionPlan result = runPromise(() -> service.propose(insights)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertNotNull(result.id()); // GH-90000
        assertEquals("insights-123", result.insightsRef()); // GH-90000
        assertNotNull(result.tasks()); // GH-90000
        assertNotNull(result.createdAt()); // GH-90000

        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class)); // GH-90000
        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    void shouldPrioritizeTasks() { // GH-90000
        // GIVEN
        CompletionService aiService = mock(CompletionService.class); // GH-90000
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text("High priority: Fix memory leak\nMedium priority: Optimize queries")
                        .modelUsed("gpt-4")
                        .build())); // GH-90000

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics); // GH-90000
        Insights insights = Insights.builder() // GH-90000
                .id("insights-123")
                .observationRef("obs-123")
                .patterns(List.of()) // GH-90000
                .anomalies(List.of()) // GH-90000
                .recommendations(List.of()) // GH-90000
                .build(); // GH-90000

        // WHEN
        EvolutionPlan result = runPromise(() -> service.propose(insights)); // GH-90000

        // THEN
        assertNotNull(result); // GH-90000
        assertFalse(result.tasks().isEmpty()); // GH-90000
        assertFalse(result.tasks().isEmpty()); // GH-90000
    }

    @Test
    void shouldHandleProposalFailure() { // GH-90000
        // GIVEN
        CompletionService aiService = mock(CompletionService.class); // GH-90000
        AuditLogger auditLogger = mock(AuditLogger.class); // GH-90000
        MetricsCollector metrics = mock(MetricsCollector.class); // GH-90000

        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Proposal failed")));

        when(auditLogger.log(any(Map.class))) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000

        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics); // GH-90000
        Insights insights = Insights.builder() // GH-90000
                .id("insights-123")
                .observationRef("obs-123")
                .build(); // GH-90000

                // WHEN
                EvolutionPlan result = runPromise(() -> service.propose(insights)); // GH-90000

                // THEN
                assertNotNull(result); // GH-90000
                assertEquals("insights-123", result.insightsRef()); // GH-90000

                verify(metrics, times(1)).incrementCounter(eq("yappc.ai.evolve.propose.fallback"), any(Map.class));
    }
}
