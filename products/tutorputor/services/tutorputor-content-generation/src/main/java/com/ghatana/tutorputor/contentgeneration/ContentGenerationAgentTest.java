package com.ghatana.tutorputor.agents;

import com.ghatana.patternlearning.llm.LlmProvider;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.tutorputor.agents.prompts.PromptTemplateEngine;
import com.ghatana.tutorputor.agents.validation.ContentValidator;
import com.ghatana.tutorputor.contracts.v1.*;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ContentGenerationAgent.
 * 
 * Tests agent functionality with mocked LLM provider to ensure:
 * - Proper prompt construction
 * - Response parsing
 * - Validation logic
 * - Error handling
 * - Retry mechanisms
 */
class ContentGenerationAgentTest extends EventloopTestBase {
    
    private LlmProvider mockLlmProvider;
    private PromptTemplateEngine promptEngine;
    private ContentValidator validator;
    private SimpleMeterRegistry meterRegistry;
    private ContentGenerationAgent agent;
    
    @BeforeEach
    void setUp() {
        mockLlmProvider = mock(LlmProvider.class);
        promptEngine = new PromptTemplateEngine();
        validator = new ContentValidator();
        meterRegistry = new SimpleMeterRegistry();
        
        // Mock LLM provider to return model name
        when(mockLlmProvider.getModelName()).thenReturn("gpt-4o-mini");
        
        agent = new ContentGenerationAgent(
            mockLlmProvider,
            promptEngine,
            validator,
            meterRegistry,
            0.3,  // temperature
            4000, // maxTokens
            3     // maxRetries
        );
    }
    
    @Test
    void testGenerateClaims_Success() throws Exception {
        // Arrange
        String mockLlmResponse = """
            {
              "claims": [
                {
                  "claim_ref": "C1",
                  "text": "The learner can explain Newton's First Law of Motion",
                  "bloom_level": "understand",
                  "content_needs": {
                    "examples": {
                      "required": true,
                      "types": ["real_world", "analogy"],
                      "count": 2,
                      "necessity": 0.9,
                      "rationale": "Concrete examples help understand abstract physics concepts"
                    },
                    "simulation": {
                      "required": true,
                      "interaction_type": "parameter_exploration",
                      "complexity": "medium",
                      "necessity": 0.8,
                      "rationale": "Interactive simulation demonstrates inertia"
                    },
                    "animation": {
                      "required": false,
                      "type": "2d",
                      "duration_seconds": 30,
                      "necessity": 0.3,
                      "rationale": "Animation is optional for this concept"
                    }
                  }
                },
                {
                  "claim_ref": "C2",
                  "text": "The learner can apply Newton's First Law to real-world scenarios",
                  "bloom_level": "apply",
                  "content_needs": {
                    "examples": {
                      "required": true,
                      "types": ["problem_solving"],
                      "count": 3,
                      "necessity": 1.0,
                      "rationale": "Application requires worked examples"
                    },
                    "simulation": {
                      "required": true,
                      "interaction_type": "prediction",
                      "complexity": "medium",
                      "necessity": 0.9,
                      "rationale": "Learners need to predict outcomes"
                    },
                    "animation": {
                      "required": false,
                      "type": "2d",
                      "duration_seconds": 20,
                      "necessity": 0.2,
                      "rationale": "Not essential for application"
                    }
                  }
                }
              ]
            }
            """;
        
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.of(mockLlmResponse));
        
        GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
            .setRequestId("test-request-1")
            .setTenantId("tenant-1")
            .setTopic("Newton's First Law of Motion")
            .setGradeLevel(GradeLevel.GRADE_9_12)
            .setDomain(Domain.SCIENCE)
            .setMaxClaims(5)
            .build();
        
