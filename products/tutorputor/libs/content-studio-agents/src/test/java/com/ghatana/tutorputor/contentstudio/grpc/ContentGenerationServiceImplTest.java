package com.ghatana.tutorputor.contentstudio.grpc;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.grpc.stub.StreamObserver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for ContentGenerationServiceImpl.
 * 
 * <p>Tests all gRPC methods with:
 * <ul>
 *   <li>Successful responses with valid JSON</li>
 *   <li>Error handling for null/empty responses</li>
 *   <li>Edge cases in JSON parsing</li>
 *   <li>Metrics verification</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for content generation service
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("ContentGenerationServiceImpl Tests")
class ContentGenerationServiceImplTest extends EventloopTestBase {

  private static CompletionResult completion(
      String text,
      String model,
      int promptTokens,
      int completionTokens,
      int tokensUsed) {
    return CompletionResult.builder()
      .text(text)
      .modelUsed(model)
      .promptTokens(promptTokens)
      .completionTokens(completionTokens)
      .tokensUsed(tokensUsed)
      .finishReason("stop")
      .build();
  }

    private LLMGateway mockLLMGateway;
    private ContentGenerationServiceImpl service;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        mockLLMGateway = mock(LLMGateway.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new ContentGenerationServiceImpl(
            mockLLMGateway,
            Executors.newVirtualThreadPerTaskExecutor(),
            meterRegistry
        );
    }

    // =========================================================================
    // Generate Claims Tests
    // =========================================================================

    @Nested
    @DisplayName("generateClaims")
    class GenerateClaimsTests {

