package com.ghatana.tutorputor.content.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ghatana.platform.core.async.Promise;
import com.ghatana.tutorputor.agents.ContentGenerationService;
import com.ghatana.tutorputor.domain.content.*;
import com.ghatana.tutorputor.domain.curriculum.LearningObjective;
import com.ghatana.tutorputor.domain.curriculum.CurriculumContext;
import com.ghatana.tutorputor.llm.LlmGateway;
import io.activej.eventloop.EventloopTestBase;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @doc.type class
 * @doc.purpose Integration tests for TutorPutor content generation workflow
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("TutorPutor Content Generation Workflow Integration Tests")
class ContentGenerationWorkflowIntegrationTest extends EventloopTestBase {

    private ContentGenerationWorkflow workflow;
    private ContentGenerationService contentService;

    @Mock
    private LlmGateway llmGateway;

    @Mock
    private MetricsCollector metrics;

    private CurriculumContext testContext;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        contentService = new ContentGenerationService(llmGateway, metrics);
        workflow = new ContentGenerationWorkflow(contentService, metrics);
        testContext = createTestContext("tenant-1", "curriculum-101");
    }

    @Nested
    @DisplayName("Single-Step Content Generation")
    class SingleStepGenerationTests {

        @Test
        @DisplayName("Should generate claims from learning objective")
        void shouldGenerateClaimsFromObjective() {
            // Setup
            LearningObjective objective = createLearningObjective(
                    "obj-1",
                    "Understand photosynthesis",
                    "Biology"
            );

            ContentGenerationRequest.ClaimGenerationRequest claimRequest =
                    new ContentGenerationRequest.ClaimGenerationRequest(
                            objective.id(),
                            objective.title(),
                            "intermediate"
                    );

            mockLlmResponse("claim-1", "Photosynthesis converts light energy to chemical energy");

            // Execute
            GeneratedClaim claim = runPromise(() ->
                    contentService.generateClaim(claimRequest, testContext)
            );

            // Verify
            assertThat(claim).isNotNull();
            assertThat(claim.claimText()).isNotEmpty();
            verify(metrics).incrementCounter(
                    argThat(s -> s.contains("claimsGenerated")),
                    argThat(map -> map.containsValue("tenant-1"))
            );
        }

        @Test
        @DisplayName("Should analyze content needs from claims")
        void shouldAnalyzeContentNeeds() {
            // Setup
            List<String> claims = List.of(
                    "Photosynthesis is light-dependent reaction",
                    "Chlorophyll absorbs light energy"
            );

            mockLlmResponse("need-1", "Students need visual understanding of light spectrum");

            // Execute
            ContentNeeds needs = runPromise(() ->
                    contentService.analyzeContentNeeds(claims, testContext)
            );

            // Verify
            assertThat(needs).isNotNull();
            assertThat(needs.requiredContentTypes())
                    .contains("visual", "explanation");
        }

        @Test
        @DisplayName("Should generate examples for claims")
        void shouldGenerateExamplesForClaims() {
            // Setup
            String claim = "Mitochondria produce ATP";
            mockLlmResponse("example-1", "In muscle cells, ATP powers contraction");

            // Execute
            List<String> examples = runPromise(() ->
                    contentService.generateExamples(claim, "elementary", 3, testContext)
            );

            // Verify
            assertThat(examples).hasSize(3);
            verify(llmGateway, times(3)).generateContent(
                    argThat(r -> r.contains("example")),
                    anyMap()
            );
        }
    }

    @Nested
    @DisplayName("Multi-Step Workflow Execution")
    class MultiStepWorkflowTests {

        @Test
        @DisplayName("Should execute complete content generation pipeline")
        void shouldExecuteCompletePipeline() {
            // Setup
            LearningObjective objective = createLearningObjective(
                    "obj-101",
                    "Cell division mitosis",
                    "Biology"
            );

            // Mock all LLM responses
            when(llmGateway.generateContent(
                    argThat(req -> req.contains("claim")), anyMap()
            )).thenReturn(Promise.of("Mitosis divides parent cell into two identical daughters"));

            when(llmGateway.generateContent(
                    argThat(req -> req.contains("example")), anyMap()
            )).thenReturn(Promise.of("Example: Root tip cells divide during growth"));

            when(llmGateway.generateContent(
                    argThat(req -> req.contains("simulation")), anyMap()
            )).thenReturn(Promise.of("Simulation: Interactive 3D model of mitosis stages"));

            // Execute workflow
            ContentGenerationWorkflow.Result result = runPromise(() ->
                    workflow.generateCompleteContent(objective, testContext)
            );

            // Verify
            assertTrue(result.success());
            assertThat(result.generatedContent()).isNotEmpty();
            assertThat(result.executionTime()).isGreaterThan(0);

            // Verify metrics
            verify(metrics, atLeastOnce()).incrementCounter(
                    argThat(s -> s.contains("Generated")),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should handle workflow with claim generation, analysis, and enhancement")
        void shouldGenerateClaimsAnalyzeAndEnhance() {
            // Setup claims
            List<String> claims = List.of(
                    "DNA stores genetic information",
                    "Genes control protein synthesis"
            );

            // Mock LLM responses for each stage
            mockLlmResponse("need", "Visual diagrams of DNA structure needed");
            mockLlmResponse("example", "Example: Eye color determined by gene variants");
            mockLlmResponse("enhanced", "Enhanced with molecular structure details");

            // Execute
            ContentGenerationWorkflow.Result result = runPromise(() ->
                    workflow.generateWithAnalysisAndEnhancement(claims, testContext)
            );

            // Verify
            assertTrue(result.success());
            assertThat(result.analyzedNeeds()).isNotEmpty();
            assertThat(result.enhancedContent()).isNotEmpty();
        }

        @Test
        @DisplayName("Should execute parallel content generation for multiple claims")
        void shouldGenerateContentInParallel() {
            // Setup
            List<String> claims = List.of(
                    "Claim 1: Photosynthesis occurs in chloroplasts",
                    "Claim 2: Light reactions produce ATP",
                    "Claim 3: Calvin cycle fixes carbon dioxide"
            );

            // Mock LLM to generate examples
            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenAnswer(inv -> Promise.of("Generated example for: " + inv.getArgument(0)));

            // Execute
            long startTime = System.currentTimeMillis();
            List<String> generatedContent = runPromise(() ->
                    workflow.generateExamplesInParallel(claims, testContext)
            );
            long duration = System.currentTimeMillis() - startTime;

            // Verify all examples generated
            assertThat(generatedContent).hasSize(3);

            // Sequential would take ~300ms (100ms per claim), parallel should be ~100ms
            assertThat(duration).isLessThan(200);
        }
    }

    @Nested
    @DisplayName("Error Handling and Recovery")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle LLM timeout gracefully")
        void shouldHandleLlmTimeout() {
            // Setup - LLM times out
            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenReturn(Promise.ofException(
                            new TimeoutException("LLM request timeout")
                    ));

            // Execute
            ContentGenerationWorkflow.Result result = runPromise(() ->
                    workflow.generateWithTimeout(
                            "objective-1",
                            5000, // 5 second timeout
                            testContext
                    )
            );

            // Verify graceful error handling
            assertFalse(result.success());
            assertThat(result.errorMessage()).contains("timeout");
            verify(metrics).incrementCounter(
                    argThat(s -> s.contains("llmError")),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should retry failed content generation")
        void shouldRetryFailedGeneration() {
            // Setup - fail once, then succeed
            AtomicInteger callCount = new AtomicInteger(0);
            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenAnswer(inv -> {
                        if (callCount.incrementAndGet() < 2) {
                            return Promise.ofException(new RuntimeException("Temporary failure"));
                        }
                        return Promise.of("Generated after retry");
                    });

            // Execute with retry logic
            ContentGenerationWorkflow.Result result = runPromise(() ->
                    workflow.generateWithRetry("objective-1", 3, testContext)
            );

            // Verify success after retry
            assertTrue(result.success());
            assertThat(result.generatedContent())
                    .contains("Generated after retry");
            verify(metrics, atLeastOnce()).incrementCounter(
                    argThat(s -> s.contains("retry")),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should handle concurrent generation failures")
        void shouldHandleConcurrentFailures() {
            // Setup
            List<String> objectives = List.of("obj-1", "obj-2", "obj-3");

            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenReturn(Promise.ofException(
                            new RuntimeException("Service unavailable")
                    ));

            // Execute parallel generation (some will fail)
            List<ContentGenerationWorkflow.Result> results = runPromise(() ->
                    workflow.generateContentBatch(objectives, testContext)
            );

            // Verify all handled properly
            assertThat(results).hasSize(objectives.size());
            assertThat(results).allSatisfy(r -> assertFalse(r.success()));
        }
    }

    @Nested
    @DisplayName("Curriculum-Aware Content Generation")
    class CurriculumAwareGenerationTests {

        @Test
        @DisplayName("Should generate content aligned with curriculum standards")
        void shouldGenerateAlignedContent() {
            // Setup curriculum context
            CurriculumContext standards = new CurriculumContext(
                    "grade-9",
                    "NGSS",
                    List.of("HS-LS1-1", "HS-LS1-2")
            );

            String claim = "Photosynthesis is essential for life";

            // Mock LLM with curriculum alignment
            mockLlmResponse("aligned", "Content aligned with HS-LS1-1 standards");

            // Execute
            GeneratedContent content = runPromise(() ->
                    workflow.generateCurriculumAlignedContent(
                            claim,
                            standards,
                            testContext
                    )
            );

            // Verify alignment
            assertThat(content).isNotNull();
            assertThat(content.alignedStandards())
                    .contains("HS-LS1-1", "HS-LS1-2");
        }

        @Test
        @DisplayName("Should respect cognitive level in content generation")
        void shouldRespectCognitiveLevel() {
            // Setup different levels
            List<String> levels = List.of(
                    "remember",
                    "understand",
                    "apply",
                    "analyze"
            );

            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenAnswer(inv -> Promise.of("Content for: " + inv.getArgument(0)));

            // Execute generation for each level
            for (String level : levels) {
                GeneratedContent content = runPromise(() ->
                        workflow.generateContentAtLevel(
                                "Photosynthesis",
                                level,
                                testContext
                        )
                );

                assertThat(content).isNotNull();
                assertThat(content.cognitiveLevelTargeted()).isEqualTo(level);
            }
        }
    }

    @Nested
    @DisplayName("Metrics and Performance")
    class MetricsAndPerformanceTests {

        @Test
        @DisplayName("Should record generation latency")
        void shouldRecordGenerationLatency() {
            // Setup with 50ms latency
            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenAnswer(inv -> {
                        Thread.sleep(50);
                        return Promise.of("Generated content");
                    });

            // Execute
            runPromise(() ->
                    contentService.generateClaim(
                            new ContentGenerationRequest.ClaimGenerationRequest(
                                    "obj-1", "Test", "intermediate"
                            ),
                            testContext
                    )
            );

            // Verify timing recorded
            ArgumentCaptor<Long> durationCaptor = ArgumentCaptor.forClass(Long.class);
            verify(metrics).recordTimer(
                    argThat(s -> s.contains("generation")),
                    durationCaptor.capture(),
                    anyMap()
            );
            assertThat(durationCaptor.getValue()).isGreaterThanOrEqualTo(50);
        }

        @Test
        @DisplayName("Should track content quality metrics")
        void shouldTrackQualityMetrics() {
            // Setup
            mockLlmResponse("high-quality", "Well-structured content with clear explanations");

            // Execute
            GeneratedContent content = runPromise(() ->
                    contentService.generateAndEvaluateContent("objective", testContext)
            );

            // Verify quality metrics tracked
            verify(metrics).recordMetric(
                    argThat(s -> s.contains("quality")),
                    anyDouble(),
                    anyMap()
            );
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 5, 10, 20})
        @DisplayName("Should measure throughput for batch content generation")
        void shouldMeasureBatchThroughput(int batchSize) {
            // Setup
            when(llmGateway.generateContent(anyString(), anyMap()))
                    .thenReturn(Promise.of("Generated content"));

            // Execute batch
            long startTime = System.currentTimeMillis();
            List<GeneratedContent> batch = runPromise(() ->
                    workflow.generateBatch(batchSize, testContext)
            );
            long duration = System.currentTimeMillis() - startTime;

            // Verify
            assertThat(batch).hasSize(batchSize);

            // Record throughput
            double throughput = batchSize / (duration / 1000.0);
            verify(metrics).recordMetric(
                    "batch.throughput",
                    throughput,
                    Map.of("batchSize", String.valueOf(batchSize))
            );
        }
    }

    @Nested
    @DisplayName("Tenant Isolation and Multi-Tenancy")
    class TenantIsolationTests {

        @Test
        @DisplayName("Should isolate content generation between tenants")
        void shouldIsolateTenantContent() {
            // Setup two different tenants
            CurriculumContext tenant1Context = createTestContext("tenant-1", "curriculum-101");
            CurriculumContext tenant2Context = createTestContext("tenant-2", "curriculum-202");

            mockLlmResponse("content-1", "Content for tenant 1");
            mockLlmResponse("content-2", "Content for tenant 2");

            // Execute for both tenants
            GeneratedContent content1 = runPromise(() ->
                    contentService.generateClaim(
                            new ContentGenerationRequest.ClaimGenerationRequest(
                                    "obj-1", "Title", "intermediate"
                            ),
                            tenant1Context
                    )
            );

            GeneratedContent content2 = runPromise(() ->
                    contentService.generateClaim(
                            new ContentGenerationRequest.ClaimGenerationRequest(
                                    "obj-2", "Title", "intermediate"
                            ),
                            tenant2Context
                    )
            );

            // Verify isolation
            assertThat(content1).isNotNull();
            assertThat(content2).isNotNull();
            verify(metrics, atLeast(2)).incrementCounter(
                    anyString(),
                    argThat(map -> map.containsValue("tenant-1") || 
                                   map.containsValue("tenant-2"))
            );
        }
    }

    // Helper Methods

    private void mockLlmResponse(String key, String response) {
        when(llmGateway.generateContent(
                argThat(req -> req.toLowerCase().contains(key.toLowerCase())),
                anyMap()
        )).thenReturn(Promise.of(response));
    }

    private LearningObjective createLearningObjective(
            String id, String title, String subject) {
        return new LearningObjective(
                id,
                title,
                subject,
                "intermediate"
        );
    }

    private CurriculumContext createTestContext(String tenantId, String curriculumId) {
        return new CurriculumContext(
                tenantId,
                curriculumId,
                "grade-9",
                "NGSS"
        );
    }

    // Test Data Classes

    static class GeneratedClaim {
        private final String claimId;
        private final String claimText;

        GeneratedClaim(String claimId, String claimText) {
            this.claimId = claimId;
            this.claimText = claimText;
        }

        String claimId() { return claimId; }
        String claimText() { return claimText; }
    }

    static class ContentNeeds {
        private final List<String> contentTypes;

        ContentNeeds(List<String> contentTypes) {
            this.contentTypes = contentTypes;
        }

        List<String> requiredContentTypes() { return contentTypes; }
    }

    static class GeneratedContent {
        private final String contentId;
        private final String text;
        private final List<String> standards;
        private final String cognitiveLevel;

        GeneratedContent(String contentId, String text, List<String> standards,
                        String cognitiveLevel) {
            this.contentId = contentId;
            this.text = text;
            this.standards = standards;
            this.cognitiveLevel = cognitiveLevel;
        }

        String contentId() { return contentId; }
        List<String> alignedStandards() { return standards; }
        String cognitiveLevelTargeted() { return cognitiveLevel; }
    }

    interface MetricsCollector {
        void incrementCounter(String name, Map<String, String> tags);
        void recordTimer(String name, long durationMs, Map<String, String> tags);
        void recordMetric(String name, double value, Map<String, String> tags);
    }
}
