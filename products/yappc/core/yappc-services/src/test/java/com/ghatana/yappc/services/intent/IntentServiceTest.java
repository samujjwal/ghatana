package com.ghatana.yappc.services.intent;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.intent.IntentAnalysis;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for IntentService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("IntentService")
class IntentServiceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private IntentService service;
    private IntentRepository intentRepository;
    private IntentEvidenceService intentEvidenceService;

    @BeforeEach
    void setUp() { 
        aiService = mock(CompletionService.class); 
        auditLogger = mock(AuditLogger.class); 
        metrics = mock(MetricsCollector.class); 
        intentRepository = mock(IntentRepository.class);
        intentEvidenceService = mock(IntentEvidenceService.class);
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete()); 
        service = new IntentServiceImpl(aiService, auditLogger, metrics); 
    }

    private void stubAiSuccess(String text) { 
        when(aiService.complete(any(CompletionRequest.class))) 
                .thenReturn(Promise.of(CompletionResult.builder() 
                        .text(text) 
                        .modelUsed("gpt-4")
                        .build())); 
    }

    private void stubAiFailure(String message) { 
        when(aiService.complete(any(CompletionRequest.class))) 
                .thenReturn(Promise.ofException(new RuntimeException(message))); 
    }

    private IntentInput captureInput(String tenantId) { 
        return IntentInput.builder() 
                .rawText("Build a task management app for teams")
                .format("text")
                .tenantId(tenantId) 
                .build(); 
    }

    private IntentSpec spec(String tenantId) { 
        return IntentSpec.builder() 
                .id("intent-123")
                .productName("Task Manager")
                .description("Team collaboration tool")
                .goals(List.of()) 
                .personas(List.of()) 
                .constraints(List.of()) 
                .tenantId(tenantId) 
                .build(); 
    }

    @Nested
    @DisplayName("capture()")
    class CaptureTests {

        @Test
        @DisplayName("returns well-formed IntentSpec with tenant preserved")
        void shouldCaptureIntentWithAI() { 
            stubAiSuccess("Product: Task Manager\nDescription: Team collaboration tool");

            IntentSpec result = runPromise(() -> service.capture(captureInput("tenant-123")));

            assertNotNull(result); 
            assertNotNull(result.id()); 
            assertNotNull(result.productName()); 
            assertEquals("tenant-123", result.tenantId()); 
            verify(aiService, times(1)).complete(any(CompletionRequest.class)); 
            verify(auditLogger, times(1)).log(anyMap()); 
        }

        @Test
        @DisplayName("records timer with tenant tag on success")
        @SuppressWarnings("unchecked")
        void shouldRecordTimerOnSuccess() { 
            stubAiSuccess("Product: X\nDescription: desc");

            runPromise(() -> service.capture(captureInput("tenant-abc")));

            verify(metrics).recordTimer(eq("yappc.intent.capture"), anyLong(),
                    any(Map.class)); 
        }

        @Test
        @DisplayName("increments success counter on success")
        void shouldIncrementSuccessCounterOnSuccess() { 
            stubAiSuccess("Product: X\nDescription: desc");

            runPromise(() -> service.capture(captureInput("tenant-abc")));

            verify(metrics).incrementCounter(contains("success"), anyMap());
        }

        @Test
        @DisplayName("AI failure uses fallback parser and records fallback metric")
        void shouldHandleAIServiceFailureOnCapture() { 
            stubAiFailure("AI service unavailable");

            IntentSpec result = runPromise(() -> service.capture(captureInput(null))); 

            assertNotNull(result); 
            assertNotNull(result.productName()); 
            verify(metrics).incrementCounter(eq("yappc.ai.intent.capture.fallback"), anyMap());
        }

        @Test
        @DisplayName("different tenants get distinct intent IDs")
        void shouldIsolateTenants() { 
            stubAiSuccess("Product: App\nDescription: desc");

            IntentSpec spec1 = runPromise(() -> service.capture(captureInput("tenant-A")));
            IntentSpec spec2 = runPromise(() -> service.capture(captureInput("tenant-B")));

            assertNotEquals(spec1.id(), spec2.id()); 
            assertEquals("tenant-A", spec1.tenantId()); 
            assertEquals("tenant-B", spec2.tenantId()); 
        }

        @Test
        @DisplayName("persists captured intent when repository is configured")
        void shouldPersistCapturedIntent() {
            stubAiSuccess("Product: Task Manager\nDescription: Team collaboration tool");
            when(intentEvidenceService.recordCapture(any(IntentInput.class), any(IntentSpec.class)))
                    .thenReturn(Promise.of("evidence-intent-1"));
            when(intentRepository.saveVersion(any(IntentSpec.class), any(IntentPersistenceContext.class)))
                    .thenAnswer(invocation -> Promise.of(new IntentVersionRecord(
                            "project-123:intent-123:v1",
                            "tenant-123",
                            "workspace-123",
                            "project-123",
                            invocation.getArgument(0, IntentSpec.class).id(),
                            1,
                            invocation.getArgument(0, IntentSpec.class),
                            "user-123",
                            java.time.Instant.now(),
                            null,
                            List.of("evidence-intent-1"),
                            Map.of())));
            service = new IntentServiceImpl(aiService, auditLogger, metrics, intentRepository, intentEvidenceService);

            IntentSpec result = runPromise(() -> service.capture(IntentInput.builder()
                    .rawText("Build a task management app for teams")
                    .tenantId("tenant-123")
                    .workspaceId("workspace-123")
                    .projectId("project-123")
                    .userId("user-123")
                    .build()));

            assertNotNull(result);
            ArgumentCaptor<IntentPersistenceContext> contextCaptor =
                    ArgumentCaptor.forClass(IntentPersistenceContext.class);
            verify(intentRepository).saveVersion(eq(result), contextCaptor.capture());
            IntentPersistenceContext context = contextCaptor.getValue();
            assertEquals("tenant-123", context.tenantId());
            assertEquals("workspace-123", context.workspaceId());
            assertEquals("project-123", context.projectId());
            assertEquals("user-123", context.actorId());
            assertEquals(List.of("evidence-intent-1"), context.evidenceIds());
            verify(intentEvidenceService).recordCapture(any(IntentInput.class), eq(result));
        }
    }

    @Nested
    @DisplayName("analyze()")
    class AnalyzeTests {

        @Test
        @DisplayName("returns IntentAnalysis with risks and gaps for given spec")
        void shouldAnalyzeIntentForFeasibility() { 
            stubAiSuccess("Feasibility: High\nRisks: Technical complexity");

            IntentAnalysis result = runPromise(() -> service.analyze(spec("tenant-123")));

            assertNotNull(result); 
            assertEquals("intent-123", result.intentId()); 
            assertNotNull(result.risks()); 
            assertNotNull(result.gaps()); 
            verify(aiService, times(1)).complete(any(CompletionRequest.class)); 
            verify(auditLogger, times(1)).log(anyMap()); 
        }

        @Test
        @DisplayName("records timer with tenant tag on successful analysis")
        @SuppressWarnings("unchecked")
        void shouldRecordTimerOnAnalyze() { 
            stubAiSuccess("Feasibility: High");

            runPromise(() -> service.analyze(spec("tenant-xyz")));

            verify(metrics).recordTimer(eq("yappc.intent.analyze"), anyLong(),
                    any(Map.class)); 
        }

        @Test
        @DisplayName("increments success counter on successful analysis")
        void shouldIncrementSuccessCounterOnAnalyze() { 
            stubAiSuccess("Feasibility: High");

            runPromise(() -> service.analyze(spec("tenant-xyz")));

            verify(metrics).incrementCounter(contains("success"), anyMap());
        }

        @Test
        @DisplayName("AI failure during analyze uses fallback output and records fallback metric")
        void shouldHandleAIServiceFailureOnAnalyze() { 
            stubAiFailure("Model timeout");

            IntentAnalysis result = runPromise(() -> service.analyze(spec("tenant-99")));

            assertNotNull(result); 
            assertEquals("intent-123", result.intentId()); 
            verify(metrics).incrementCounter(eq("yappc.ai.intent.analyze.fallback"), anyMap());
        }

        @Test
        @DisplayName("records prompt, model, evidence, and confidence grounding for analysis")
        @SuppressWarnings({"unchecked", "rawtypes"})
        void shouldRecordAiGroundingForAnalysis() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.of(CompletionResult.builder()
                            .text("Feasibility: High")
                            .modelUsed("gpt-4")
                            .tokensUsed(42)
                            .finishReason("stop")
                            .build()));
            when(intentEvidenceService.recordAnalysis(any(IntentSpec.class), any(IntentAnalysis.class), anyMap()))
                    .thenReturn(Promise.of("evidence-analysis-1"));
            service = new IntentServiceImpl(aiService, auditLogger, metrics, intentRepository, intentEvidenceService);

            IntentAnalysis result = runPromise(() -> service.analyze(IntentSpec.builder()
                    .id("intent-123")
                    .productName("Task Manager")
                    .description("Team collaboration tool")
                    .tenantId("tenant-123")
                    .metadata(Map.of(
                            "workspaceId", "workspace-123",
                            "projectId", "project-123",
                            "userId", "user-123"))
                    .build()));

            assertNotNull(result);
            ArgumentCaptor<Map<String, Object>> metadataCaptor =
                    ArgumentCaptor.forClass((Class) Map.class);
            verify(intentEvidenceService).recordAnalysis(any(IntentSpec.class), eq(result), metadataCaptor.capture());
            Map<String, Object> metadata = metadataCaptor.getValue();
            assertEquals("intent.analyze", metadata.get("promptKey"));
            assertEquals("v1", metadata.get("promptVersion"));
            assertEquals("baseline", metadata.get("promptVariant"));
            assertEquals("gpt-4", metadata.get("modelUsed"));
            assertEquals("stop", metadata.get("finishReason"));
            assertEquals(42, metadata.get("tokensUsed"));
            assertNotNull(metadata.get("promptHash"));
            assertNotNull(metadata.get("confidence"));
        }
    }
}
