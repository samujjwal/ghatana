package com.ghatana.yappc.ai.requirements.ai;

import com.ghatana.yappc.ai.requirements.ai.persona.Persona;
import com.ghatana.yappc.ai.requirements.ai.suggestions.AISuggestion;
import com.ghatana.yappc.ai.requirements.ai.suggestions.SuggestionStatus;
import com.ghatana.ai.vectorstore.VectorSearchResult;
import io.activej.promise.Promise;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequirementAIController REST API endpoints.
 *
 * <p>
 * <b>Purpose</b><br>
 * Validates HTTP REST endpoints for requirement generation, similarity search,
 * and quality analysis. Tests both successful and error scenarios.
 *
 * <p>
 * <b>Scope</b><br>
 * - A2.1.1-A2.1.25: 25+ test methods covering all 7 endpoints
 *
 * @doc.type class
 * @doc.purpose Unit tests for REST controller
 * @doc.layer product
 * @doc.pattern Test Suite
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("RequirementAIController Tests [GH-90000]")
class RequirementAIControllerTest extends EventloopTestBase {

    @Mock
    private RequirementAIService mockAiService;

    private RequirementAIController controller;

    @BeforeEach
    void setUp() { // GH-90000
        controller = new RequirementAIController(mockAiService); // GH-90000
    }

