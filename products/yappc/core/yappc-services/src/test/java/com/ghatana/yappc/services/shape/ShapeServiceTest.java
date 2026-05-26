package com.ghatana.yappc.services.shape;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.artifact.ArtifactGraphResponse;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.shape.SystemModel;
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
 * @doc.purpose Tests for ShapeService implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ShapeService")
class ShapeServiceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private ShapeRepository shapeRepository;
    private ShapeArtifactGraphLineageService lineageService;
    private ShapeService service;

    @BeforeEach
    void setUp() { 
        aiService = mock(CompletionService.class); 
        auditLogger = mock(AuditLogger.class); 
        metrics = mock(MetricsCollector.class); 
        shapeRepository = mock(ShapeRepository.class);
        lineageService = mock(ShapeArtifactGraphLineageService.class);
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
                .metadata(Map.of(
                        "workspaceId", "workspace-123",
                        "projectId", "project-123",
                        "userId", "user-123",
                        "evidenceIds", "evidence-intent-1"))
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
        @SuppressWarnings("unchecked")
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
        @DisplayName("AI failure on derive uses fallback shape and records fallback metric")
        void shouldHandleAIServiceFailureOnDerive() { 
            stubAiFailure("AI service unavailable");

            ShapeSpec result = runPromise(() -> service.derive(intent("tenant-fail")));

            assertNotNull(result); 
            assertEquals("intent-123", result.intentRef()); 
            verify(metrics).incrementCounter(eq("yappc.ai.shape.derive.fallback"), anyMap());
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

        @Test
        @DisplayName("persists derived shape with intent lineage when repository is configured")
        void shouldPersistDerivedShape() {
            stubAiSuccess("{\"architecture\": {}, \"domainModel\": {}}");
            when(shapeRepository.saveShape(any(ShapeSpec.class), any(ShapePersistenceContext.class)))
                    .thenAnswer(invocation -> Promise.of(new ShapeVersionRecord(
                            "project-123:shape-123:v1",
                            "tenant-123",
                            "workspace-123",
                            "project-123",
                            invocation.getArgument(0, ShapeSpec.class).id(),
                            1,
                            invocation.getArgument(0, ShapeSpec.class),
                            null,
                            "user-123",
                            java.time.Instant.now(),
                            "intent-123",
                            List.of("evidence-intent-1"),
                            Map.of())));
            service = new ShapeServiceImpl(aiService, auditLogger, metrics, shapeRepository);

            ShapeSpec result = runPromise(() -> service.derive(intent("tenant-123")));

            assertEquals("workspace-123", result.metadata().get("workspaceId"));
            ArgumentCaptor<ShapePersistenceContext> contextCaptor =
                    ArgumentCaptor.forClass(ShapePersistenceContext.class);
            verify(shapeRepository).saveShape(eq(result), contextCaptor.capture());
            ShapePersistenceContext context = contextCaptor.getValue();
            assertEquals("workspace-123", context.workspaceId());
            assertEquals("project-123", context.projectId());
            assertEquals("intent-123", context.sourceIntentId());
            assertEquals(List.of("evidence-intent-1"), context.intentEvidenceIds());
        }

        @Test
        @DisplayName("records shape artifact graph lineage after persistence")
        void shouldRecordShapeArtifactGraphLineage() {
            stubAiSuccess("{\"architecture\": {}, \"domainModel\": {}}");
            when(shapeRepository.saveShape(any(ShapeSpec.class), any(ShapePersistenceContext.class)))
                    .thenAnswer(invocation -> Promise.of(new ShapeVersionRecord(
                            "project-123:shape-123:v1",
                            "tenant-123",
                            "workspace-123",
                            "project-123",
                            invocation.getArgument(0, ShapeSpec.class).id(),
                            1,
                            invocation.getArgument(0, ShapeSpec.class),
                            null,
                            "user-123",
                            java.time.Instant.now(),
                            "intent-123",
                            List.of("evidence-intent-1"),
                            Map.of())));
            when(lineageService.recordShapeLineage(any(ShapeSpec.class)))
                    .thenReturn(Promise.of(new ArtifactGraphResponse(true, "ingest", Map.of(), "ok")));
            service = new ShapeServiceImpl(aiService, auditLogger, metrics, shapeRepository, lineageService);

            ShapeSpec result = runPromise(() -> service.derive(intent("tenant-123")));

            verify(lineageService).recordShapeLineage(eq(result));
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
        @SuppressWarnings("unchecked")
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
        @DisplayName("AI failure during generateModel uses fallback model and records fallback metric")
        void shouldHandleAIServiceFailureOnGenerateModel() { 
            stubAiFailure("LLM timeout");

            SystemModel result = runPromise(() -> service.generateModel(spec("tenant-err")));

            assertNotNull(result); 
            assertNotNull(result.designRationale()); 
            verify(metrics).incrementCounter(eq("yappc.ai.shape.systemModel.fallback"), anyMap());
        }
    }
}
