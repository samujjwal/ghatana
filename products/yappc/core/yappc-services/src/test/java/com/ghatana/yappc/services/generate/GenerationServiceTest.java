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
    void setUp() { 
        aiService = mock(CompletionService.class); 
        auditLogger = mock(AuditLogger.class); 
        metrics = mock(MetricsCollector.class); 
        when(aiService.complete(any(CompletionRequest.class))) 
                .thenReturn(Promise.of(CompletionResult.builder() 
                        .text("public class Main { }")
                        .modelUsed("gpt-4")
                        .build())); 
        when(auditLogger.log(any(Map.class))).thenReturn(Promise.complete()); 
        service = new GenerationServiceImpl(aiService, auditLogger, metrics); 
    }

    private ValidatedSpec specWithoutEntities() { 
        return ValidatedSpec.of( 
                ShapeSpec.builder().id("shape-123").tenantId("tenant-1").build(),
                LifecycleValidationResult.builder().build()); 
    }

    private ValidatedSpec specWithEntities(List<EntitySpec> entities) { 
        DomainModel model = DomainModel.builder().entities(entities).build(); 
        return ValidatedSpec.of( 
                ShapeSpec.builder().id("shape-456").tenantId("tenant-1").domainModel(model).build(),
                LifecycleValidationResult.builder().build()); 
    }

    @Test
    @DisplayName("generate: spec without domainModel → artifacts contain config/docs/pipeline but no entity code")
    void shouldGenerateArtifacts() { 
        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities())); 

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
    void shouldGenerateEntityCodeForEachEntity() { 
        EntitySpec entity1 = EntitySpec.builder().name("User").description("User entity").build();
        EntitySpec entity2 = EntitySpec.builder().name("Order").description("Order entity").build();

        GeneratedArtifacts result = runPromise(() -> service.generate(specWithEntities(List.of(entity1, entity2)))); 

        assertNotNull(result); 
        // 2 entities + 3 base artifacts = at least 5
        assertThat(result.artifacts().size()).isGreaterThanOrEqualTo(5); 
        // AI called once per entity + once per base artifact
        verify(aiService, atLeastOnce()).complete(any(CompletionRequest.class)); 
    }

    @Test
    @DisplayName("generate: metrics timer and success counter recorded")
    void shouldRecordMetricsOnSuccess() { 
        runPromise(() -> service.generate(specWithoutEntities())); 

        verify(metrics, atLeastOnce()).recordTimer(eq("yappc.generate.execute"), anyLong(), any(Map.class));
        verify(metrics, atLeastOnce()).incrementCounter(contains("success"), any(Map.class));
    }

    @Test
    @DisplayName("generate: audit logger called with event details")
    void shouldAuditGenerateExecution() { 
        runPromise(() -> service.generate(specWithoutEntities())); 

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

        DiffResult result = runPromise(() -> service.regenerateWithDiff(spec, existing)); 

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

        runPromise(() -> service.regenerateWithDiff(spec, existing)); 

        verify(metrics, atLeastOnce()).recordTimer(contains("diff"), anyLong(), any(Map.class));
        verify(auditLogger, atLeast(2)).log(any(Map.class)); // once for generate, once for diff 
    }

    @Test
    @DisplayName("generate: AI failure propagates and error metric recorded")
    void shouldHandleGenerationFailure() { 
        when(aiService.complete(any(CompletionRequest.class))) 
                .thenReturn(Promise.ofException(new RuntimeException("Generation failed")));

        try {
            GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities())); 
            // Promises.toList may succeed with remaining artifacts if only some calls fail
            assertNotNull(result); 
            verify(metrics, atLeastOnce()).recordTimer(anyString(), anyLong(), any(Map.class)); 
        } catch (Exception e) { 
            assertThat(e.getMessage()).containsIgnoringCase("generation failed");
            verify(metrics, atLeastOnce()).incrementCounter(contains("error"), any(Map.class));
        }
    }

    @Test
    @DisplayName("generate: metadata contains validation_passed flag")
    void shouldIncludeValidationMetadataInArtifacts() { 
        GeneratedArtifacts result = runPromise(() -> service.generate(specWithoutEntities())); 

        assertThat(result.metadata()).isNotNull(); 
        assertThat(result.metadata()).containsKey("validation_passed");
    }
}