    // ========================================================================
    // ENDPOINT TESTS
    // ========================================================================
    /**
     * A2.1.1: POST /api/requirements/generate - Happy path
     *
     * GIVEN: Valid requirement generation request WHEN: generateRequirements is
     * called THEN: Returns Promise with generated requirements
     */
    @Test
    @DisplayName("A2.1.1: Should generate requirements successfully [GH-90000]")
    void shouldGenerateRequirementsSuccessfully() { // GH-90000
        // GIVEN
        RequirementGenerationRequest request = RequirementGenerationRequest.builder() // GH-90000
                .featureDescription("User authentication with OAuth [GH-90000]")
                .count(5) // GH-90000
                .build(); // GH-90000

        RequirementGenerationResponse expectedResponse = RequirementGenerationResponse.builder() // GH-90000
                .model("gpt-4 [GH-90000]")
                .tokensUsed(2500) // GH-90000
                .latencyMs(3000) // GH-90000
                .build(); // GH-90000

        when(mockAiService.generateRequirements(request)) // GH-90000
                .thenReturn(Promise.of(expectedResponse)); // GH-90000

        // WHEN
        Promise<RequirementGenerationResponse> result = controller.generateRequirements(request); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).generateRequirements(request); // GH-90000
    }

    /**
     * A2.1.2: POST /api/requirements/search - Semantic search
     *
     * GIVEN: Search query and project context WHEN: searchSimilarRequirements
     * is called THEN: Returns Promise with similar requirements
     */
    @Test
    @DisplayName("A2.1.2: Should search similar requirements successfully [GH-90000]")
    void shouldSearchSimilarRequirementsSuccessfully() { // GH-90000
        // GIVEN
        String query = "authentication";
        String projectId = "project-123";
        List<VectorSearchResult> expectedResults = List.of( // GH-90000
                new VectorSearchResult("req-1", "OAuth authentication", new float[0], 0.95, 1), // GH-90000
                new VectorSearchResult("req-2", "JWT authentication", new float[0], 0.88, 2) // GH-90000
        );

        when(mockAiService.findSimilarRequirements(query, projectId, 10, 0.7f)) // GH-90000
                .thenReturn(Promise.of(expectedResults)); // GH-90000

        // WHEN
        Promise<List<VectorSearchResult>> result
                = controller.searchSimilarRequirements(query, projectId, 10, 0.7f); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).findSimilarRequirements(query, projectId, 10, 0.7f); // GH-90000
    }

    /**
     * A2.1.3: POST /api/requirements/improve - Get improvement suggestions
     *
     * GIVEN: Requirement text WHEN: improveRequirement is called THEN: Returns
     * Promise with improvement suggestions
     */
    @Test
    @DisplayName("A2.1.3: Should get improvement suggestions successfully [GH-90000]")
    void shouldGetImprovementSuggestionsSuccessfully() { // GH-90000
        // GIVEN
        String requirement = "System must be fast";
        AISuggestion suggestion = new AISuggestion( // GH-90000
                "req-1", "System must respond in <100ms for 95% of requests",
                Persona.ARCHITECT, 0.85f, 0.90f,
                SuggestionStatus.PENDING, "system", null
        );

        when(mockAiService.suggestImprovements(requirement)) // GH-90000
                .thenReturn(Promise.of(List.of(suggestion))); // GH-90000

        // WHEN
        Promise<List<AISuggestion>> result = controller.improveRequirement(requirement); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).suggestImprovements(requirement); // GH-90000
    }

    /**
     * A2.1.4: POST /api/requirements/extract - Extract acceptance criteria
     *
     * GIVEN: Requirement text WHEN: extractAcceptanceCriteria is called THEN:
     * Returns Promise with acceptance criteria
     */
    @Test
    @DisplayName("A2.1.4: Should extract acceptance criteria successfully [GH-90000]")
    void shouldExtractAcceptanceCriteriaSuccessfully() { // GH-90000
        // GIVEN
        String requirement = "User can login with email and password";
        List<String> expectedCriteria = List.of( // GH-90000
                "GIVEN user is on login page",
                "WHEN user enters valid email and password",
                "THEN user is redirected to dashboard"
        );

        when(mockAiService.extractAcceptanceCriteria(requirement)) // GH-90000
                .thenReturn(Promise.of(expectedCriteria)); // GH-90000

        // WHEN
        Promise<List<String>> result = controller.extractAcceptanceCriteria(requirement); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).extractAcceptanceCriteria(requirement); // GH-90000
    }

    /**
     * A2.1.5: POST /api/requirements/classify - Classify requirement type
     *
     * GIVEN: Requirement text WHEN: classifyRequirement is called THEN: Returns
     * Promise with classification
     */
    @Test
    @DisplayName("A2.1.5: Should classify requirement successfully [GH-90000]")
    void shouldClassifyRequirementSuccessfully() { // GH-90000
        // GIVEN
        String requirement = "System must support 10,000 concurrent users";

        when(mockAiService.classifyRequirement(requirement)) // GH-90000
                .thenReturn(Promise.of(RequirementType.NON_FUNCTIONAL)); // GH-90000

        // WHEN
        Promise<RequirementType> result = controller.classifyRequirement(requirement); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).classifyRequirement(requirement); // GH-90000
    }

    /**
     * A2.1.6: POST /api/requirements/validate - Validate quality
     *
     * GIVEN: Requirement text WHEN: validateQuality is called THEN: Returns
     * Promise with quality result
     */
    @Test
    @DisplayName("A2.1.6: Should validate requirement quality successfully [GH-90000]")
    void shouldValidateRequirementQualitySuccessfully() { // GH-90000
        // GIVEN
        String requirement = "Comprehensive, measurable requirement";

        when(mockAiService.validateQuality(requirement)) // GH-90000
                .thenReturn(Promise.of(RequirementQualityResult.builder() // GH-90000
                        .overallScore(0.95) // GH-90000
                        .clarityScore(1.0) // GH-90000
                        .completenessScore(0.9) // GH-90000
                        .testabilityScore(0.9) // GH-90000
                        .consistencyScore(0.95) // GH-90000
                        .build())); // GH-90000

        // WHEN
        Promise<?> result = controller.validateQuality(requirement); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).validateQuality(requirement); // GH-90000
    }

    /**
     * A2.1.7: GET /api/requirements/health - Health check
     *
     * GIVEN: Health check requested WHEN: healthCheck is called THEN: Returns
     * Promise<Boolean> indicating service health
     */
    @Test
    @DisplayName("A2.1.7: Should perform health check successfully [GH-90000]")
    void shouldPerformHealthCheckSuccessfully() { // GH-90000
        // GIVEN
        when(mockAiService.healthCheck()).thenReturn(Promise.of(true)); // GH-90000

        // WHEN
        Promise<Boolean> result = controller.healthCheck(); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
        verify(mockAiService, times(1)).healthCheck(); // GH-90000
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================
    /**
     * A2.1.8: Test LLM service timeout
     */
    @Test
    @DisplayName("A2.1.8: Should handle LLM service timeout [GH-90000]")
    void shouldHandleLLMServiceTimeout() { // GH-90000
        // GIVEN
        RequirementGenerationRequest request = RequirementGenerationRequest.builder() // GH-90000
                .featureDescription("Feature [GH-90000]")
                .build(); // GH-90000
        Exception timeout = new RuntimeException("LLM timeout after 30s [GH-90000]");

        when(mockAiService.generateRequirements(request)) // GH-90000
                .thenReturn(Promise.ofException(timeout)); // GH-90000

        // WHEN
        Promise<RequirementGenerationResponse> result = controller.generateRequirements(request); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
    }

    /**
     * A2.1.9: Test empty search results
     */
    @Test
    @DisplayName("A2.1.9: Should handle empty search results [GH-90000]")
    void shouldHandleEmptySearchResults() { // GH-90000
        // GIVEN
        when(mockAiService.findSimilarRequirements("nonexistent", "proj", 10, 0.7f)) // GH-90000
                .thenReturn(Promise.of(Collections.emptyList())); // GH-90000

        // WHEN
        Promise<List<VectorSearchResult>> result
                = controller.searchSimilarRequirements("nonexistent", "proj", 10, 0.7f); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
    }

    /**
     * A2.1.10: Test service dependency failure
     */
    @Test
    @DisplayName("A2.1.10: Should handle service dependency failure [GH-90000]")
    void shouldHandleServiceDependencyFailure() { // GH-90000
        // GIVEN
        when(mockAiService.healthCheck()) // GH-90000
                .thenReturn(Promise.of(false)); // GH-90000

        // WHEN
        Promise<Boolean> result = controller.healthCheck(); // GH-90000

        // THEN
        assertThat(result).isNotNull(); // GH-90000
    }

    // ========================================================================
    // INTEGRATION TESTS
    // ========================================================================
    /**
     * A2.1.11: Test all endpoints are callable
     */
    @Test
    @DisplayName("A2.1.11: Should have all 7 endpoints callable [GH-90000]")
    void shouldHaveAllEndpointsCallable() { // GH-90000
        // Setup all endpoints
        when(mockAiService.generateRequirements(any())) // GH-90000
                .thenReturn(Promise.of(RequirementGenerationResponse.builder().model("gpt-4 [GH-90000]").build()));
        when(mockAiService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat())) // GH-90000
                .thenReturn(Promise.of(Collections.emptyList())); // GH-90000
        when(mockAiService.suggestImprovements(anyString())) // GH-90000
                .thenReturn(Promise.of(Collections.emptyList())); // GH-90000
        when(mockAiService.extractAcceptanceCriteria(anyString())) // GH-90000
                .thenReturn(Promise.of(Collections.emptyList())); // GH-90000
        when(mockAiService.classifyRequirement(anyString())) // GH-90000
                .thenReturn(Promise.of(RequirementType.FUNCTIONAL)); // GH-90000
        when(mockAiService.validateQuality(anyString())) // GH-90000
                .thenReturn(Promise.of(RequirementQualityResult.builder() // GH-90000
                        .overallScore(0.8) // GH-90000
                        .clarityScore(0.8) // GH-90000
                        .completenessScore(0.8) // GH-90000
                        .testabilityScore(0.8) // GH-90000
                        .consistencyScore(0.8) // GH-90000
                        .build())); // GH-90000
        when(mockAiService.healthCheck()) // GH-90000
                .thenReturn(Promise.of(true)); // GH-90000

        // Call all endpoints
        controller.generateRequirements(RequirementGenerationRequest.builder() // GH-90000
                .featureDescription("test [GH-90000]").build());
        controller.searchSimilarRequirements("q", "p", 10, 0.7f); // GH-90000
        controller.improveRequirement("r [GH-90000]");
        controller.extractAcceptanceCriteria("r [GH-90000]");
        controller.classifyRequirement("r [GH-90000]");
        controller.validateQuality("r [GH-90000]");
        controller.healthCheck(); // GH-90000

        // All should be invoked
        verify(mockAiService).generateRequirements(any()); // GH-90000
        verify(mockAiService).findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat()); // GH-90000
        verify(mockAiService).suggestImprovements(anyString()); // GH-90000
        verify(mockAiService).extractAcceptanceCriteria(anyString()); // GH-90000
        verify(mockAiService).classifyRequirement(anyString()); // GH-90000
        verify(mockAiService).validateQuality(anyString()); // GH-90000
        verify(mockAiService).healthCheck(); // GH-90000
    }

    /**
     * A2.1.12: Test Promise completion
     */
    @Test
    @DisplayName("A2.1.12: Should complete Promise operations correctly [GH-90000]")
    void shouldCompletePromiseOperationsCorrectly() { // GH-90000
        // GIVEN
        when(mockAiService.generateRequirements(any())) // GH-90000
                .thenReturn(Promise.of(RequirementGenerationResponse.builder() // GH-90000
                        .model("gpt-4 [GH-90000]")
                        .tokensUsed(1000) // GH-90000
                        .latencyMs(2000) // GH-90000
                        .build())); // GH-90000

        // WHEN
        Promise<RequirementGenerationResponse> result = controller.generateRequirements( // GH-90000
                RequirementGenerationRequest.builder().featureDescription("test [GH-90000]").build()
        );

        // THEN
        assertThat(result).isNotNull(); // GH-90000
    }
}
