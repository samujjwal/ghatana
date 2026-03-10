package com.ghatana.yappc.ai.requirements.ai.service;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.ai.requirements.ai.RequirementAIService;
import com.ghatana.yappc.ai.requirements.ai.RequirementGenerationRequest;
import com.ghatana.yappc.ai.requirements.ai.RequirementGenerationResponse;
import com.ghatana.yappc.ai.requirements.ai.RequirementQualityResult;
import com.ghatana.yappc.ai.requirements.ai.RequirementType;
import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import com.ghatana.yappc.ai.requirements.ai.persona.PersonaRepository;
import com.ghatana.yappc.ai.requirements.ai.prompts.PromptTemplate;
import com.ghatana.yappc.ai.requirements.ai.prompts.PromptTemplateManager;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RequirementAIService.
 */
@DisplayName("RequirementAI Service Tests")
/**
 * @doc.type class
 * @doc.purpose Handles requirement ai service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class RequirementAIServiceTest extends EventloopTestBase {

    private RequirementAIService service;

    @Mock private CompletionService completionService;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private PersonaRepository personaRepository;
    @Mock private PromptTemplateManager promptTemplateManager;
    @Mock private MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new RequirementAIService(
                completionService,
                embeddingService,
                vectorStore,
                personaRepository,
                promptTemplateManager,
                metricsCollector
        );

        // Default mocks
        when(personaRepository.findById(anyString())).thenReturn(Promise.of(Optional.of(Persona.DEFAULT)));
        when(promptTemplateManager.getTemplate(anyString())).thenReturn(new PromptTemplate("template"));
        
        // Mock LLM response
        CompletionResult mockResult = CompletionResult.builder()
                .text("Mock response")
                .tokensUsed(10)
                .modelUsed("test-model")
                .build();
        when(completionService.complete(any(CompletionRequest.class))).thenReturn(Promise.of(mockResult));

        // Mock Embedding response
        when(embeddingService.createEmbedding(anyString())).thenReturn(Promise.of(new EmbeddingResult("test", new float[1536], "test-model")));

        // Mock Vector Search
        when(vectorStore.search(any(), anyInt(), anyDouble(), any())).thenReturn(Promise.of(List.of()));
    }

    @Test
    @DisplayName("Should generate requirements (mocked)")
    void shouldGenerateRequirements() {
        // GIVEN: Valid request
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .featureDescription("User authentication system")
                .context("Mobile banking application")
                .build();

        // WHEN: Generate requirements
        RequirementGenerationResponse response = runPromise(()
                -> service.generateRequirements(request));

        // THEN: Response is not null
        assertThat(response).isNotNull();
        // Note: requirements list is empty because parsing is not implemented in service yet
    }

    @Test
    @DisplayName("Should search similar requirements (mocked)")
    void shouldSearchSimilarRequirements() {
        // GIVEN: Search query
        String query = "user authentication";

        // WHEN: Search
        List<VectorSearchResult> results = runPromise(()
                -> service.searchSimilarRequirements(query, 5));

        // THEN: Results returned (empty list from mock)
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("Should improve requirement (mocked)")
    void shouldImproveRequirement() {
        // GIVEN: Simple requirement
        String requirement = "User can login";

        // WHEN: Improve
        List<AISuggestion> suggestions = runPromise(()
                -> service.improveRequirement(requirement));

        // THEN: Suggestions returned (empty list from mock parsing)
        assertThat(suggestions).isNotNull();
    }

    @Test
    @DisplayName("Should extract acceptance criteria (mocked)")
    void shouldExtractAcceptanceCriteria() {
        // GIVEN: Requirement
        String requirement = "Users must be able to reset their password";

        // WHEN: Extract
        List<String> criteria = runPromise(()
                -> service.extractAcceptanceCriteria(requirement));

        // THEN: Criteria returned
        assertThat(criteria).isNotNull();
    }

    @Test
    @DisplayName("Should classify requirement (mocked)")
    void shouldClassifyRequirement() {
        // GIVEN: Requirement
        String requirement = "System must validate user credentials";

        // WHEN: Classify
        RequirementType classification = runPromise(()
                -> service.classifyRequirement(requirement));

        // THEN: Classification returned (default FUNCTIONAL)
        assertThat(classification).isNotNull();
    }

    @Test
    @DisplayName("Should validate quality (mocked)")
    void shouldValidateQuality() {
        // GIVEN: Requirement
        String requirement = "User can login";

        // WHEN: Validate
        RequirementQualityResult quality = runPromise(()
                -> service.validateQuality(requirement));

        // THEN: Quality result returned
        assertThat(quality).isNotNull();
        assertThat(quality.getScore()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("Should return health status")
    void shouldReturnHealthStatus() {
        // WHEN: Check health
        Boolean isHealthy = runPromise(() -> service.healthCheck());

        // THEN: Boolean returned
        assertThat(isHealthy).isNotNull();
    }
}
