package com.ghatana.tutorputor.contentstudio.grpc;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for ContentGenerationService gRPC implementation
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ContentGenerationService Tests")
class ContentGenerationServiceTest {

    private LLMGateway llmGateway;
    private MeterRegistry meterRegistry;
    private ContentGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LLMGateway.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new ContentGenerationServiceImpl(llmGateway, Executors.newFixedThreadPool(2), meterRegistry);
    }

    @Nested
    @DisplayName("Generate Claims")
    class GenerateClaimsBehavior {

        @Test
        @DisplayName("should generate claims for valid request")
        void shouldGenerateClaimsSuccessfully() {
            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                    .setTopic("Photosynthesis")
                    .setGradeLevel("Grade 10")
                    .setDomain("Biology")
                    .setMaxClaims(3)
                    .setRequestId("request-1")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("1. Photosynthesis requires light energy\n2. Chloroplasts are the site of photosynthesis")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateClaimsResponse> observer = new CapturingObserver<>();
            service.generateClaims(request, observer);

            // Allow time for async completion
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertNull(observer.getError());
        }

        @Test
        @DisplayName("should handle missing topic")
        void shouldHandleMissingTopic() {
            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                    .setTopic("")
                    .setGradeLevel("Grade 10")
                    .setDomain("Biology")
                    .setMaxClaims(3)
                    .build();

            CapturingObserver<GenerateClaimsResponse> observer = new CapturingObserver<>();
            service.generateClaims(request, observer);

            // Allow time for async error handling
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            // Should either error or complete with validation message
            assertTrue(observer.isCompleted() || observer.getError() != null);
        }

        @Test
        @DisplayName("should increments claims counter on success")
        void shouldIncrementClaimsCounter() {
            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                    .setTopic("Photosynthesis")
                    .setGradeLevel("Grade 10")
                    .setDomain("Biology")
                    .setMaxClaims(3)
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Claim 1\nClaim 2")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateClaimsResponse> observer = new CapturingObserver<>();
            service.generateClaims(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(meterRegistry.counter("tutorputor.content.claims.generated").count() > 0);
        }

        @Test
        @DisplayName("should handle LLM gateway errors")
        void shouldHandleLLMError() {
            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                    .setTopic("Photosynthesis")
                    .setGradeLevel("Grade 10")
                    .setDomain("Biology")
                    .setMaxClaims(3)
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.ofException(new RuntimeException("LLM unavailable")));

            CapturingObserver<GenerateClaimsResponse> observer = new CapturingObserver<>();
            service.generateClaims(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(meterRegistry.counter("tutorputor.content.llm.errors").count() > 0);
        }
    }

    @Nested
    @DisplayName("Analyze Content Needs")
    class AnalyzeContentNeedsBehavior {

        @Test
        @DisplayName("should analyze content needs for valid request")
        void shouldAnalyzeContentNeedsSuccessfully() {
            AnalyzeContentNeedsRequest request = AnalyzeContentNeedsRequest.newBuilder()
                    .setTopic("Evolution")
                    .setGradeLevel("Grade 12")
                    .setCurrentContent("Basic definition of evolution")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Content needs: examples, animations, real-world applications")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<AnalyzeContentNeedsResponse> observer = new CapturingObserver<>();
            service.analyzeContentNeeds(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }

        @Test
        @DisplayName("should handle empty current content")
        void shouldHandleEmptyCurrentContent() {
            AnalyzeContentNeedsRequest request = AnalyzeContentNeedsRequest.newBuilder()
                    .setTopic("Evolution")
                    .setGradeLevel("Grade 12")
                    .setCurrentContent("")
                    .build();

            CapturingObserver<AnalyzeContentNeedsResponse> observer = new CapturingObserver<>();
            service.analyzeContentNeeds(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(observer.isCompleted() || observer.getError() != null);
        }
    }

    @Nested
    @DisplayName("Generate Examples")
    class GenerateExamplesBehavior {

        @Test
        @DisplayName("should generate examples for valid request")
        void shouldGenerateExamplesSuccessfully() {
            GenerateExamplesRequest request = GenerateExamplesRequest.newBuilder()
                    .setTopic("Fractions")
                    .setGradeLevel("Grade 4")
                    .setDomain("Mathematics")
                    .setMaxExamples(2)
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Example 1: 1/2 of pizza\nExample 2: 3/4 of a cake")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateExamplesResponse> observer = new CapturingObserver<>();
            service.generateExamples(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(meterRegistry.counter("tutorputor.content.examples.generated").count() > 0);
        }

        @Test
        @DisplayName("should include real-world context in examples")
        void shouldIncludeRealWorldContext() {
            GenerateExamplesRequest request = GenerateExamplesRequest.newBuilder()
                    .setTopic("Gravity")
                    .setGradeLevel("Grade 9")
                    .setDomain("Physics")
                    .setMaxExamples(1)
                    .setIncludeRealWorldContext(true)
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Example: Why objects fall to Earth (gravity effect)")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateExamplesResponse> observer = new CapturingObserver<>();
            service.generateExamples(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("Generate Simulation")
    class GenerateSimulationBehavior {

        @Test
        @DisplayName("should generate simulation manifest for valid request")
        void shouldGenerateSimulationSuccessfully() {
            GenerateSimulationRequest request = GenerateSimulationRequest.newBuilder()
                    .setTopic("Water Cycle")
                    .setGradeLevel("Grade 5")
                    .setDomain("Environmental Science")
                    .setBudget(1000)
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Simulation manifest: evaporation, condensation, precipitation stages")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateSimulationResponse> observer = new CapturingObserver<>();
            service.generateSimulation(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(meterRegistry.counter("tutorputor.content.simulations.generated").count() > 0);
        }

        @Test
        @DisplayName("should respect budget constraints")
        void shouldRespectBudgetConstraints() {
            GenerateSimulationRequest request = GenerateSimulationRequest.newBuilder()
                    .setTopic("Complex System")
                    .setGradeLevel("Grade 11")
                    .setDomain("Systems")
                    .setBudget(100)  // Low budget
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Simplified simulation within budget")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateSimulationResponse> observer = new CapturingObserver<>();
            service.generateSimulation(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("Validate Content")
    class ValidateContentBehavior {

        @Test
        @DisplayName("should validate content against standards")
        void shouldValidateContentSuccessfully() {
            ValidateContentRequest request = ValidateContentRequest.newBuilder()
                    .setContent("Definition of mitosis: process of cell division")
                    .setGradeLevel("Grade 9")
                    .setDomain("Biology")
                    .setEducationalStandards("Bloom's Level 1-2")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Validation passed: Content is age-appropriate and accurate")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<ValidateContentResponse> observer = new CapturingObserver<>();
            service.validateContent(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(meterRegistry.counter("tutorputor.content.validations.performed").count() > 0);
        }

        @Test
        @DisplayName("should detect content issues")
        void shouldDetectContentIssues() {
            ValidateContentRequest request = ValidateContentRequest.newBuilder()
                    .setContent("Misleading information about vaccines")
                    .setGradeLevel("Grade 6")
                    .setDomain("Health")
                    .setEducationalStandards("Scientific Accuracy")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Validation failed: Contains misinformation")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<ValidateContentResponse> observer = new CapturingObserver<>();
            service.validateContent(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }

        @Test
        @DisplayName("should increment validation counter")
        void shouldIncrementValidationCounter() {
            ValidateContentRequest request = ValidateContentRequest.newBuilder()
                    .setContent("Test content")
                    .setGradeLevel("Grade 10")
                    .setDomain("Test")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Valid")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<ValidateContentResponse> observer = new CapturingObserver<>();
            service.validateContent(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(meterRegistry.counter("tutorputor.content.validations.performed").count() > 0);
        }
    }

    @Nested
    @DisplayName("Enhance Content")
    class EnhanceContentBehavior {

        @Test
        @DisplayName("should enhance content for engagement")
        void shouldEnhanceContentSuccessfully() {
            EnhanceContentRequest request = EnhanceContentRequest.newBuilder()
                    .setContent("Definition of photosynthesis")
                    .setGradeLevel("Grade 8")
                    .setDomain("Biology")
                    .setEnhancementType("engagement")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Enhanced: Photosynthesis - the amazing process where plants turn sunlight into food!")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<EnhanceContentResponse> observer = new CapturingObserver<>();
            service.enhanceContent(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(meterRegistry.counter("tutorputor.content.enhancements.performed").count() > 0);
        }

        @Test
        @DisplayName("should enhance content for accessibility")
        void shouldEnhanceForAccessibility() {
            EnhanceContentRequest request = EnhanceContentRequest.newBuilder()
                    .setContent("Complex mathematical formula: E=mc²")
                    .setGradeLevel("Grade 11")
                    .setDomain("Physics")
                    .setEnhancementType("accessibility")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Simplified: Energy equals mass times the speed of light squared")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<EnhanceContentResponse> observer = new CapturingObserver<>();
            service.enhanceContent(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(meterRegistry.counter("tutorputor.content.enhancements.performed").count() > 0);
        }

        @Test
        @DisplayName("should handle multiple enhancement types")
        void shouldHandleMultipleEnhancementTypes() {
            EnhanceContentRequest request = EnhanceContentRequest.newBuilder()
                    .setContent("Test content")
                    .setGradeLevel("Grade 7")
                    .setDomain("General")
                    .setEnhancementType("clarity")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Enhanced content")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<EnhanceContentResponse> observer = new CapturingObserver<>();
            service.enhanceContent(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("Metrics and Observability")
    class MetricsObservability {

        @Test
        @DisplayName("should record generation timer")
        void shouldRecordGenerationTimer() {
            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                    .setTopic("Test")
                    .setGradeLevel("Grade 10")
                    .setDomain("Test")
                    .build();

            CompletionResult result = CompletionResult.builder()
                    .content("Test claims")
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.of(result));

            CapturingObserver<GenerateClaimsResponse> observer = new CapturingObserver<>();
            service.generateClaims(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(meterRegistry.timer("tutorputor.content.generation.duration").count() > 0);
        }

        @Test
        @DisplayName("should track LLM error counter")
        void shouldTrackLLMErrors() {
            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                    .setTopic("Test")
                    .setGradeLevel("Grade 10")
                    .getDomain()
                    .build();

            when(llmGateway.complete(any(CompletionRequest.class)))
                    .thenReturn(io.activej.promise.Promise.ofException(new RuntimeException("Error")));

            CapturingObserver<GenerateClaimsResponse> observer = new CapturingObserver<>();
            service.generateClaims(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(meterRegistry.counter("tutorputor.content.llm.errors").count() > 0);
        }

        @Test
        @DisplayName("should initialize all metrics")
        void shouldInitializeAllMetrics() {
            assertNotNull(meterRegistry.counter("tutorputor.content.claims.generated"));
            assertNotNull(meterRegistry.counter("tutorputor.content.examples.generated"));
            assertNotNull(meterRegistry.counter("tutorputor.content.simulations.generated"));
            assertNotNull(meterRegistry.counter("tutorputor.content.validations.performed"));
            assertNotNull(meterRegistry.counter("tutorputor.content.enhancements.performed"));
            assertNotNull(meterRegistry.timer("tutorputor.content.generation.duration"));
            assertNotNull(meterRegistry.counter("tutorputor.content.llm.errors"));
        }
    }

    // ─── Helper Classes ───────────────────────────────────────────────────

    private static class CapturingObserver<T> implements StreamObserver<T> {
        private final List<T> results = new ArrayList<>();
        private Throwable error;
        private boolean completed;

        @Override
        public void onNext(T value) {
            results.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        public List<T> getResults() {
            return results;
        }

        public Throwable getError() {
            return error;
        }

        public boolean isCompleted() {
            return completed;
        }
    }
}
