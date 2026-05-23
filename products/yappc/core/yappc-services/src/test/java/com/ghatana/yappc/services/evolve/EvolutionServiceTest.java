package com.ghatana.yappc.services.evolve;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.TenantContext;
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
@SuppressWarnings("unchecked")
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

        when(auditLogger.log(any(Map.class))) 
                .thenReturn(Promise.complete()); 

        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics); 
        Insights insights = Insights.builder() 
                .id("insights-123")
                .observationRef("obs-123")
                .build(); 

                // WHEN
                EvolutionPlan result = runPromise(() -> service.propose(insights)); 

                // THEN
                assertNotNull(result); 
                assertEquals("insights-123", result.insightsRef()); 

                verify(metrics, times(1)).incrementCounter(eq("yappc.ai.evolve.propose.fallback"), any(Map.class));
    }

    @Test
    void shouldPersistEvolutionProposalWhenRepositoryIsConfigured() {
        // GIVEN
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        EvolutionPlanRepository repository = mock(EvolutionPlanRepository.class);

        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Recommendation: Add rollback validation before promote")
                        .modelUsed("gpt-4")
                        .build()));
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete());
        when(repository.save(any(EvolutionPlanRepository.EvolutionProposal.class)))
                .thenReturn(Promise.complete());

        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));
        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics, repository);
        Insights insights = Insights.builder()
                .id("insights-123")
                .observationRef("project-123:obs-456")
                .patterns(List.of())
                .anomalies(List.of())
                .recommendations(List.of())
                .build();

        try {
            // WHEN
            EvolutionPlan result = runPromise(() -> service.propose(insights));

            // THEN
            assertNotNull(result);
            verify(repository).save(argThat(proposal ->
                    proposal.tenantId().equals("tenant-alpha")
                            && proposal.projectId().equals("project-123")
                            && proposal.approvalState().equals("PENDING_APPROVAL")
                            && proposal.productUnitIntentRef().equals(result.newIntentRef())
                            && proposal.provenance().contains("insights-123")));
        } finally {
            TenantContext.clear();
            runBlocking(TenantContext::clear);
        }
    }
}