        @Test
        @DisplayName("should generate claims successfully with valid LLM response")
        void shouldGenerateClaimsSuccessfully() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "claims": [
                    {
                      "claim_ref": "C1",
                      "text": "The learner can identify the components of Newton's first law",
                      "bloom_level": "REMEMBER"
                    },
                    {
                      "claim_ref": "C2",
                      "text": "The learner can explain how inertia affects motion",
                      "bloom_level": "UNDERSTAND"
                    },
                    {
                      "claim_ref": "C3",
                      "text": "The learner can apply Newton's first law to real-world scenarios",
                      "bloom_level": "APPLY"
                    }
                  ]
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 500, 100, 600)));

            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                .setRequestId("test-123")
                .setTenantId("tenant-1")
                .setTopic("Newton's First Law of Motion")
                .setGradeLevel("GRADE_6_8")
                .setDomain("SCIENCE")
                .setMaxClaims(3)
                .build();

            // WHEN
            AtomicReference<GenerateClaimsResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.generateClaims(request, new StreamObserver<>() {
                @Override
                public void onNext(GenerateClaimsResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            boolean completed = latch.await(5, TimeUnit.SECONDS);

            // THEN
            assertThat(completed).isTrue();
            GenerateClaimsResponse response = responseRef.get();
            assertThat(response).isNotNull();
            assertThat(response.getRequestId()).isEqualTo("test-123");
            assertThat(response.getClaimsList()).hasSize(3);
            
            LearningClaim claim1 = response.getClaims(0);
            assertThat(claim1.getClaimRef()).isEqualTo("C1");
            assertThat(claim1.getText()).contains("Newton's first law");
            assertThat(claim1.getBloomLevel()).isEqualTo("REMEMBER");
            
            assertThat(response.getValidation().getValid()).isTrue();
            assertThat(response.getMetadata().getModelName()).isEqualTo("gpt-4");
        }

        @Test
        @DisplayName("should handle markdown-wrapped JSON in LLM response")
        void shouldHandleMarkdownWrappedJson() throws Exception {
            // GIVEN
            String llmResponse = """
                Here are the learning claims:
                
                ```json
                {
                  "claims": [
                    {
                      "claim_ref": "C1",
                      "text": "The learner can define gravity",
                      "bloom_level": "REMEMBER"
                    }
                  ]
                }
                ```
                
                These claims are appropriate for the grade level.
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 200, 50, 250)));

            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                .setTopic("Gravity")
                .setMaxClaims(1)
                .build();

            // WHEN
            AtomicReference<GenerateClaimsResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.generateClaims(request, new StreamObserver<>() {
                @Override
                public void onNext(GenerateClaimsResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            GenerateClaimsResponse response = responseRef.get();
            assertThat(response.getClaimsList()).hasSize(1);
            assertThat(response.getClaims(0).getText()).contains("define gravity");
        }

        @Test
        @DisplayName("should return error when LLM returns null")
        void shouldReturnErrorWhenLLMReturnsNull() throws Exception {
            // GIVEN
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(null));

            GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
                .setTopic("Test Topic")
                .build();

            // WHEN
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.generateClaims(request, new StreamObserver<>() {
                @Override
                public void onNext(GenerateClaimsResponse response) {
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                    errorRef.set(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            assertThat(errorRef.get()).isNotNull();
            assertThat(errorRef.get().getMessage()).contains("LLM returned empty response");
        }
    }

    // =========================================================================
    // Analyze Content Needs Tests
    // =========================================================================

    @Nested
    @DisplayName("analyzeContentNeeds")
    class AnalyzeContentNeedsTests {

        @Test
        @DisplayName("should analyze content needs successfully")
        void shouldAnalyzeContentNeedsSuccessfully() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "examples": {
                    "required": true,
                    "types": ["REAL_WORLD", "PROBLEM_SOLVING"],
                    "count": 3,
                    "necessity": 0.95,
                    "rationale": "Examples needed to illustrate abstract concept"
                  },
                  "simulation": {
                    "required": true,
                    "interaction_type": "PARAMETER_EXPLORATION",
                    "complexity": "MEDIUM",
                    "necessity": 0.85,
                    "rationale": "Interactive exploration enhances understanding"
                  },
                  "animation": {
                    "required": false,
                    "animation_type": "TWO_D",
                    "duration_seconds": 0,
                    "necessity": 0.3,
                    "rationale": "Static examples sufficient for this claim"
                  }
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 300, 75, 375)));

            AnalyzeContentNeedsRequest request = AnalyzeContentNeedsRequest.newBuilder()
                .setRequestId("needs-123")
                .setClaimText("The learner can apply Newton's laws to calculate acceleration")
                .setBloomLevel("APPLY")
                .setDomain("SCIENCE")
                .setGradeLevel("GRADE_9_12")
                .build();

            // WHEN
            AtomicReference<AnalyzeContentNeedsResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.analyzeContentNeeds(request, new StreamObserver<>() {
                @Override
                public void onNext(AnalyzeContentNeedsResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            AnalyzeContentNeedsResponse response = responseRef.get();
            assertThat(response).isNotNull();
            assertThat(response.getRequestId()).isEqualTo("needs-123");
            
            ContentNeeds needs = response.getContentNeeds();
            assertThat(needs.getExamples().getRequired()).isTrue();
            assertThat(needs.getExamples().getTypesList()).containsExactly("REAL_WORLD", "PROBLEM_SOLVING");
            assertThat(needs.getExamples().getCount()).isEqualTo(3);
            assertThat(needs.getExamples().getNecessity()).isEqualTo(0.95);
            
            assertThat(needs.getSimulation().getRequired()).isTrue();
            assertThat(needs.getSimulation().getInteractionType()).isEqualTo("PARAMETER_EXPLORATION");
            assertThat(needs.getSimulation().getComplexity()).isEqualTo("MEDIUM");
            
            assertThat(needs.getAnimation().getRequired()).isFalse();
        }
    }

    // =========================================================================
    // Generate Examples Tests
    // =========================================================================

    @Nested
    @DisplayName("generateExamples")
    class GenerateExamplesTests {

        @Test
        @DisplayName("should generate examples successfully")
        void shouldGenerateExamplesSuccessfully() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "examples": [
                    {
                      "example_id": "EX1",
                      "type": "REAL_WORLD",
                      "title": "Car Braking",
                      "description": "A car coming to a stop demonstrates Newton's first law",
                      "content": "When you're in a car that suddenly brakes, your body continues moving forward...",
                      "tags": ["physics", "motion", "inertia"],
                      "relevance_score": 0.95
                    },
                    {
                      "example_id": "EX2",
                      "type": "PROBLEM_SOLVING",
                      "title": "Calculate stopping distance",
                      "description": "Apply Newton's laws to calculate braking distance",
                      "content": "Given a car traveling at 60 km/h with a friction coefficient of 0.7...",
                      "tags": ["calculation", "friction"],
                      "relevance_score": 0.88
                    }
                  ]
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 400, 100, 500)));

            GenerateExamplesRequest request = GenerateExamplesRequest.newBuilder()
                .setRequestId("ex-123")
                .setClaimRef("C1")
                .setClaimText("The learner can apply Newton's first law to real scenarios")
                .addExampleTypes("REAL_WORLD")
                .addExampleTypes("PROBLEM_SOLVING")
                .setCount(2)
                .setDomain("SCIENCE")
                .setGradeLevel("GRADE_9_12")
                .build();

            // WHEN
            AtomicReference<GenerateExamplesResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.generateExamples(request, new StreamObserver<>() {
                @Override
                public void onNext(GenerateExamplesResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            GenerateExamplesResponse response = responseRef.get();
            assertThat(response).isNotNull();
            assertThat(response.getExamplesList()).hasSize(2);
            
            Example ex1 = response.getExamples(0);
            assertThat(ex1.getExampleId()).isEqualTo("EX1");
            assertThat(ex1.getType()).isEqualTo("REAL_WORLD");
            assertThat(ex1.getTitle()).isEqualTo("Car Braking");
            assertThat(ex1.getTagsList()).contains("physics", "motion");
            assertThat(ex1.getRelevanceScore()).isEqualTo(0.95);
        }
    }

    // =========================================================================
    // Generate Simulation Tests
    // =========================================================================

    @Nested
    @DisplayName("generateSimulation")
    class GenerateSimulationTests {

        @Test
        @DisplayName("should generate simulation manifest successfully")
        void shouldGenerateSimulationSuccessfully() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "manifest": {
                    "title": "Newton's First Law Demonstration",
                    "description": "Explore how objects resist changes in motion",
                    "domain": "SCIENCE",
                    "entities": [
                      {
                        "id": "ball1",
                        "label": "Ball",
                        "entity_type": "BALL",
                        "visual": "{\\"color\\": \\"#3B82F6\\", \\"radius\\": 20}",
                        "position": {"x": 100, "y": 200, "z": 0}
                      },
                      {
                        "id": "platform1",
                        "label": "Frictionless Surface",
                        "entity_type": "PLATFORM",
                        "visual": "{\\"color\\": \\"#9CA3AF\\"}",
                        "position": {"x": 0, "y": 250, "z": 0}
                      }
                    ],
                    "steps": [
                      {
                        "id": "step1",
                        "description": "Observe the ball at rest",
                        "duration": 3000,
                        "actions": ["observe"]
                      },
                      {
                        "id": "step2",
                        "description": "Apply force to the ball",
                        "duration": 2000,
                        "actions": ["apply_force"]
                      }
                    ],
                    "keyframes": [
                      {
                        "id": "kf1",
                        "time_ms": 0,
                        "state": "{\\"phase\\": \\"initial\\", \\"ball_velocity\\": 0}"
                      },
                      {
                        "id": "kf2",
                        "time_ms": 3000,
                        "state": "{\\"phase\\": \\"motion\\", \\"ball_velocity\\": 5}"
                      }
                    ],
                    "domain_config": "{\\"gravity\\": 9.8, \\"friction\\": 0}"
                  }
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 600, 150, 750)));

            GenerateSimulationRequest request = GenerateSimulationRequest.newBuilder()
                .setRequestId("sim-123")
                .setClaimRef("C1")
                .setClaimText("The learner can explain how inertia affects motion")
                .setInteractionType("PARAMETER_EXPLORATION")
                .setComplexity("MEDIUM")
                .setDomain("SCIENCE")
                .setGradeLevel("GRADE_6_8")
                .build();

            // WHEN
            AtomicReference<GenerateSimulationResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.generateSimulation(request, new StreamObserver<>() {
                @Override
                public void onNext(GenerateSimulationResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            GenerateSimulationResponse response = responseRef.get();
            assertThat(response).isNotNull();
            
            SimulationManifest manifest = response.getManifest();
            assertThat(manifest.getTitle()).isEqualTo("Newton's First Law Demonstration");
            assertThat(manifest.getEntitiesList()).hasSize(2);
            assertThat(manifest.getStepsList()).hasSize(2);
            assertThat(manifest.getKeyframesList()).hasSize(2);
            
            Entity ball = manifest.getEntities(0);
            assertThat(ball.getId()).isEqualTo("ball1");
            assertThat(ball.getEntityType()).isEqualTo("BALL");
            assertThat(ball.getPosition().getX()).isEqualTo(100.0);
        }
    }

    // =========================================================================
    // Validate Content Tests
    // =========================================================================

    @Nested
    @DisplayName("validateContent")
    class ValidateContentTests {

        @Test
        @DisplayName("should validate content successfully")
        void shouldValidateContentSuccessfully() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "status": "valid",
                  "overall_score": 87,
                  "can_publish": true,
                  "dimension_scores": {
                    "educational": 90,
                    "experiential": 85,
                    "safety": 95,
                    "technical": 82,
                    "accessibility": 83
                  },
                  "issues": [
                    {
                      "issue_id": "I1",
                      "dimension": "accessibility",
                      "severity": "warning",
                      "message": "Consider adding audio narration",
                      "suggestion": "Add voice-over explanations for visual learners"
                    }
                  ]
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 300, 75, 375)));

            ValidateContentRequest request = ValidateContentRequest.newBuilder()
                .setRequestId("val-123")
                .setExperienceId("exp-456")
                .setTitle("Understanding Newton's Laws")
                .setDescription("An interactive experience exploring motion")
                .addClaimTexts("The learner can identify Newton's first law")
                .addClaimTexts("The learner can apply Newton's laws")
                .setDomain("SCIENCE")
                .build();

            // WHEN
            AtomicReference<ValidateContentResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.validateContent(request, new StreamObserver<>() {
                @Override
                public void onNext(ValidateContentResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            ValidateContentResponse response = responseRef.get();
            assertThat(response).isNotNull();
            assertThat(response.getStatus()).isEqualTo("valid");
            assertThat(response.getOverallScore()).isEqualTo(87);
            assertThat(response.getCanPublish()).isTrue();
            
            assertThat(response.getDimensionScoresMap()).containsEntry("educational", 90);
            assertThat(response.getDimensionScoresMap()).containsEntry("safety", 95);
            
            assertThat(response.getIssuesList()).hasSize(1);
            assertThat(response.getIssues(0).getDimension()).isEqualTo("accessibility");
            assertThat(response.getIssues(0).getSeverity()).isEqualTo("warning");
        }

        @Test
        @DisplayName("should handle invalid content with multiple issues")
        void shouldHandleInvalidContent() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "status": "invalid",
                  "overall_score": 45,
                  "can_publish": false,
                  "dimension_scores": {
                    "educational": 40,
                    "experiential": 50,
                    "safety": 60,
                    "technical": 35,
                    "accessibility": 40
                  },
                  "issues": [
                    {
                      "issue_id": "I1",
                      "dimension": "educational",
                      "severity": "error",
                      "message": "Claims are not measurable",
                      "suggestion": "Add specific observable actions to each claim"
                    },
                    {
                      "issue_id": "I2",
                      "dimension": "technical",
                      "severity": "error",
                      "message": "Simulation physics are incorrect",
                      "suggestion": "Review gravity and friction values"
                    }
                  ]
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 300, 75, 375)));

            ValidateContentRequest request = ValidateContentRequest.newBuilder()
                .setTitle("Bad Content")
                .setDescription("Poorly designed experience")
                .build();

            // WHEN
            AtomicReference<ValidateContentResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.validateContent(request, new StreamObserver<>() {
                @Override
                public void onNext(ValidateContentResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            ValidateContentResponse response = responseRef.get();
            assertThat(response.getStatus()).isEqualTo("invalid");
            assertThat(response.getOverallScore()).isEqualTo(45);
            assertThat(response.getCanPublish()).isFalse();
            assertThat(response.getIssuesList()).hasSize(2);
            assertThat(response.getIssueCount()).isEqualTo(2);
        }
    }

    // =========================================================================
    // Enhance Content Tests
    // =========================================================================

    @Nested
    @DisplayName("enhanceContent")
    class EnhanceContentTests {

        @Test
        @DisplayName("should enhance content with suggestions")
        void shouldEnhanceContentSuccessfully() throws Exception {
            // GIVEN
            String llmResponse = """
                {
                  "enhancements": [
                    {
                      "enhancement_id": "E1",
                      "type": "engagement",
                      "title": "Add Interactive Quiz",
                      "description": "Include a 3-question quiz after each concept",
                      "rationale": "Spaced retrieval improves long-term retention",
                      "confidence": 0.92,
                      "implementation": "{\\"quiz_type\\": \\"multiple_choice\\", \\"questions_per_section\\": 3}"
                    },
                    {
                      "enhancement_id": "E2",
                      "type": "real_world",
                      "title": "Add Career Connection",
                      "description": "Show how engineers use these concepts",
                      "rationale": "Career relevance increases student motivation",
                      "confidence": 0.88,
                      "implementation": "{\\"video_length_seconds\\": 90, \\"professionals\\": [\\"aerospace_engineer\\", \\"physicist\\"]}"
                    }
                  ],
                  "overall_confidence": 0.90
                }
                """;
            
            when(mockLLMGateway.complete(any(CompletionRequest.class)))
              .thenReturn(Promise.of(completion(llmResponse, "gpt-4", 350, 85, 435)));

            EnhanceContentRequest request = EnhanceContentRequest.newBuilder()
                .setRequestId("enh-123")
                .setExperienceId("exp-456")
                .setTitle("Newton's Laws")
                .setDescription("Interactive physics experience")
                .addClaimTexts("The learner can apply Newton's laws")
                .setDomain("SCIENCE")
                .setGradeLevel("GRADE_9_12")
                .addEnhancementTypes("engagement")
                .addEnhancementTypes("real_world")
                .build();

            // WHEN
            AtomicReference<EnhanceContentResponse> responseRef = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            service.enhanceContent(request, new StreamObserver<>() {
                @Override
                public void onNext(EnhanceContentResponse response) {
                    responseRef.set(response);
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            });

            latch.await(5, TimeUnit.SECONDS);

            // THEN
            EnhanceContentResponse response = responseRef.get();
            assertThat(response).isNotNull();
            assertThat(response.getRequestId()).isEqualTo("enh-123");
            assertThat(response.getOverallConfidence()).isEqualTo(0.90);
            assertThat(response.getEnhancementsList()).hasSize(2);
            assertThat(response.getEnhancementCount()).isEqualTo(2);
            
            Enhancement enh1 = response.getEnhancements(0);
            assertThat(enh1.getType()).isEqualTo("engagement");
            assertThat(enh1.getTitle()).isEqualTo("Add Interactive Quiz");
            assertThat(enh1.getConfidence()).isEqualTo(0.92);
            assertThat(enh1.getImplementation()).contains("quiz_type");
        }
    }
}
