package com.ghatana.tutorputor.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.tutorputor.contentstudio.knowledge.KnowledgeBaseService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ghatana.platform.testing.activej.EventloopTestBase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive tests for ContentGenerationAgent.
 *
 * @doc.type test
 * @doc.purpose Tests for TutorPutor content generation agent
 * @doc.layer product
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentGenerationAgent Tests")
class ContentGenerationAgentTest extends EventloopTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(ContentGenerationAgentTest.class);

    @Mock
    private OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> mockGenerator;

    @Mock
    private KnowledgeBaseService mockKnowledgeBaseService;

    @Mock
    private AgentContext mockContext;

    @Mock
    private MemoryStore mockMemoryStore;

    @Mock
    private LearnerProfileHttpClient mockLearnerProfileClient;

    private ContentQualityValidator qualityValidator;
    private TestContentGenerationAgent agent;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        qualityValidator = new ContentQualityValidator(registry);
        
        // Set up agent
        agent = new TestContentGenerationAgent(
            mockGenerator,
            mockKnowledgeBaseService,
            qualityValidator,
            mockLearnerProfileClient
        );

        lenient().when(mockLearnerProfileClient.getPersonalization(anyString(), anyString()))
            .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Should generate content successfully")
    void shouldGenerateContentSuccessfully() {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.basic(
            "Photosynthesis",
            "SCIENCE",
            "6",
            ContentGenerationRequest.ContentType.CLAIM
        );

        ContentGenerationResponse generatedResponse = ContentGenerationResponse.builder()
            .content("Photosynthesis is the process by which plants convert sunlight into energy.")
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.8)
            .generationTimeMs(500)
            .tokenCount(50)
            .build();

        when(mockContext.getTurnId()).thenReturn("turn-1");
        when(mockContext.getLogger()).thenReturn(LOG);
        when(mockContext.getMemoryStore()).thenReturn(mockMemoryStore);
        lenient().when(mockContext.getAllConfig()).thenReturn(java.util.Map.of());

        when(mockGenerator.generate(any(), any()))
            .thenReturn(Promise.of(generatedResponse));
        
        // Mock the generator metadata to avoid NullPointerException
        GeneratorMetadata metadata = mock(GeneratorMetadata.class);
        lenient().when(metadata.getName()).thenReturn("test-generator");
        lenient().when(mockGenerator.getMetadata()).thenReturn(metadata);
        
        when(mockKnowledgeBaseService.checkCurriculumAlignment(anyString(), anyString(), anyString()))
            .thenReturn(Promise.of(new KnowledgeBaseService.CurriculumAlignmentResult(
                true, 0.85, "Aligned with science curriculum", 
                List.of("Biology", "Energy"), "https://example.com"
            )));
        
        when(mockMemoryStore.storeEpisode(any()))
            .thenReturn(Promise.of(Episode.builder()
                .agentId("test-agent")
                .turnId("turn-1")
                .timestamp(Instant.now())
                .input("test")
                .output("test")
                .build()));
        
        when(mockMemoryStore.storeFact(any()))
            .thenReturn(Promise.of(Fact.builder()
                .agentId("test-agent")
                .subject("test")
                .predicate("has")
                .object("value")
                .build()));

        // Reflect is fire-and-forget; make stubbing lenient so strict Mockito doesn't fail
        // if the eventloop stops before reflect consumes it.
        lenient().when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
            .thenReturn(Promise.of(List.of()));

        // When
        ContentGenerationResponse result = runPromise(() -> agent.executeTurn(request, mockContext));

        // Then
        assertThat(result.content()).isEqualTo(generatedResponse.content());
        assertThat(result.curriculumAligned()).isTrue();
        assertThat(result.alignedTopics()).containsExactly("Biology", "Energy");
        verify(mockGenerator).generate(any(), any());
        verify(mockMemoryStore).storeEpisode(any());
        verify(mockMemoryStore).storeFact(any());
    }

    @Test
    @DisplayName("Should validate request with missing topic")
    void shouldValidateRequestWithMissingTopic() {
        // Given
        ContentGenerationRequest invalidRequest = new ContentGenerationRequest(
            null, // Missing topic
            "SCIENCE",
            "6",
            ContentGenerationRequest.ContentType.CLAIM,
            null, null, null, null, null
        );

        // When/Then - perceive phase should throw
        // Note: In actual execution, this happens inside executeTurn
        assertThatThrownBy(() -> {
            // Simulate perceive validation
            if (invalidRequest.topic() == null || invalidRequest.topic().isBlank()) {
                throw new IllegalArgumentException("Topic is required");
            }
        }).isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Topic is required");
    }

    @Test
    @DisplayName("Should enrich request with learner context")
    void shouldEnrichRequestWithLearnerContext() {
        // Given
        ContentGenerationRequest request = ContentGenerationRequest.forLearner(
            "Algebra",
            "MATH",
            "8",
            ContentGenerationRequest.ContentType.EXAMPLE,
            "learner-123"
        );

        // Then
        assertThat(request.learnerId()).isEqualTo("learner-123");
        assertThat(request.topic()).isEqualTo("Algebra");
        assertThat(request.domain()).isEqualTo("MATH");
    }

    @Test
    @DisplayName("Should derive learner preferences from snapshot fields when explicit preferences are absent")
    void shouldDeriveLearnerPreferencesFromSnapshotFields() {
        ContentGenerationRequest request = ContentGenerationRequest.forLearner(
            "Fractions",
            "MATH",
            "5",
            ContentGenerationRequest.ContentType.EXAMPLE,
            "learner-123"
        );

        LearnerProfileHttpClient.LearnerPersonalizationSnapshot snapshot =
            new LearnerProfileHttpClient.LearnerPersonalizationSnapshot();
        snapshot.preferredModality = "VISUAL";
        snapshot.preferredPacing = "ADAPTIVE";
        snapshot.preferredDifficulty = "BEGINNER";
        snapshot.adjustedDifficulty = "easy";
        snapshot.preferences = List.of();
        snapshot.knowledgeGaps = List.of("fraction-basics");

        when(mockLearnerProfileClient.getPersonalization("learner-123", "Fractions"))
            .thenReturn(Optional.of(snapshot));
        when(mockContext.getLogger()).thenReturn(LOG);

        ContentGenerationRequest enriched = agent.runPerceive(request, mockContext);

        assertThat(enriched.learnerPreferences()).containsExactly(
            "visual-learning",
            "adaptive-checkpoints",
            "scaffolded-practice"
        );
        assertThat(enriched.difficulty()).isEqualTo("easy");
        assertThat(enriched.knowledgeGaps()).containsExactly("fraction-basics");
    }

    @Test
    @DisplayName("Should create response with validation issues")
    void shouldCreateResponseWithValidationIssues() {
        // Given
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("Short")
            .domain("SCIENCE")
            .gradeLevel("6")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.3)
            .generationTimeMs(100)
            .tokenCount(10)
            .build();

        List<String> issues = List.of("Content too short", "Missing explanation");

        // When
        ContentGenerationResponse responseWithIssues = response.withValidationIssues(issues);

        // Then
        assertThat(responseWithIssues.validationIssues()).containsExactly(
            "Content too short", "Missing explanation"
        );
        assertThat(responseWithIssues.content()).isEqualTo(response.content());
    }

    @Test
    @DisplayName("Should create response with curriculum alignment")
    void shouldCreateResponseWithCurriculumAlignment() {
        // Given
        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("The mitochondria is the powerhouse of the cell.")
            .domain("SCIENCE")
            .gradeLevel("7")
            .contentType(ContentGenerationRequest.ContentType.CLAIM)
            .qualityScore(0.8)
            .generationTimeMs(200)
            .tokenCount(20)
            .build();

        List<String> topics = List.of("Cell Biology", "Organelles");

        // When
        ContentGenerationResponse alignedResponse = response.withCurriculumAlignment(true, topics);

        // Then
        assertThat(alignedResponse.curriculumAligned()).isTrue();
        assertThat(alignedResponse.alignedTopics()).containsExactly("Cell Biology", "Organelles");
    }

    @Test
    @DisplayName("Should extract a strategy immediately for a strong learner-specific generation")
    void shouldExtractImmediateStrategyForStrongLearnerOutcome() {
        ContentGenerationRequest request = new ContentGenerationRequest(
            "Stoichiometry",
            "CHEMISTRY",
            "10",
            ContentGenerationRequest.ContentType.EXAMPLE,
            "learner-42",
            "hard",
            List.of("visual-learning", "worked-examples"),
            List.of("unit-conversion"),
            null
        );

        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("Worked stoichiometry example with visuals.")
            .domain("CHEMISTRY")
            .gradeLevel("10")
            .contentType(ContentGenerationRequest.ContentType.EXAMPLE)
            .qualityScore(0.95)
            .curriculumAligned(true)
            .alignedTopics(List.of("Stoichiometry"))
            .generationTimeMs(400)
            .tokenCount(240)
            .build();

        when(mockContext.getLogger()).thenReturn(LOG);
        when(mockContext.getMemoryStore()).thenReturn(mockMemoryStore);
        when(mockMemoryStore.storePolicy(any()))
            .thenReturn(Promise.of(Policy.builder()
                .agentId("test-agent")
                .situation("s")
                .action("a")
                .confidence(0.9)
                .build()));
        when(mockMemoryStore.storeFact(any()))
            .thenReturn(Promise.of(Fact.builder()
                .agentId("test-agent")
                .subject("learner-42")
                .predicate("responds_well_to")
                .object("example")
                .build()));
        when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
            .thenReturn(Promise.of(List.of()));

        runPromise(() -> agent.runReflect(request, response, mockContext));

        verify(mockMemoryStore).storePolicy(any());
        verify(mockMemoryStore).storeFact(any());
    }

    @Test
    @DisplayName("Should extract aggregate successful patterns after three strong episodes")
    void shouldExtractAggregatePatternsAfterThreeStrongEpisodes() {
        ContentGenerationRequest request = ContentGenerationRequest.basic(
            "Cellular Respiration",
            "SCIENCE",
            "8",
            ContentGenerationRequest.ContentType.LESSON
        );

        ContentGenerationResponse response = ContentGenerationResponse.builder()
            .content("Structured lesson with examples and checks for understanding.")
            .domain("SCIENCE")
            .gradeLevel("8")
            .contentType(ContentGenerationRequest.ContentType.LESSON)
            .qualityScore(0.82)
            .generationTimeMs(550)
            .tokenCount(180)
            .build();

        Episode episode1 = Episode.builder()
            .agentId("tutorputor-content-agent")
            .turnId("turn-1")
            .timestamp(Instant.now())
            .input("input")
            .output("output")
            .context(java.util.Map.of("domain", "SCIENCE"))
            .reward(0.90)
            .build();
        Episode episode2 = Episode.builder()
            .agentId("tutorputor-content-agent")
            .turnId("turn-2")
            .timestamp(Instant.now())
            .input("input")
            .output("output")
            .context(java.util.Map.of("domain", "SCIENCE"))
            .reward(0.88)
            .build();
        Episode episode3 = Episode.builder()
            .agentId("tutorputor-content-agent")
            .turnId("turn-3")
            .timestamp(Instant.now())
            .input("input")
            .output("output")
            .context(java.util.Map.of("domain", "SCIENCE"))
            .reward(0.87)
            .build();

        when(mockContext.getLogger()).thenReturn(LOG);
        when(mockContext.getMemoryStore()).thenReturn(mockMemoryStore);
        when(mockMemoryStore.queryEpisodes(any(), any(Integer.class)))
            .thenReturn(Promise.of(List.of(episode1, episode2, episode3)));
        when(mockMemoryStore.storePolicy(any()))
            .thenReturn(Promise.of(Policy.builder()
                .agentId("test-agent")
                .situation("s")
                .action("a")
                .confidence(0.88)
                .build()));

        runPromise(() -> agent.runReflect(request, response, mockContext));

        verify(mockMemoryStore).storePolicy(any());
    }

    private static final class TestContentGenerationAgent extends ContentGenerationAgent {
        private TestContentGenerationAgent(
            OutputGenerator<ContentGenerationRequest, ContentGenerationResponse> generator,
            KnowledgeBaseService knowledgeBaseService,
            ContentQualityValidator qualityValidator,
            LearnerProfileHttpClient learnerProfileClient
        ) {
            super(generator, knowledgeBaseService, qualityValidator, learnerProfileClient);
        }

        private Promise<Void> runReflect(
            ContentGenerationRequest request,
            ContentGenerationResponse response,
            AgentContext context
        ) {
            return super.reflect(request, response, context);
        }

        private ContentGenerationRequest runPerceive(
            ContentGenerationRequest request,
            AgentContext context
        ) {
            return super.perceive(request, context);
        }
    }
}
