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
    void setUp() { // GH-90000
        aiService = mock(CompletionService.class); // GH-90000
        auditLogger = mock(AuditLogger.class); // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete()); // GH-90000
        service = new IntentServiceImpl(aiService, auditLogger, metrics); // GH-90000
    }

    private void stubAiSuccess(String text) { // GH-90000
        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text(text) // GH-90000
                        .modelUsed("gpt-4")
                        .build())); // GH-90000
    }

    private void stubAiFailure(String message) { // GH-90000
        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException(message))); // GH-90000
    }

    private IntentInput captureInput(String tenantId) { // GH-90000
        return IntentInput.builder() // GH-90000
                .rawText("Build a task management app for teams")
                .format("text")
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000
    }

    private IntentSpec spec(String tenantId) { // GH-90000
        return IntentSpec.builder() // GH-90000
                .id("intent-123")
                .productName("Task Manager")
                .description("Team collaboration tool")
                .goals(List.of()) // GH-90000
                .personas(List.of()) // GH-90000
                .constraints(List.of()) // GH-90000
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("capture()")
    class CaptureTests {

        @Test
        @DisplayName("returns well-formed IntentSpec with tenant preserved")
        void shouldCaptureIntentWithAI() { // GH-90000
            stubAiSuccess("Product: Task Manager\nDescription: Team collaboration tool");

            IntentSpec result = runPromise(() -> service.capture(captureInput("tenant-123")));

            assertNotNull(result); // GH-90000
            assertNotNull(result.id()); // GH-90000
            assertNotNull(result.productName()); // GH-90000
            assertEquals("tenant-123", result.tenantId()); // GH-90000
            verify(aiService, times(1)).complete(any(CompletionRequest.class)); // GH-90000
            verify(auditLogger, times(1)).log(anyMap()); // GH-90000
        }

        @Test
        @DisplayName("records timer with tenant tag on success")
        void shouldRecordTimerOnSuccess() { // GH-90000
            stubAiSuccess("Product: X\nDescription: desc");

            runPromise(() -> service.capture(captureInput("tenant-abc")));

            verify(metrics).recordTimer(eq("yappc.intent.capture"), anyLong(),
                    any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("increments success counter on success")
        void shouldIncrementSuccessCounterOnSuccess() { // GH-90000
            stubAiSuccess("Product: X\nDescription: desc");

            runPromise(() -> service.capture(captureInput("tenant-abc")));

            verify(metrics).incrementCounter(contains("success"), anyMap());
        }

        @Test
        @DisplayName("AI failure uses fallback parser and records fallback metric")
        void shouldHandleAIServiceFailureOnCapture() { // GH-90000
            stubAiFailure("AI service unavailable");

            IntentSpec result = runPromise(() -> service.capture(captureInput(null))); // GH-90000

            assertNotNull(result); // GH-90000
            assertNotNull(result.productName()); // GH-90000
            verify(metrics).incrementCounter(eq("yappc.ai.intent.capture.fallback"), anyMap());
        }

        @Test
        @DisplayName("different tenants get distinct intent IDs")
        void shouldIsolateTenants() { // GH-90000
            stubAiSuccess("Product: App\nDescription: desc");

            IntentSpec spec1 = runPromise(() -> service.capture(captureInput("tenant-A")));
            IntentSpec spec2 = runPromise(() -> service.capture(captureInput("tenant-B")));

            assertNotEquals(spec1.id(), spec2.id()); // GH-90000
            assertEquals("tenant-A", spec1.tenantId()); // GH-90000
            assertEquals("tenant-B", spec2.tenantId()); // GH-90000
        }
    }

    @Nested
    @DisplayName("analyze()")
    class AnalyzeTests {

        @Test
        @DisplayName("returns IntentAnalysis with risks and gaps for given spec")
        void shouldAnalyzeIntentForFeasibility() { // GH-90000
            stubAiSuccess("Feasibility: High\nRisks: Technical complexity");

            IntentAnalysis result = runPromise(() -> service.analyze(spec("tenant-123")));

            assertNotNull(result); // GH-90000
            assertEquals("intent-123", result.intentId()); // GH-90000
            assertNotNull(result.risks()); // GH-90000
            assertNotNull(result.gaps()); // GH-90000
            verify(aiService, times(1)).complete(any(CompletionRequest.class)); // GH-90000
            verify(auditLogger, times(1)).log(anyMap()); // GH-90000
        }

        @Test
        @DisplayName("records timer with tenant tag on successful analysis")
        void shouldRecordTimerOnAnalyze() { // GH-90000
            stubAiSuccess("Feasibility: High");

            runPromise(() -> service.analyze(spec("tenant-xyz")));

            verify(metrics).recordTimer(eq("yappc.intent.analyze"), anyLong(),
                    any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("increments success counter on successful analysis")
        void shouldIncrementSuccessCounterOnAnalyze() { // GH-90000
            stubAiSuccess("Feasibility: High");

            runPromise(() -> service.analyze(spec("tenant-xyz")));

            verify(metrics).incrementCounter(contains("success"), anyMap());
        }

        @Test
        @DisplayName("AI failure during analyze uses fallback output and records fallback metric")
        void shouldHandleAIServiceFailureOnAnalyze() { // GH-90000
            stubAiFailure("Model timeout");

            IntentAnalysis result = runPromise(() -> service.analyze(spec("tenant-99")));

            assertNotNull(result); // GH-90000
            assertEquals("intent-123", result.intentId()); // GH-90000
            verify(metrics).incrementCounter(eq("yappc.ai.intent.analyze.fallback"), anyMap());
        }
    }
}
