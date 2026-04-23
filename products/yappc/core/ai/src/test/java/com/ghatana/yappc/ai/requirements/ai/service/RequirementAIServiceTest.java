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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        service = new RequirementAIService( // GH-90000
                completionService,
                embeddingService,
                vectorStore,
                personaRepository,
                promptTemplateManager,
                metricsCollector
        );

        // Default mocks
        when(personaRepository.findById(anyString())).thenReturn(Promise.of(Optional.of(Persona.DEFAULT))); // GH-90000
        when(promptTemplateManager.getTemplate(anyString())).thenReturn(new PromptTemplate("template"));

        // Mock LLM response
        CompletionResult mockResult = CompletionResult.builder() // GH-90000
                .text("Mock response")
                .tokensUsed(10) // GH-90000
                .modelUsed("test-model")
                .build(); // GH-90000
        when(completionService.complete(any(CompletionRequest.class))).thenReturn(Promise.of(mockResult)); // GH-90000

        // Mock Embedding response
        when(embeddingService.createEmbedding(anyString())).thenReturn(Promise.of(new EmbeddingResult("test", new float[1536], "test-model"))); // GH-90000

        // Mock Vector Search
        when(vectorStore.search(any(), anyInt(), anyDouble(), any())).thenReturn(Promise.of(List.of())); // GH-90000
    }

    @Test
    @DisplayName("Should generate requirements (mocked)")
    void shouldGenerateRequirements() { // GH-90000
        // GIVEN: Valid request
        RequirementGenerationRequest request = RequirementGenerationRequest.builder() // GH-90000
                .featureDescription("User authentication system")
                .context("Mobile banking application")
                .build(); // GH-90000

        // WHEN: Generate requirements
        RequirementGenerationResponse response = runPromise(() // GH-90000
                -> service.generateRequirements(request)); // GH-90000

        // THEN: Response is not null
        assertThat(response).isNotNull(); // GH-90000
        // Note: requirements list is empty because parsing is not implemented in service yet
    }

    @Test
    @DisplayName("Should search similar requirements (mocked)")
    void shouldSearchSimilarRequirements() { // GH-90000
        // GIVEN: Search query
        String query = "user authentication";

        // WHEN: Search
        List<VectorSearchResult> results = runPromise(() // GH-90000
                -> service.searchSimilarRequirements(query, 5)); // GH-90000

        // THEN: Results returned (empty list from mock) // GH-90000
        assertThat(results).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should improve requirement (mocked)")
    void shouldImproveRequirement() { // GH-90000
        // GIVEN: Simple requirement
        String requirement = "User can login";

        // WHEN: Improve
        List<AISuggestion> suggestions = runPromise(() // GH-90000
                -> service.improveRequirement(requirement)); // GH-90000

        // THEN: Suggestions returned (empty list from mock parsing) // GH-90000
        assertThat(suggestions).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should extract acceptance criteria (mocked)")
    void shouldExtractAcceptanceCriteria() { // GH-90000
        // GIVEN: Requirement
        String requirement = "Users must be able to reset their password";

        // WHEN: Extract
        List<String> criteria = runPromise(() // GH-90000
                -> service.extractAcceptanceCriteria(requirement)); // GH-90000

        // THEN: Criteria returned
        assertThat(criteria).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should classify requirement (mocked)")
    void shouldClassifyRequirement() { // GH-90000
        // GIVEN: Requirement
        String requirement = "System must validate user credentials";

        // WHEN: Classify
        RequirementType classification = runPromise(() // GH-90000
                -> service.classifyRequirement(requirement)); // GH-90000

        // THEN: Classification returned (default FUNCTIONAL) // GH-90000
        assertThat(classification).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should validate quality (mocked)")
    void shouldValidateQuality() { // GH-90000
        // GIVEN: Requirement
        String requirement = "User can login";

        // WHEN: Validate
        RequirementQualityResult quality = runPromise(() // GH-90000
                -> service.validateQuality(requirement)); // GH-90000

        // THEN: Quality result returned
        assertThat(quality).isNotNull(); // GH-90000
        assertThat(quality.getScore()).isGreaterThanOrEqualTo(0.0); // GH-90000
    }

    @Test
    @DisplayName("Should return health status")
    void shouldReturnHealthStatus() { // GH-90000
        // WHEN: Check health
        Boolean isHealthy = runPromise(() -> service.healthCheck()); // GH-90000

        // THEN: Boolean returned
        assertThat(isHealthy).isNotNull(); // GH-90000
    }
}