        // Act
        GenerateClaimsResponse response = runPromise(() -> agent.generateClaims(request));
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRequestId()).isEqualTo("test-request-1");
        assertThat(response.getClaimsList()).hasSize(2);
        
        // Verify first claim
        Claim claim1 = response.getClaims(0);
        assertThat(claim1.getClaimRef()).isEqualTo("C1");
        assertThat(claim1.getText()).contains("Newton's First Law");
        assertThat(claim1.getBloomLevel()).isEqualTo(BloomLevel.UNDERSTAND);
        
        // Verify content needs
        ContentNeeds contentNeeds1 = claim1.getContentNeeds();
        assertThat(contentNeeds1.getExamples().getRequired()).isTrue();
        assertThat(contentNeeds1.getExamples().getCount()).isEqualTo(2);
        assertThat(contentNeeds1.getSimulation().getRequired()).isTrue();
        assertThat(contentNeeds1.getAnimation().getRequired()).isFalse();
        
        // Verify validation
        assertThat(response.getValidation().getValid()).isTrue();
        assertThat(response.getValidation().getConfidenceScore()).isGreaterThan(0.5f);
        
        // Verify metadata
        assertThat(response.getMetadata().getModelName()).isEqualTo("gpt-4o-mini");
        assertThat(response.getMetadata().getTemperature()).isEqualTo(0.3f);
        
        // Verify LLM was called
        verify(mockLlmProvider, times(1)).generate(anyString(), anyMap());
    }
    
    @Test
    void testGenerateClaims_RetryOnFailure() throws Exception {
        // Arrange
        String mockLlmResponse = """
            {
              "claims": [
                {
                  "claim_ref": "C1",
                  "text": "Test claim",
                  "bloom_level": "understand",
                  "content_needs": {
                    "examples": {"required": false, "types": [], "count": 0, "necessity": 0.0, "rationale": ""},
                    "simulation": {"required": false, "interaction_type": "parameter_exploration", "complexity": "low", "necessity": 0.0, "rationale": ""},
                    "animation": {"required": false, "type": "2d", "duration_seconds": 0, "necessity": 0.0, "rationale": ""}
                  }
                }
              ]
            }
            """;
        
        // Fail twice, then succeed
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.ofException(new RuntimeException("LLM timeout")))
            .thenReturn(Promise.ofException(new RuntimeException("LLM timeout")))
            .thenReturn(Promise.of(mockLlmResponse));
        
        GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
            .setRequestId("test-request-2")
            .setTenantId("tenant-1")
            .setTopic("Test Topic")
            .setGradeLevel(GradeLevel.GRADE_6_8)
            .setDomain(Domain.MATH)
            .setMaxClaims(1)
            .build();
        
        // Act
        GenerateClaimsResponse response = runPromise(() -> agent.generateClaims(request));
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getClaimsList()).hasSize(1);
        
        // Verify LLM was called 3 times (2 failures + 1 success)
        verify(mockLlmProvider, times(3)).generate(anyString(), anyMap());
    }
    
    @Test
    void testGenerateExamples_Success() throws Exception {
        // Arrange
        String mockLlmResponse = """
            {
              "examples": [
                {
                  "type": "real_world",
                  "title": "Car Braking Example",
                  "description": "When a car brakes suddenly, passengers continue moving forward due to inertia",
                  "problem_statement": "Why do passengers lurch forward when a car brakes?",
                  "solution": "Newton's First Law states that objects in motion stay in motion unless acted upon by an external force. The car's brakes apply force to the car, but not directly to the passengers. Therefore, passengers continue moving forward at the original speed until the seatbelt applies force to stop them.",
                  "key_learning_points": [
                    "Objects resist changes in motion (inertia)",
                    "External forces are required to change motion",
                    "Seatbelts provide the force to stop passengers"
                  ],
                  "real_world_connection": "This is why seatbelts are essential for safety"
                },
                {
                  "type": "analogy",
                  "title": "Hockey Puck on Ice",
                  "description": "A hockey puck sliding on ice demonstrates low friction and inertia",
                  "solution": "On smooth ice with minimal friction, a hockey puck continues sliding in a straight line at constant speed until friction or another force (like a stick) acts on it.",
                  "key_learning_points": [
                    "Low friction allows motion to continue",
                    "Straight-line motion at constant speed",
                    "External force needed to change direction or speed"
                  ],
                  "real_world_connection": "Ice skating and hockey rely on low friction"
                }
              ]
            }
            """;
        
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.of(mockLlmResponse));
        
        GenerateExamplesRequest request = GenerateExamplesRequest.newBuilder()
            .setRequestId("test-request-3")
            .setTenantId("tenant-1")
            .setClaimText("The learner can explain Newton's First Law")
            .setClaimRef("C1")
            .setGradeLevel(GradeLevel.GRADE_9_12)
            .setDomain(Domain.SCIENCE)
            .addTypes(ExampleType.REAL_WORLD)
            .addTypes(ExampleType.ANALOGY)
            .setCount(2)
            .build();
        
        // Act
        GenerateExamplesResponse response = runPromise(() -> agent.generateExamples(request));
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getExamplesList()).hasSize(2);
        
        // Verify first example
        Example example1 = response.getExamples(0);
        assertThat(example1.getType()).isEqualTo(ExampleType.REAL_WORLD);
        assertThat(example1.getTitle()).isEqualTo("Car Braking Example");
        assertThat(example1.getDescription()).contains("passengers");
        assertThat(example1.getSolutionContent()).contains("Newton's First Law");
        assertThat(example1.getKeyLearningPointsList()).hasSize(3);
        assertThat(example1.getRealWorldConnection()).contains("seatbelts");
        
        // Verify validation
        assertThat(response.getValidation().getValid()).isTrue();
    }
    
    @Test
    void testAnalyzeContentNeeds_Success() throws Exception {
        // Arrange
        String mockLlmResponse = """
            {
              "content_needs": {
                "examples": {
                  "required": true,
                  "types": ["real_world", "problem_solving"],
                  "count": 2,
                  "necessity": 0.9,
                  "rationale": "Abstract physics concepts require concrete examples"
                },
                "simulation": {
                  "required": true,
                  "interaction_type": "parameter_exploration",
                  "complexity": "medium",
                  "necessity": 0.85,
                  "rationale": "Hands-on exploration helps learners understand forces"
                },
                "animation": {
                  "required": false,
                  "type": "2d",
                  "duration_seconds": 30,
                  "necessity": 0.4,
                  "rationale": "Animation can supplement but is not essential"
                }
              },
              "rationale": "This claim involves understanding abstract concepts (forces, motion) which benefit from multiple representations: concrete examples for grounding, interactive simulation for exploration, and optional animation for visualization."
            }
            """;
        
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.of(mockLlmResponse));
        
        AnalyzeContentNeedsRequest request = AnalyzeContentNeedsRequest.newBuilder()
            .setRequestId("test-request-4")
            .setClaimText("The learner can analyze the relationship between force and acceleration")
            .setBloomLevel(BloomLevel.ANALYZE)
            .setGradeLevel(GradeLevel.GRADE_9_12)
            .setDomain(Domain.SCIENCE)
            .build();
        
        // Act
        AnalyzeContentNeedsResponse response = runPromise(() -> agent.analyzeContentNeeds(request));
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getContentNeeds()).isNotNull();
        
        // Verify example needs
        ExampleNeeds exampleNeeds = response.getContentNeeds().getExamples();
        assertThat(exampleNeeds.getRequired()).isTrue();
        assertThat(exampleNeeds.getCount()).isEqualTo(2);
        assertThat(exampleNeeds.getTypesList()).contains(ExampleType.REAL_WORLD, ExampleType.PROBLEM_SOLVING);
        assertThat(exampleNeeds.getNecessity()).isGreaterThan(0.8f);
        
        // Verify simulation needs
        SimulationNeeds simNeeds = response.getContentNeeds().getSimulation();
        assertThat(simNeeds.getRequired()).isTrue();
        assertThat(simNeeds.getInteractionType()).isEqualTo(InteractionType.PARAMETER_EXPLORATION);
        assertThat(simNeeds.getComplexity()).isEqualTo(Complexity.MEDIUM);
        
        // Verify rationale
        assertThat(response.getRationale()).contains("abstract concepts");
    }
    
    @Test
    void testGenerateSimulation_Success() throws Exception {
        // Arrange
        String mockLlmResponse = """
            {
              "simulation": {
                "name": "Force and Acceleration Lab",
                "description": "Explore the relationship between force, mass, and acceleration",
                "entities": [
                  {
                    "id": "cart1",
                    "type": "CART",
                    "properties": {
                      "mass": "1.0",
                      "friction": "0.1"
                    }
                  },
                  {
                    "id": "force1",
                    "type": "FORCE_ARROW",
                    "properties": {
                      "magnitude": "10.0",
                      "direction": "right"
                    }
                  }
                ],
                "goals": [
                  {
                    "description": "Observe how changing force affects acceleration"
                  },
                  {
                    "description": "Verify F = ma relationship"
                  }
                ]
              }
            }
            """;
        
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.of(mockLlmResponse));
        
        GenerateSimulationRequest request = GenerateSimulationRequest.newBuilder()
            .setRequestId("test-request-5")
            .setTenantId("tenant-1")
            .setClaimText("The learner can analyze force and acceleration")
            .setClaimRef("C1")
            .setGradeLevel(GradeLevel.GRADE_9_12)
            .setDomain(Domain.SCIENCE)
            .setInteractionType(InteractionType.PARAMETER_EXPLORATION)
            .setComplexity(Complexity.MEDIUM)
            .build();
        
        // Act
        GenerateSimulationResponse response = runPromise(() -> agent.generateSimulation(request));
        
        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getManifest()).isNotNull();
        
        SimulationManifest manifest = response.getManifest();
        assertThat(manifest.getName()).isEqualTo("Force and Acceleration Lab");
        assertThat(manifest.getDescription()).contains("force, mass, and acceleration");
        assertThat(manifest.getEntitiesList()).hasSize(2);
        assertThat(manifest.getGoalsList()).hasSize(2);
        
        // Verify entities
        Entity cart = manifest.getEntities(0);
        assertThat(cart.getEntityId()).isEqualTo("cart1");
        assertThat(cart.getType()).isEqualTo("CART");
        assertThat(cart.getPropertiesMap()).containsKey("mass");
        
        // Verify validation
        assertThat(response.getValidation().getValid()).isTrue();
    }
    
    @Test
    void testAgentLifecycle() {
        // Test agent lifecycle methods
        assertThat(agent.getId()).isNotNull();
        assertThat(agent.getVersion()).isEqualTo("1.0.0");
        assertThat(agent.getSupportedEventTypes()).contains("content.generation.requested");
        assertThat(agent.getOutputEventTypes()).contains("content.generation.completed");
        assertThat(agent.isHealthy()).isTrue();
    }
    
    @Test
    void testMetricsCollection() throws Exception {
        // Arrange
        String mockLlmResponse = """
            {
              "claims": [
                {
                  "claim_ref": "C1",
                  "text": "Test",
                  "bloom_level": "understand",
                  "content_needs": {
                    "examples": {"required": false, "types": [], "count": 0, "necessity": 0.0, "rationale": ""},
                    "simulation": {"required": false, "interaction_type": "parameter_exploration", "complexity": "low", "necessity": 0.0, "rationale": ""},
                    "animation": {"required": false, "type": "2d", "duration_seconds": 0, "necessity": 0.0, "rationale": ""}
                  }
                }
              ]
            }
            """;
        
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.of(mockLlmResponse));
        
        GenerateClaimsRequest request = GenerateClaimsRequest.newBuilder()
            .setRequestId("test-metrics")
            .setTenantId("tenant-1")
            .setTopic("Test")
            .setGradeLevel(GradeLevel.GRADE_6_8)
            .setDomain(Domain.MATH)
            .setMaxClaims(1)
            .build();
        
        // Act
        runPromise(() -> agent.generateClaims(request));

        // Assert - verify timer was recorded (timer name matches the one in the agent)
        assertThat(meterRegistry.get("tutorputor.agent.generate_claims").timer().count())
            .isEqualTo(1);
    }

    @Test
    void testGenerateAnimation_Success() throws Exception {
        // Arrange
        String mockLlmResponse = """
            Animation Introduction to Newton's First Law
            This animation shows how an object in motion stays in motion
            Frame 1: Object at rest
            Frame 2: Force applied, object begins moving
            Frame 3: Object continues at constant velocity
            """;

        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.of(mockLlmResponse));

        GenerateAnimationRequest request = GenerateAnimationRequest.newBuilder()
            .setRequestId("test-animation-1")
            .setTenantId("tenant-1")
            .setClaimText("The learner can explain Newton's First Law of Motion")
            .setClaimRef("C1")
            .setAnimationType(AnimationType.TWO_D)
            .setDurationSeconds(30)
            .build();

        // Act
        GenerateAnimationResponse response = runPromise(() -> agent.generateAnimation(request));

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getRequestId()).isEqualTo("test-animation-1");
        assertThat(response.hasAnimation()).isTrue();

        AnimationSpec anim = response.getAnimation();
        assertThat(anim.getAnimationId()).isEqualTo("test-animation-1-ANIM");
        assertThat(anim.getType()).isEqualTo(AnimationType.TWO_D);
        assertThat(anim.getDurationSeconds()).isEqualTo(30);
        assertThat(anim.getKeyframesCount()).isGreaterThanOrEqualTo(2);

        // Verify keyframe structure
        Keyframe firstFrame = anim.getKeyframes(0);
        assertThat(firstFrame.getTimeMs()).isEqualTo(0);
        assertThat(firstFrame.getPropertiesMap()).containsKey("opacity");

        // Validate that the last keyframe is at the full duration
        Keyframe lastFrame = anim.getKeyframes(anim.getKeyframesCount() - 1);
        assertThat(lastFrame.getTimeMs()).isEqualTo(30000);

        // Verify config
        assertThat(anim.getConfigMap()).containsKey("width");
        assertThat(anim.getConfigMap()).containsKey("fps");

        // Verify validation passed
        assertThat(response.getValidation().getValid()).isTrue();
        assertThat(response.getValidation().getConfidenceScore()).isGreaterThan(0.5f);

        // Verify timer was recorded
        assertThat(meterRegistry.get("tutorputor.agent.generate_animation").timer().count())
            .isEqualTo(1);
    }

    @Test
    void testGenerateAnimation_ExhaustsRetriesOnLlmFailure() {
        // Arrange - always fail
        when(mockLlmProvider.generate(anyString(), anyMap()))
            .thenReturn(Promise.ofException(new RuntimeException("LLM unavailable")));

        GenerateAnimationRequest request = GenerateAnimationRequest.newBuilder()
            .setRequestId("test-animation-retry")
            .setTenantId("tenant-1")
            .setClaimText("Test claim")
            .setClaimRef("C1")
            .setAnimationType(AnimationType.TWO_D)
            .setDurationSeconds(20)
            .build();

        // Act + Assert
        assertThatThrownBy(() -> runPromise(() -> agent.generateAnimation(request)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("LLM unavailable");

        // All 3 retries should have been attempted
        verify(mockLlmProvider, times(3)).generate(anyString(), anyMap());

        // Failure counter should be incremented
        assertThat(meterRegistry.get("tutorputor.agent.failures").counter().count())
            .isEqualTo(1);
    }
}
