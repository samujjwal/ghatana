package com.ghatana.yappc.services.evolve;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.evolve.EvolutionPlan;
import com.ghatana.yappc.domain.learn.Insights;
import com.ghatana.yappc.domain.learn.Pattern;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        EvolutionImpactAnalysisService impactAnalysisService = mock(EvolutionImpactAnalysisService.class);

        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Recommendation: Add rollback validation before promote")
                        .modelUsed("gpt-4")
                        .build()));
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete());
        when(repository.save(any(EvolutionPlanRepository.EvolutionProposal.class)))
                .thenReturn(Promise.complete());
        when(impactAnalysisService.analyze(any(EvolutionImpactAnalysisService.ImpactAnalysisRequest.class)))
                .thenReturn(Promise.of(new EvolutionImpactAnalysis(
                        "READY",
                        "artifact-graph",
                        List.of("web"),
                        List.of("checkout"),
                        List.of("checkout validation"),
                        List.of("preview"),
                        List.of("node-checkout"),
                        List.of())));

        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));
        EvolutionService service = new EvolutionServiceImpl(
                aiService,
                auditLogger,
                metrics,
                repository,
                EvolutionExecutionHandoffService.noop(),
                impactAnalysisService);
        Insights insights = Insights.builder()
                .id("insights-123")
                .observationRef("project-123:obs-456")
                .patterns(List.of(Pattern.builder()
                        .id("pattern-1")
                        .type("run-failure")
                        .description("Repeated failed run")
                        .confidence(0.91)
                        .evidence(List.of("learn-run-1", "learn-approval-1"))
                        .build()))
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
                            && proposal.provenance().contains("insights-123")
                            && proposal.provenance().contains("project-123:obs-456")
                            && proposal.provenance().contains("learn-run-1")
                            && proposal.metadata().get("sourceObservationRef").equals("project-123:obs-456")
                            && proposal.metadata().get("sourceLearningEvidenceIds").equals(List.of("learn-run-1", "learn-approval-1"))
                            && ((Map<String, Object>) proposal.metadata().get("impactAnalysis")).get("status").equals("READY")
                            && ((List<String>) ((Map<String, Object>) proposal.metadata().get("impactAnalysis"))
                                    .get("affectedModules")).contains("checkout")));
            verify(impactAnalysisService).analyze(argThat(request ->
                    request.tenantId().equals("tenant-alpha")
                            && request.projectId().equals("project-123")
                            && request.workspaceId().equals("workspace-unavailable")
                            && request.insights().id().equals("insights-123")));
        } finally {
            TenantContext.clear();
            runBlocking(TenantContext::clear);
        }
    }

    @Test
    void shouldApproveProposalAndReturnLifecycleHandoff() {
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        EvolutionPlanRepository repository = mock(EvolutionPlanRepository.class);
                EvolutionExecutionHandoffService handoffService = mock(EvolutionExecutionHandoffService.class);

        EvolutionPlanRepository.EvolutionProposalState state =
                new EvolutionPlanRepository.EvolutionProposalState(
                        "proposal-123",
                        "tenant-alpha",
                        "project-123",
                        "PENDING_APPROVAL",
                        "intent-987",
                        Map.of(),
                        java.time.Instant.now()
                );

        when(repository.findProposalState("tenant-alpha", "proposal-123"))
                .thenReturn(Promise.of(Optional.of(state)));
        when(repository.transitionApprovalState(
                eq("tenant-alpha"),
                eq("proposal-123"),
                eq("APPROVED"),
                eq("reviewer-1"),
                any(),
                any()))
                .thenReturn(Promise.complete());
        when(handoffService.handoff(any(EvolutionExecutionHandoffService.EvolutionExecutionRequest.class)))
                .thenReturn(Promise.of(new EvolutionExecutionHandoffService.EvolutionExecutionHandoff(
                        "handoff-123",
                        "QUEUED",
                        java.time.Instant.now(),
                        Map.of("collection", "yappc_evolution_execution_handoffs")
                )));

        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));
        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics, repository, handoffService);

        try {
            EvolutionService.EvolutionDecision decision = runPromise(() ->
                    service.approveProposal("proposal-123", "reviewer-1", "ready for execution"));

            assertEquals("APPROVED", decision.decision());
            assertTrue(decision.shouldExecuteLifecycle());
            assertEquals(List.of("validate", "generate", "run"), decision.executionPhases());
            assertEquals("intent-987", decision.productUnitIntentRef());
                        assertEquals("handoff-123", decision.metadata().get("handoffId"));
                        assertEquals("QUEUED", decision.metadata().get("handoffStatus"));
            verify(repository).transitionApprovalState(
                    eq("tenant-alpha"),
                    eq("proposal-123"),
                    eq("APPROVED"),
                    eq("reviewer-1"),
                    eq("ready for execution"),
                    argThat(metadata -> List.of("validate", "generate", "run").equals(metadata.get("nextPhases"))));
                        verify(handoffService).handoff(argThat(request ->
                                        request.proposalId().equals("proposal-123")
                                                        && request.tenantId().equals("tenant-alpha")
                                                        && request.projectId().equals("project-123")
                                                        && request.productUnitIntentRef().equals("intent-987")
                                                        && request.phases().equals(List.of("validate", "generate", "run"))));
        } finally {
            TenantContext.clear();
            runBlocking(TenantContext::clear);
        }
    }

    @Test
    void shouldRejectProposalWithoutLifecycleHandoff() {
        CompletionService aiService = mock(CompletionService.class);
        AuditLogger auditLogger = mock(AuditLogger.class);
        MetricsCollector metrics = mock(MetricsCollector.class);
        EvolutionPlanRepository repository = mock(EvolutionPlanRepository.class);

        EvolutionPlanRepository.EvolutionProposalState state =
                new EvolutionPlanRepository.EvolutionProposalState(
                        "proposal-999",
                        "tenant-alpha",
                        "project-123",
                        "PENDING_APPROVAL",
                        "intent-111",
                        Map.of(),
                        java.time.Instant.now()
                );

        when(repository.findProposalState("tenant-alpha", "proposal-999"))
                .thenReturn(Promise.of(Optional.of(state)));
        when(repository.transitionApprovalState(
                eq("tenant-alpha"),
                eq("proposal-999"),
                eq("REJECTED"),
                eq("reviewer-2"),
                any(),
                any()))
                .thenReturn(Promise.complete());

        TenantContext.setCurrentTenantId("tenant-alpha");
        runBlocking(() -> TenantContext.setCurrentTenantId("tenant-alpha"));
        EvolutionService service = new EvolutionServiceImpl(aiService, auditLogger, metrics, repository);

        try {
            EvolutionService.EvolutionDecision decision = runPromise(() ->
                    service.rejectProposal("proposal-999", "reviewer-2", "not enough confidence"));

            assertEquals("REJECTED", decision.decision());
            assertFalse(decision.shouldExecuteLifecycle());
            assertTrue(decision.executionPhases().isEmpty());
            verify(repository).transitionApprovalState(
                    eq("tenant-alpha"),
                    eq("proposal-999"),
                    eq("REJECTED"),
                    eq("reviewer-2"),
                    eq("not enough confidence"),
                    argThat(metadata -> Boolean.FALSE.equals(metadata.get("requiresLifecycleExecute"))));
        } finally {
            TenantContext.clear();
            runBlocking(TenantContext::clear);
        }
    }
}
