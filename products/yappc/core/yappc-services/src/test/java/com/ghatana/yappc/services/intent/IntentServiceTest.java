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

    @BeforeEach
    void setUp() {
        aiService = mock(CompletionService.class);
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
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
    }
}
