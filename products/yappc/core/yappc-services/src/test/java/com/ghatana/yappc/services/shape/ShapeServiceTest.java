package com.ghatana.yappc.services.shape;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
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
 * @doc.purpose Tests for ShapeService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ShapeService")
class ShapeServiceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private ShapeService service;

    @BeforeEach
    void setUp() {
        aiService = mock(CompletionService.class);
        auditLogger = mock(AuditLogger.class);
        metrics = mock(MetricsCollector.class);
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
        service = new ShapeServiceImpl(aiService, auditLogger, metrics);
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

    private IntentSpec intent(String tenantId) {
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

    private ShapeSpec spec(String tenantId) {
        return ShapeSpec.builder()
                .id("shape-123")
                .intentRef("intent-123")
                .domainModel(null)
                .workflows(List.of())
                .integrations(List.of())
                .tenantId(tenantId)
                .build();
    }

    @Nested
    @DisplayName("derive()")
    class DeriveTests {

        @Test
        @DisplayName("returns ShapeSpec with intentRef and tenant preserved")
        void shouldDeriveShapeFromIntent() {
            stubAiSuccess("{\"architecture\": {\"name\": \"microservices\"}, \"domainModel\": {}}");

            ShapeSpec result = runPromise(() -> service.derive(intent("tenant-123")));

            assertNotNull(result);
            assertNotNull(result.id());
            assertEquals("intent-123", result.intentRef());
            assertEquals("tenant-123", result.tenantId());
            assertNotNull(result.domainModel());
            verify(aiService, times(1)).complete(any(CompletionRequest.class));
            verify(auditLogger, times(1)).log(anyMap());
        }

        @Test
        @DisplayName("records timer with tenant tag on success")
        void shouldRecordTimerOnDerive() {
            stubAiSuccess("{\"architecture\": {\"name\": \"monolith\"}, \"domainModel\": {}}");

            runPromise(() -> service.derive(intent("tenant-abc")));

            verify(metrics).recordTimer(eq("yappc.shape.derive"), anyLong(),
                    any(Map.class));
        }

        @Test
        @DisplayName("increments success counter on successful derive")
        void shouldIncrementSuccessCounterOnDerive() {
            stubAiSuccess("{\"architecture\": {}, \"domainModel\": {}}");

            runPromise(() -> service.derive(intent("tenant-abc")));

            verify(metrics).incrementCounter(contains("success"), anyMap());
        }

        @Test
        @DisplayName("increments error counter and propagates on AI failure")
        void shouldHandleAIServiceFailureOnDerive() {
            stubAiFailure("AI service unavailable");

            Exception thrown = assertThrows(Exception.class,
                    () -> runPromise(() -> service.derive(intent("tenant-fail"))));

            assertTrue(thrown.getMessage().contains("AI service unavailable"));
            verify(metrics).incrementCounter(eq("yappc.shape.derive.error"), anyMap());
        }

        @Test
        @DisplayName("different tenants get distinct shape IDs")
        void shouldIsolateTenantsOnDerive() {
            stubAiSuccess("{\"architecture\": {}, \"domainModel\": {}}");

            ShapeSpec s1 = runPromise(() -> service.derive(intent("tenant-A")));
            ShapeSpec s2 = runPromise(() -> service.derive(intent("tenant-B")));

            assertNotEquals(s1.id(), s2.id());
            assertEquals("tenant-A", s1.tenantId());
            assertEquals("tenant-B", s2.tenantId());
        }
    }

    @Nested
    @DisplayName("generateModel()")
    class GenerateModelTests {

        @Test
        @DisplayName("returns SystemModel referencing the original ShapeSpec")
        void shouldGenerateSystemModel() {
            stubAiSuccess("Design Rationale: Microservices for scalability");

            ShapeSpec input = spec("tenant-123");
            SystemModel result = runPromise(() -> service.generateModel(input));

            assertNotNull(result);
            assertEquals(input, result.shape());
            assertNotNull(result.designRationale());
            verify(aiService, times(1)).complete(any(CompletionRequest.class));
            verify(auditLogger, times(1)).log(anyMap());
        }

        @Test
        @DisplayName("records timer with tenant tag on success")
        void shouldRecordTimerOnGenerateModel() {
            stubAiSuccess("Design Rationale: Simple monolith");

            runPromise(() -> service.generateModel(spec("tenant-xyz")));

            verify(metrics).recordTimer(eq("yappc.shape.generateModel"), anyLong(),
                    any(Map.class));
        }

        @Test
        @DisplayName("increments success counter on successful generateModel")
        void shouldIncrementSuccessCounterOnGenerateModel() {
            stubAiSuccess("Design Rationale: Serverless for low cost");

            runPromise(() -> service.generateModel(spec("tenant-xyz")));

            verify(metrics).incrementCounter(contains("success"), anyMap());
        }

        @Test
        @DisplayName("increments error counter and propagates on AI failure during generateModel")
        void shouldHandleAIServiceFailureOnGenerateModel() {
            stubAiFailure("LLM timeout");

            Exception thrown = assertThrows(Exception.class,
                    () -> runPromise(() -> service.generateModel(spec("tenant-err"))));

            assertTrue(thrown.getMessage().contains("LLM timeout"));
            verify(metrics).incrementCounter(eq("yappc.shape.generateModel.error"), anyMap());
        }
    }
}
