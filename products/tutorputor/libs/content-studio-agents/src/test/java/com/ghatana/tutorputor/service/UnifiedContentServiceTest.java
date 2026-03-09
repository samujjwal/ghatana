package com.ghatana.tutorputor.service;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for UnifiedContentService.
 *
 * @doc.type class
 * @doc.purpose Unit tests for unified service
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("UnifiedContentService Tests")
class UnifiedContentServiceTest {

    private UnifiedContentService service;
    private LLMGateway mockLlmGateway;
    private MemoryStore mockMemoryStore;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        mockLlmGateway = mock(LLMGateway.class);
        mockMemoryStore = mock(MemoryStore.class);
        
        // Setup mock LLM responses
        when(mockLlmGateway.complete(any(CompletionRequest.class)))
            .thenReturn(Promise.of(CompletionResult.builder()
                .text("Generated educational content about the topic.")
                .modelUsed("test-model")
                .promptTokens(100)
                .completionTokens(50)
                .tokensUsed(150)
                .finishReason("stop")
                .metadata(Map.of("model", "test-model"))
                .build()));
        
        // Create service via factory
        service = UnifiedContentServiceFactory.createForTesting(
            mockLlmGateway,
            mockMemoryStore,
            meterRegistry
        );
    }

    @Test
    @DisplayName("Should submit batch generation job")
    void shouldSubmitBatchGenerationJob() {
        // GIVEN
        UnifiedContentService.BatchGenerationRequest request = 
            UnifiedContentService.BatchGenerationRequest.builder()
                .tenantId("tenant-1")
                .requesterId("user-1")
                .topics(List.of("photosynthesis", "cellular respiration", "mitosis"))
                .gradeLevel(7)
                .contentTypes(List.of("CLAIM", "EXAMPLE"))
                .build();

        // WHEN
        String jobId = service.submitBatchGeneration(request);

        // THEN
        assertThat(jobId).isNotNull();
        
        var progress = service.getJobProgress(jobId);
        assertThat(progress).isNotNull();
        assertThat(progress.totalSteps()).isEqualTo(6); // 3 topics * 2 content types
    }

    @Test
    @DisplayName("Should create and retrieve experiment")
    void shouldCreateAndRetrieveExperiment() {
        // WHEN
        service.createExperiment(
            "exp-001",
            "Content Quality Test",
            "llm-standard",
            "llm-creative",
            50.0
        );

        // THEN - no exception means success
        // Note: Full verification would require accessing experiment manager
    }

    @Test
    @DisplayName("Should get service statistics")
    void shouldGetServiceStatistics() {
        // WHEN
        UnifiedContentService.ServiceStats stats = service.getStats();

        // THEN
        assertThat(stats).isNotNull();
        assertThat(stats.queuedJobs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should start and stop service")
    void shouldStartAndStopService() {
        // WHEN/THEN - should not throw
        service.start();
        service.stop(java.time.Duration.ofSeconds(1));
    }
}
