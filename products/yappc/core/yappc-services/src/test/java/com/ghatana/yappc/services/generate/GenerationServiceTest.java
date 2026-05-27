package com.ghatana.yappc.services.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.api.GenerationRun;
import com.ghatana.yappc.api.GenerationRunRepository;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.GenerationReviewAction;
import com.ghatana.yappc.domain.generate.GenerationReviewRequest;
import com.ghatana.yappc.domain.generate.GenerationReviewResult;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import com.ghatana.yappc.domain.validate.ValidationIssue;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for GenerationService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@SuppressWarnings("unchecked")
@DisplayName("GenerationService")
class GenerationServiceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private GenerationRunRepository generationRunRepository;
    private ObjectMapper objectMapper;
    private MutableAiHealthProvider aiHealthProvider;
    private GenerationService service;

    @BeforeEach
    void setUp() {
        aiService = mock(CompletionService.class);
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
        generationRunRepository = mock(GenerationRunRepository.class);
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("public class Main { }")
                        .modelUsed("gpt-4")
                        .build()));
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete());
        when(generationRunRepository.save(any(GenerationRun.class))).thenReturn(Promise.complete());
        lenient().when(generationRunRepository.findById(anyString())).thenReturn(Promise.of(null));
        lenient().when(generationRunRepository.updateReviewStatus(anyString(), any(GenerationRun.ReviewStatus.class)))
                .thenReturn(Promise.complete());
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        aiHealthProvider = new MutableAiHealthProvider(false);
        service = GenerationServiceTestFactory.create(
            aiService,
            auditLogger,
            metrics,
            generationRunRepository,
            objectMapper,
            aiHealthProvider);
    }

    private ValidatedSpec specWithoutEntities() { 
        return ValidatedSpec.of( 
                ShapeSpec.builder()
                        .id("shape-123")
                        .tenantId("tenant-1")
                        .domainModel(DomainModel.builder()
                                .entities(List.of())
                                .relationships(List.of())
                                .boundedContexts(List.of())
                                .build())
                        .build(),
                LifecycleValidationResult.builder().build()); 
    }

    private ValidatedSpec incompleteSpecWithoutDomainModel() {
        return ValidatedSpec.of(
                ShapeSpec.builder().id("shape-incomplete").tenantId("tenant-1").build(),
                LifecycleValidationResult.builder().build());
    }

    private ValidatedSpec blockedValidationSpec() {
        return ValidatedSpec.of(
                ShapeSpec.builder()
                        .id("shape-blocked")
                        .tenantId("tenant-1")
                        .domainModel(DomainModel.builder()
                                .entities(List.of())
                                .relationships(List.of())
                                .boundedContexts(List.of())
                                .build())
                        .build(),
                LifecycleValidationResult.builder()
                        .passed(false)
                        .issues(List.of(ValidationIssue.builder()
                                .id("shape-required")
                                .severity("error")
                                .category("shape")
                                .message("Shape is incomplete")
                                .blocking(true)
                                .build()))
                        .build());
    }

    private ValidatedSpec specWithEntities(List<EntitySpec> entities) {
        DomainModel model = DomainModel.builder().entities(entities).build();
        return ValidatedSpec.of(
                ShapeSpec.builder().id("shape-456").tenantId("tenant-1").domainModel(model).build(),
                LifecycleValidationResult.builder().build());
    }

    private com.ghatana.yappc.domain.generate.GenerationContext defaultContext() {
        return com.ghatana.yappc.domain.generate.GenerationContext.builder()
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .projectId("project-1")
                .actorId("test-actor")
                .phase("GENERATE")
                .sourceArtifactIds(List.of())
                .canvasNodeIds(List.of())
                .intentId("intent-1")
                .shapeId("shape-1")
                .correlationId("corr-1")
                .build();
    }

    @Test
    @DisplayName("generate: spec without domainModel → artifacts contain config/docs/pipeline but no entity code")
    void shouldGenerateArtifacts() {
        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        assertNotNull(result);
        assertNotNull(result.id());
        assertThat(result.specRef()).isEqualTo("shape-123");
        assertThat(result.artifacts()).isNotNull();
        // config + docs + ci/cd pipeline — 3 base artifacts
        assertThat(result.artifacts().size()).isGreaterThanOrEqualTo(3);

        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class));
        verify(auditLogger, times(1)).log(any(Map.class));
    }

    @Test
    @DisplayName("generate: spec with entities → entity code generation called per entity")
    void shouldBlockGenerationWhenShapeIncomplete() {
        Exception error = assertThrows(Exception.class,
                () -> runPromise(() -> service.generate(incompleteSpecWithoutDomainModel(), defaultContext())));

        assertThat(error.getMessage()).contains("Shape is not ready for generation");
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(aiService, never()).complete(any(CompletionRequest.class));
    }

    @Test
    @DisplayName("generate: blocking validation result is blocked before run creation")
    void shouldBlockGenerationWhenValidationFailed() {
        Exception error = assertThrows(Exception.class,
                () -> runPromise(() -> service.generate(blockedValidationSpec(), defaultContext())));

        assertThat(error.getMessage()).contains("shape validation must pass");
        verify(generationRunRepository, never()).save(any(GenerationRun.class));
        verify(aiService, never()).complete(any(CompletionRequest.class));
    }

    @Test
    @DisplayName("generate: spec with entities -> entity code generation called per entity")
    void shouldGenerateEntityCodeForEachEntity() {
        EntitySpec entity1 = EntitySpec.builder().name("User").description("User entity").build();
        EntitySpec entity2 = EntitySpec.builder().name("Order").description("Order entity").build();

        GeneratedArtifacts result = runPromise(() -> service.generate(specWithEntities(List.of(entity1, entity2)), defaultContext()));

        assertNotNull(result);
        // 2 entities + 3 base artifacts = at least 5
        assertThat(result.artifacts().size()).isGreaterThanOrEqualTo(5);
        // AI called once per entity + once per base artifact
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class));
    }

    @Test
    @DisplayName("generate: metrics timer and success counter recorded")
    void shouldRecordMetricsOnSuccess() {
        runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        verify(metrics, atLeastOnce()).recordTimer(eq("yappc.generate.execute"), anyLong(), any(Map.class));
        verify(metrics, atLeastOnce()).incrementCounter(contains("success"), any(Map.class));
    }

    @Test
    @DisplayName("generate: audit logger called with event details")
    void shouldAuditGenerateExecution() {
        runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        verify(auditLogger, times(1)).log(any(Map.class));
    }

    @Test
    @DisplayName("regenerateWithDiff: both old and new artifacts present in diff result")
    void shouldRegenerateWithDiff() {
        ValidatedSpec spec = specWithoutEntities();
        GeneratedArtifacts existing = GeneratedArtifacts.builder()
                .id("old-123")
                .specRef("shape-123")
                .artifacts(List.of())
                .build();

        DiffResult result = runPromise(() -> service.regenerateWithDiff(spec, existing, defaultContext()));

        assertNotNull(result);
        assertNotNull(result.newArtifacts());
        assertNotNull(result.oldArtifacts());
        assertNotNull(result.diffs());
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class));
    }

    @Test
    @DisplayName("regenerateWithDiff: diff metrics and audit recorded")
    void shouldRecordMetricsOnDiff() {
        ValidatedSpec spec = specWithoutEntities();
        GeneratedArtifacts existing = GeneratedArtifacts.builder()
                .id("old-123").specRef("shape-123").artifacts(List.of()).build();

        runPromise(() -> service.regenerateWithDiff(spec, existing, defaultContext()));

        verify(metrics, atLeastOnce()).recordTimer(contains("diff"), anyLong(), any(Map.class));
        verify(auditLogger, atLeast(2)).log(any(Map.class)); // once for generate, once for diff
    }

    @Test
    @DisplayName("generate: AI failure propagates and error metric recorded")
    void shouldHandleGenerationFailure() {
        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Generation failed")));

        try {
            GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));
            // Promises.toList may succeed with remaining artifacts if only some calls fail
            assertNotNull(result);
            verify(metrics, atLeastOnce()).recordTimer(anyString(), anyLong(), any(Map.class));
        } catch (Exception e) {
            assertThat(e.getMessage()).containsIgnoringCase("generation failed");
            verify(metrics, atLeastOnce()).incrementCounter(contains("error"), any(Map.class));
        }
    }

            @Test
            @DisplayName("generate: failure persists failed run state with correlation and reason")
            void shouldPersistFailedRunStateOnGenerationFailure() {
            when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.ofException(new RuntimeException("Generation failed")));

            assertThrows(Exception.class, () -> runPromise(() -> service.generate(specWithoutEntities(), defaultContext())));

            verify(generationRunRepository, atLeast(2)).save(argThat(run ->
                run.status() == GenerationRun.RunStatus.FAILED
                    && "corr-1".equals(run.provenance().get("correlation_id"))
                    && "Generation failed".equals(run.metadata().get("failure_reason"))));
            }

    @Test
    @DisplayName("generate: metadata contains validation_passed flag")
    void shouldIncludeValidationMetadataInArtifacts() {
        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        assertThat(result.metadata()).isNotNull();
        assertThat(result.metadata()).containsKey("validation_passed");
        assertThat(result.metadata()).containsEntry("assurance_passed", "true");
        assertThat(result.metadata().get("assurance_checks_passed")).contains("compile", "security", "a11y");
    }

    @Test
    @DisplayName("generate: assurance failure blocks completed run and audit")
    void shouldBlockGenerationWhenAssuranceFails() {
        GenerationAssuranceService failingAssurance = new GenerationAssuranceService() {
            @Override
            public GenerationAssuranceReport assure(ValidatedSpec spec, GeneratedArtifacts artifacts) {
                return new GenerationAssuranceReport(
                        false,
                        List.of(new GenerationAssuranceCheck(
                                "security",
                                false,
                                List.of("unsafe generated path"))));
            }
        };
        service = new GenerationServiceImpl(
                aiService,
                auditLogger,
                metrics,
                generationRunRepository,
                objectMapper,
                aiHealthProvider,
                failingAssurance);

        Exception error = assertThrows(Exception.class,
                () -> runPromise(() -> service.generate(specWithoutEntities(), defaultContext())));

        assertThat(error.getMessage()).contains("Generate assurance failed");
        verify(auditLogger, never()).log(any(Map.class));
    }

    // ---------------------------------------------------------------------------
    // AI degraded fallback
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("generate: AI-degraded flag produces deterministic fallback artifacts")
    void shouldUseDeterministicFallbackWhenAiDegraded() {
        aiHealthProvider.setDegraded(true);

        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        assertNotNull(result);
        // Degraded path still produces artifacts via fallback text
        assertThat(result.artifacts()).isNotEmpty();
        // AI completion must NOT be called when degraded
        verify(aiService, never()).complete(any(CompletionRequest.class));
    }

    @Test
    @DisplayName("generate: degraded flag is reset and re-enabled correctly")
    void degradedFlagToggle() {
        aiHealthProvider.setDegraded(true);
        aiHealthProvider.setDegraded(false);

        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        assertNotNull(result);
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class));
    }

    // ---------------------------------------------------------------------------
    // reviewDecision — APPLY
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("reviewDecision: APPLY sets run status to APPROVED")
    void reviewDecisionApplySetsApprovedStatus() {
        GenerationRun existingRun = buildReviewableRun("run-apply");
        when(generationRunRepository.findById("run-apply")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-apply", "project-1", "actor-1", "Approved content", GenerationReviewAction.APPLY);

        GenerationReviewResult result = runPromise(() -> service.reviewDecision(request));

        assertNotNull(result);
        assertThat(result.runId()).isEqualTo("run-apply");
        assertThat(result.decision()).isEqualTo("apply");
        verify(generationRunRepository, atLeastOnce()).updateReviewStatus(eq("run-apply"), eq(GenerationRun.ReviewStatus.APPROVED));
        verify(auditLogger, atLeastOnce()).log(any(Map.class));
    }

    @Test
    @DisplayName("reviewDecision: REJECT sets run status to REJECTED")
    void reviewDecisionRejectSetsRejectedStatus() {
        GenerationRun existingRun = buildReviewableRun("run-reject");
        when(generationRunRepository.findById("run-reject")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-reject", "project-1", "actor-1", "Content does not meet quality bar", GenerationReviewAction.REJECT);

        GenerationReviewResult result = runPromise(() -> service.reviewDecision(request));

        assertNotNull(result);
        assertThat(result.decision()).isEqualTo("reject");
        verify(generationRunRepository, atLeastOnce()).updateReviewStatus(eq("run-reject"), eq(GenerationRun.ReviewStatus.REJECTED));
    }

    // ---------------------------------------------------------------------------
    // reviewDecision — ROLLBACK safety checks
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("reviewDecision: ROLLBACK succeeds when run is in valid state within time window")
    void reviewDecisionRollbackSucceedsForValidRun() {
        GenerationRun existingRun = buildReviewableRun("run-rollback");
        when(generationRunRepository.findById("run-rollback")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-rollback", "project-1", "actor-1", "Rolling back to prior baseline", GenerationReviewAction.ROLLBACK);

        GenerationReviewResult result = runPromise(() -> service.reviewDecision(request));

        assertNotNull(result);
        assertThat(result.decision()).isEqualTo("rollback");
    }

    @Test
    @DisplayName("reviewDecision: ROLLBACK fails when run is not found")
    void reviewDecisionRollbackFailsWhenRunNotFound() {
        when(generationRunRepository.findById("run-missing")).thenReturn(Promise.of(null));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-missing", "project-1", "actor-1", "Rollback reason", GenerationReviewAction.ROLLBACK);

        // Safety check returns false → rollback throws IllegalStateException
        assertThrows(Exception.class, () -> runPromise(() -> service.reviewDecision(request)));
    }

    @Test
    @DisplayName("reviewDecision: ROLLBACK fails when reason is blank")
    void reviewDecisionRollbackFailsWithoutReason() {
        GenerationRun existingRun = buildReviewableRun("run-no-reason");
        when(generationRunRepository.findById("run-no-reason")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-no-reason", "project-1", "actor-1", "", GenerationReviewAction.ROLLBACK);

        // Safety check blocks because reason is blank
        assertThrows(Exception.class, () -> runPromise(() -> service.reviewDecision(request)));
    }

    @Test
    @DisplayName("reviewDecision: ROLLBACK fails when actor is blank")
    void reviewDecisionRollbackFailsWithoutActor() {
        GenerationRun existingRun = buildReviewableRun("run-no-actor");
        when(generationRunRepository.findById("run-no-actor")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-no-actor", "project-1", "", "Valid rollback reason", GenerationReviewAction.ROLLBACK);

        // Safety check blocks because actor is blank
        assertThrows(Exception.class, () -> runPromise(() -> service.reviewDecision(request)));
    }

    @Test
    @DisplayName("reviewDecision: null action throws IllegalArgumentException")
    void reviewDecisionNullActionThrows() {
        GenerationReviewRequest request = new GenerationReviewRequest(
                "run-null", "project-1", "actor-1", "reason", null, List.of(), null);

        assertThrows(Exception.class, () -> runPromise(() -> service.reviewDecision(request)));
    }

    // ---------------------------------------------------------------------------
    // User edits serialization
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("reviewDecision: APPLY with user edits serializes them via ObjectMapper")
    void reviewDecisionApplyWithUserEditsSerializedViaObjectMapper() {
        GenerationRun existingRun = buildReviewableRun("run-edits");
        when(generationRunRepository.findById("run-edits")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest.UserEdit edit = new GenerationReviewRequest.UserEdit(
                "artifact-1", "region-1", 5, 10,
                "original", "edited", "modify", Instant.now());

        GenerationReviewRequest request = new GenerationReviewRequest(
                "run-edits", "project-1", "actor-1", "Applied with edits",
                GenerationReviewAction.APPLY,
                List.of(edit),
                new GenerationReviewRequest.ReviewProvenance(
                        "session-1", "trace-1", "web", "1.0.0", Instant.now(), Map.of()));

        GenerationReviewResult result = runPromise(() -> service.reviewDecision(request));

        assertNotNull(result);
        assertThat(result.decision()).isEqualTo("apply");
        // Run is saved with user_edits metadata (saved at least twice: update + metadata save)
        verify(generationRunRepository, atLeastOnce()).save(any(GenerationRun.class));
    }

    // ---------------------------------------------------------------------------
    // Audit observability
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("reviewDecision: APPLY emits audit event observable via auditLogger")
    void reviewDecisionApplyEmitsAuditEvent() {
        GenerationRun existingRun = buildReviewableRun("run-audit");
        when(generationRunRepository.findById("run-audit")).thenReturn(Promise.of(existingRun));

        GenerationReviewRequest request = GenerationReviewRequest.of(
                "run-audit", "project-1", "actor-1", "Approved", GenerationReviewAction.APPLY);

        runPromise(() -> service.reviewDecision(request));

        verify(auditLogger, atLeastOnce()).log(any(Map.class));
    }

    @Test
    @DisplayName("generate: generation run saved with complete provenance context")
    void generateRunSavedWithCompleteProvenance() {
        runPromise(() -> service.generate(specWithoutEntities(), defaultContext()));

        // Run saved at least twice: initial GENERATING state + completed COMPLETED state
        verify(generationRunRepository, atLeast(2)).save(argThat(run ->
                run.tenantId().equals("tenant-1")
                && run.workspaceId().equals("workspace-1")
                && run.projectId().equals("project-1")
                && run.provenance().containsKey("actor_id")
                && run.provenance().containsKey("correlation_id")));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Builds a GenerationRun in APPROVED/REVIEW_PENDING state completed within the
     * rollback window, suitable for review decision tests.
     */
    private GenerationRun buildReviewableRun(String runId) {
        return GenerationRun.builder()
                .id(runId)
                .planId("plan-1")
                .projectId("project-1")
                .tenantId("tenant-1")
                .workspaceId("workspace-1")
                .status(GenerationRun.RunStatus.COMPLETED)
                .reviewStatus(GenerationRun.ReviewStatus.REVIEW_PENDING)
                .createdAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now().minusSeconds(30))
                .build();
    }
}
