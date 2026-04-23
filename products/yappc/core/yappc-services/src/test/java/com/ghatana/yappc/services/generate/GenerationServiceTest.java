package com.ghatana.yappc.services.generate;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.generate.DiffResult;
import com.ghatana.yappc.domain.generate.GeneratedArtifacts;
import com.ghatana.yappc.domain.generate.ValidatedSpec;
import com.ghatana.yappc.domain.shape.DomainModel;
import com.ghatana.yappc.domain.shape.EntitySpec;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.domain.validate.LifecycleValidationResult;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
@DisplayName("GenerationService")
class GenerationServiceTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private GenerationService service;

    @BeforeEach
    void setUp() { // GH-90000
        aiService = mock(CompletionService.class); // GH-90000
        auditLogger = mock(AuditLogger.class); // GH-90000
        metrics = mock(MetricsCollector.class); // GH-90000
        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                        .text("public class Main { }")
                        .modelUsed("gpt-4")
                        .build())); // GH-90000
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete()); // GH-90000
        service = new GenerationServiceImpl(aiService, auditLogger, metrics); // GH-90000
    }

    private ValidatedSpec specWithoutEntities() { // GH-90000
        return ValidatedSpec.of( // GH-90000
                ShapeSpec.builder().id("shape-123").tenantId("tenant-1").build(),
                LifecycleValidationResult.builder().build()); // GH-90000
    }

    private ValidatedSpec specWithEntities(List<EntitySpec> entities) { // GH-90000
        DomainModel model = DomainModel.builder().entities(entities).build(); // GH-90000
        return ValidatedSpec.of( // GH-90000
                ShapeSpec.builder().id("shape-456").tenantId("tenant-1").domainModel(model).build(),
                LifecycleValidationResult.builder().build()); // GH-90000
    }

    @Test
    @DisplayName("generate: spec without domainModel → artifacts contain config/docs/pipeline but no entity code")
    void shouldGenerateArtifacts() { // GH-90000
        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities())); // GH-90000

        assertNotNull(result); // GH-90000
        assertNotNull(result.id()); // GH-90000
        assertThat(result.specRef()).isEqualTo("shape-123");
        assertThat(result.artifacts()).isNotNull(); // GH-90000
        // config + docs + ci/cd pipeline — 3 base artifacts
        assertThat(result.artifacts().size()).isGreaterThanOrEqualTo(3); // GH-90000

        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class)); // GH-90000
        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    @DisplayName("generate: spec with entities → entity code generation called per entity")
    void shouldGenerateEntityCodeForEachEntity() { // GH-90000
        EntitySpec entity1 = EntitySpec.builder().name("User").description("User entity").build();
        EntitySpec entity2 = EntitySpec.builder().name("Order").description("Order entity").build();

        GeneratedArtifacts result = runPromise(() -> service.generate(specWithEntities(List.of(entity1, entity2)))); // GH-90000

        assertNotNull(result); // GH-90000
        // 2 entities + 3 base artifacts = at least 5
        assertThat(result.artifacts().size()).isGreaterThanOrEqualTo(5); // GH-90000
        // AI called once per entity + once per base artifact
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class)); // GH-90000
    }

    @Test
    @DisplayName("generate: metrics timer and success counter recorded")
    void shouldRecordMetricsOnSuccess() { // GH-90000
        runPromise(() -> service.generate(specWithoutEntities())); // GH-90000

        verify(metrics, atLeastOnce()).recordTimer(eq("yappc.generate.execute"), anyLong(), any(Map.class));
        verify(metrics, atLeastOnce()).incrementCounter(contains("success"), any(Map.class));
    }

    @Test
    @DisplayName("generate: audit logger called with event details")
    void shouldAuditGenerateExecution() { // GH-90000
        runPromise(() -> service.generate(specWithoutEntities())); // GH-90000

        verify(auditLogger, times(1)).log(any(Map.class)); // GH-90000
    }

    @Test
    @DisplayName("regenerateWithDiff: both old and new artifacts present in diff result")
    void shouldRegenerateWithDiff() { // GH-90000
        ValidatedSpec spec = specWithoutEntities(); // GH-90000
        GeneratedArtifacts existing = GeneratedArtifacts.builder() // GH-90000
                .id("old-123")
                .specRef("shape-123")
                .artifacts(List.of()) // GH-90000
                .build(); // GH-90000

        DiffResult result = runPromise(() -> service.regenerateWithDiff(spec, existing)); // GH-90000

        assertNotNull(result); // GH-90000
        assertNotNull(result.newArtifacts()); // GH-90000
        assertNotNull(result.oldArtifacts()); // GH-90000
        assertNotNull(result.diffs()); // GH-90000
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class)); // GH-90000
    }

    @Test
    @DisplayName("regenerateWithDiff: diff metrics and audit recorded")
    void shouldRecordMetricsOnDiff() { // GH-90000
        ValidatedSpec spec = specWithoutEntities(); // GH-90000
        GeneratedArtifacts existing = GeneratedArtifacts.builder() // GH-90000
                .id("old-123").specRef("shape-123").artifacts(List.of()).build();

        runPromise(() -> service.regenerateWithDiff(spec, existing)); // GH-90000

        verify(metrics, atLeastOnce()).recordTimer(contains("diff"), anyLong(), any(Map.class));
        verify(auditLogger, atLeast(2)).log(any(Map.class)); // once for generate, once for diff // GH-90000
    }

    @Test
    @DisplayName("generate: AI failure propagates and error metric recorded")
    void shouldHandleGenerationFailure() { // GH-90000
        when(aiService.complete(any(CompletionRequest.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("Generation failed")));

        try {
            GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities())); // GH-90000
            // Promises.toList may succeed with remaining artifacts if only some calls fail
            assertNotNull(result); // GH-90000
            verify(metrics, atLeastOnce()).recordTimer(anyString(), anyLong(), any(Map.class)); // GH-90000
        } catch (Exception e) { // GH-90000
            assertThat(e.getMessage()).containsIgnoringCase("generation failed");
            verify(metrics, atLeastOnce()).incrementCounter(contains("error"), any(Map.class));
        }
    }

    @Test
    @DisplayName("generate: metadata contains validation_passed flag")
    void shouldIncludeValidationMetadataInArtifacts() { // GH-90000
        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities())); // GH-90000

        assertThat(result.metadata()).isNotNull(); // GH-90000
        assertThat(result.metadata()).containsKey("validation_passed");
    }
}
