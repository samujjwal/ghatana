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
@DisplayName("ShapeService [GH-90000]")
class ShapeServiceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private ShapeService service;

    @BeforeEach
    void setUp() { // GH-90000
        aiService = mock(CompletionService.class); // GH-90000
        auditLogger = mock(AuditLogger.class); // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete()); // GH-90000
        service = new ShapeServiceImpl(aiService, auditLogger, metrics); // GH-90000
    }

    private void stubAiSuccess(String text) { // GH-90000
        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text(text) // GH-90000
                        .modelUsed("gpt-4 [GH-90000]")
                        .build())); // GH-90000
    }

    private void stubAiFailure(String message) { // GH-90000
        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException(message))); // GH-90000
    }

    private IntentSpec intent(String tenantId) { // GH-90000
        return IntentSpec.builder() // GH-90000
                .id("intent-123 [GH-90000]")
                .productName("Task Manager [GH-90000]")
                .description("Team collaboration tool [GH-90000]")
                .goals(List.of()) // GH-90000
                .personas(List.of()) // GH-90000
                .constraints(List.of()) // GH-90000
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000
    }

    private ShapeSpec spec(String tenantId) { // GH-90000
        return ShapeSpec.builder() // GH-90000
                .id("shape-123 [GH-90000]")
                .intentRef("intent-123 [GH-90000]")
                .domainModel(null) // GH-90000
                .workflows(List.of()) // GH-90000
                .integrations(List.of()) // GH-90000
                .tenantId(tenantId) // GH-90000
                .build(); // GH-90000
    }

    @Nested
    @DisplayName("derive() [GH-90000]")
    class DeriveTests {

        @Test
        @DisplayName("returns ShapeSpec with intentRef and tenant preserved [GH-90000]")
        void shouldDeriveShapeFromIntent() { // GH-90000
            stubAiSuccess("{\"architecture\": {\"name\": \"microservices\"}, \"domainModel\": {}}"); // GH-90000

            ShapeSpec result = runPromise(() -> service.derive(intent("tenant-123 [GH-90000]")));

            assertNotNull(result); // GH-90000
            assertNotNull(result.id()); // GH-90000
            assertEquals("intent-123", result.intentRef()); // GH-90000
            assertEquals("tenant-123", result.tenantId()); // GH-90000
            assertNotNull(result.domainModel()); // GH-90000
            verify(aiService, times(1)).complete(any(CompletionRequest.class)); // GH-90000
            verify(auditLogger, times(1)).log(anyMap()); // GH-90000
        }

        @Test
        @DisplayName("records timer with tenant tag on success [GH-90000]")
        void shouldRecordTimerOnDerive() { // GH-90000
            stubAiSuccess("{\"architecture\": {\"name\": \"monolith\"}, \"domainModel\": {}}"); // GH-90000

            runPromise(() -> service.derive(intent("tenant-abc [GH-90000]")));

            verify(metrics).recordTimer(eq("yappc.shape.derive [GH-90000]"), anyLong(),
                    any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("increments success counter on successful derive [GH-90000]")
        void shouldIncrementSuccessCounterOnDerive() { // GH-90000
            stubAiSuccess("{\"architecture\": {}, \"domainModel\": {}}"); // GH-90000

            runPromise(() -> service.derive(intent("tenant-abc [GH-90000]")));

            verify(metrics).incrementCounter(contains("success [GH-90000]"), anyMap());
        }

        @Test
        @DisplayName("AI failure on derive uses fallback shape and records fallback metric [GH-90000]")
        void shouldHandleAIServiceFailureOnDerive() { // GH-90000
            stubAiFailure("AI service unavailable [GH-90000]");

            ShapeSpec result = runPromise(() -> service.derive(intent("tenant-fail [GH-90000]")));

            assertNotNull(result); // GH-90000
            assertEquals("intent-123", result.intentRef()); // GH-90000
            verify(metrics).incrementCounter(eq("yappc.ai.shape.derive.fallback [GH-90000]"), anyMap());
        }

        @Test
        @DisplayName("different tenants get distinct shape IDs [GH-90000]")
        void shouldIsolateTenantsOnDerive() { // GH-90000
            stubAiSuccess("{\"architecture\": {}, \"domainModel\": {}}"); // GH-90000

            ShapeSpec s1 = runPromise(() -> service.derive(intent("tenant-A [GH-90000]")));
            ShapeSpec s2 = runPromise(() -> service.derive(intent("tenant-B [GH-90000]")));

            assertNotEquals(s1.id(), s2.id()); // GH-90000
            assertEquals("tenant-A", s1.tenantId()); // GH-90000
            assertEquals("tenant-B", s2.tenantId()); // GH-90000
        }
    }

    @Nested
    @DisplayName("generateModel() [GH-90000]")
    class GenerateModelTests {

        @Test
        @DisplayName("returns SystemModel referencing the original ShapeSpec [GH-90000]")
        void shouldGenerateSystemModel() { // GH-90000
            stubAiSuccess("Design Rationale: Microservices for scalability [GH-90000]");

            ShapeSpec input = spec("tenant-123 [GH-90000]");
            SystemModel result = runPromise(() -> service.generateModel(input)); // GH-90000

            assertNotNull(result); // GH-90000
            assertEquals(input, result.shape()); // GH-90000
            assertNotNull(result.designRationale()); // GH-90000
            verify(aiService, times(1)).complete(any(CompletionRequest.class)); // GH-90000
            verify(auditLogger, times(1)).log(anyMap()); // GH-90000
        }

        @Test
        @DisplayName("records timer with tenant tag on success [GH-90000]")
        void shouldRecordTimerOnGenerateModel() { // GH-90000
            stubAiSuccess("Design Rationale: Simple monolith [GH-90000]");

            runPromise(() -> service.generateModel(spec("tenant-xyz [GH-90000]")));

            verify(metrics).recordTimer(eq("yappc.shape.generateModel [GH-90000]"), anyLong(),
                    any(Map.class)); // GH-90000
        }

        @Test
        @DisplayName("increments success counter on successful generateModel [GH-90000]")
        void shouldIncrementSuccessCounterOnGenerateModel() { // GH-90000
            stubAiSuccess("Design Rationale: Serverless for low cost [GH-90000]");

            runPromise(() -> service.generateModel(spec("tenant-xyz [GH-90000]")));

            verify(metrics).incrementCounter(contains("success [GH-90000]"), anyMap());
        }

        @Test
        @DisplayName("AI failure during generateModel uses fallback model and records fallback metric [GH-90000]")
        void shouldHandleAIServiceFailureOnGenerateModel() { // GH-90000
            stubAiFailure("LLM timeout [GH-90000]");

            SystemModel result = runPromise(() -> service.generateModel(spec("tenant-err [GH-90000]")));

            assertNotNull(result); // GH-90000
            assertNotNull(result.designRationale()); // GH-90000
            verify(metrics).incrementCounter(eq("yappc.ai.shape.systemModel.fallback [GH-90000]"), anyMap());
        }
    }
}
