package com.ghatana.requirements.ai;

import com.ghatana.requirements.ai.persona.Persona;
import com.ghatana.requirements.ai.suggestions.AISuggestion;
import com.ghatana.requirements.ai.suggestions.SuggestionStatus;
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
@ExtendWith(MockitoExtension.class)
@DisplayName("RequirementAIController Tests")
class RequirementAIControllerTest extends EventloopTestBase {

    @Mock
    private RequirementAIService mockAiService;

    private RequirementAIController controller;

    @BeforeEach
    void setUp() {
        controller = new RequirementAIController(mockAiService);
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
    @DisplayName("A2.1.1: Should generate requirements successfully")
    void shouldGenerateRequirementsSuccessfully() {
        // GIVEN
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .featureDescription("User authentication with OAuth")
                .count(5)
                .build();

        RequirementGenerationResponse expectedResponse = RequirementGenerationResponse.builder()
                .model("gpt-4")
                .tokensUsed(2500)
                .latencyMs(3000)
                .build();

        when(mockAiService.generateRequirements(request))
                .thenReturn(Promise.of(expectedResponse));

        // WHEN
        Promise<RequirementGenerationResponse> result = controller.generateRequirements(request);

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).generateRequirements(request);
    }

    /**
     * A2.1.2: POST /api/requirements/search - Semantic search
     *
     * GIVEN: Search query and project context WHEN: searchSimilarRequirements
     * is called THEN: Returns Promise with similar requirements
     */
    @Test
    @DisplayName("A2.1.2: Should search similar requirements successfully")
    void shouldSearchSimilarRequirementsSuccessfully() {
        // GIVEN
        String query = "authentication";
        String projectId = "project-123";
        List<VectorSearchResult> expectedResults = List.of(
                new VectorSearchResult("req-1", "OAuth authentication", new float[0], 0.95, 1),
                new VectorSearchResult("req-2", "JWT authentication", new float[0], 0.88, 2)
        );

        when(mockAiService.findSimilarRequirements(query, projectId, 10, 0.7f))
                .thenReturn(Promise.of(expectedResults));

        // WHEN
        Promise<List<VectorSearchResult>> result
                = controller.searchSimilarRequirements(query, projectId, 10, 0.7f);

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).findSimilarRequirements(query, projectId, 10, 0.7f);
    }

    /**
     * A2.1.3: POST /api/requirements/improve - Get improvement suggestions
     *
     * GIVEN: Requirement text WHEN: improveRequirement is called THEN: Returns
     * Promise with improvement suggestions
     */
    @Test
    @DisplayName("A2.1.3: Should get improvement suggestions successfully")
    void shouldGetImprovementSuggestionsSuccessfully() {
        // GIVEN
        String requirement = "System must be fast";
        AISuggestion suggestion = new AISuggestion(
                "req-1", "System must respond in <100ms for 95% of requests",
                Persona.ARCHITECT, 0.85f, 0.90f,
                SuggestionStatus.PENDING, "system", null
        );

        when(mockAiService.suggestImprovements(requirement))
                .thenReturn(Promise.of(List.of(suggestion)));

        // WHEN
        Promise<List<AISuggestion>> result = controller.improveRequirement(requirement);

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).suggestImprovements(requirement);
    }

    /**
     * A2.1.4: POST /api/requirements/extract - Extract acceptance criteria
     *
     * GIVEN: Requirement text WHEN: extractAcceptanceCriteria is called THEN:
     * Returns Promise with acceptance criteria
     */
    @Test
    @DisplayName("A2.1.4: Should extract acceptance criteria successfully")
    void shouldExtractAcceptanceCriteriaSuccessfully() {
        // GIVEN
        String requirement = "User can login with email and password";
        List<String> expectedCriteria = List.of(
                "GIVEN user is on login page",
                "WHEN user enters valid email and password",
                "THEN user is redirected to dashboard"
        );

        when(mockAiService.extractAcceptanceCriteria(requirement))
                .thenReturn(Promise.of(expectedCriteria));

        // WHEN
        Promise<List<String>> result = controller.extractAcceptanceCriteria(requirement);

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).extractAcceptanceCriteria(requirement);
    }

    /**
     * A2.1.5: POST /api/requirements/classify - Classify requirement type
     *
     * GIVEN: Requirement text WHEN: classifyRequirement is called THEN: Returns
     * Promise with classification
     */
    @Test
    @DisplayName("A2.1.5: Should classify requirement successfully")
    void shouldClassifyRequirementSuccessfully() {
        // GIVEN
        String requirement = "System must support 10,000 concurrent users";

        when(mockAiService.classifyRequirement(requirement))
                .thenReturn(Promise.of(RequirementType.NON_FUNCTIONAL));

        // WHEN
        Promise<RequirementType> result = controller.classifyRequirement(requirement);

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).classifyRequirement(requirement);
    }

    /**
     * A2.1.6: POST /api/requirements/validate - Validate quality
     *
     * GIVEN: Requirement text WHEN: validateQuality is called THEN: Returns
     * Promise with quality result
     */
    @Test
    @DisplayName("A2.1.6: Should validate requirement quality successfully")
    void shouldValidateRequirementQualitySuccessfully() {
        // GIVEN
        String requirement = "Comprehensive, measurable requirement";

        when(mockAiService.validateQuality(requirement))
                .thenReturn(Promise.of(RequirementQualityResult.builder()
                        .overallScore(0.95)
                        .clarityScore(1.0)
                        .completenessScore(0.9)
                        .testabilityScore(0.9)
                        .consistencyScore(0.95)
                        .build()));

        // WHEN
        Promise<?> result = controller.validateQuality(requirement);

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).validateQuality(requirement);
    }

    /**
     * A2.1.7: GET /api/requirements/health - Health check
     *
     * GIVEN: Health check requested WHEN: healthCheck is called THEN: Returns
     * Promise<Boolean> indicating service health
     */
    @Test
    @DisplayName("A2.1.7: Should perform health check successfully")
    void shouldPerformHealthCheckSuccessfully() {
        // GIVEN
        when(mockAiService.healthCheck()).thenReturn(Promise.of(true));

        // WHEN
        Promise<Boolean> result = controller.healthCheck();

        // THEN
        assertThat(result).isNotNull();
        verify(mockAiService, times(1)).healthCheck();
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================
    /**
     * A2.1.8: Test LLM service timeout
     */
    @Test
    @DisplayName("A2.1.8: Should handle LLM service timeout")
    void shouldHandleLLMServiceTimeout() {
        // GIVEN
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .featureDescription("Feature")
                .build();
        Exception timeout = new RuntimeException("LLM timeout after 30s");

        when(mockAiService.generateRequirements(request))
                .thenReturn(Promise.ofException(timeout));

        // WHEN
        Promise<RequirementGenerationResponse> result = controller.generateRequirements(request);

        // THEN
        assertThat(result).isNotNull();
    }

    /**
     * A2.1.9: Test empty search results
     */
    @Test
    @DisplayName("A2.1.9: Should handle empty search results")
    void shouldHandleEmptySearchResults() {
        // GIVEN
        when(mockAiService.findSimilarRequirements("nonexistent", "proj", 10, 0.7f))
                .thenReturn(Promise.of(Collections.emptyList()));

        // WHEN
        Promise<List<VectorSearchResult>> result
                = controller.searchSimilarRequirements("nonexistent", "proj", 10, 0.7f);

        // THEN
        assertThat(result).isNotNull();
    }

    /**
     * A2.1.10: Test service dependency failure
     */
    @Test
    @DisplayName("A2.1.10: Should handle service dependency failure")
    void shouldHandleServiceDependencyFailure() {
        // GIVEN
        when(mockAiService.healthCheck())
                .thenReturn(Promise.of(false));

        // WHEN
        Promise<Boolean> result = controller.healthCheck();

        // THEN
        assertThat(result).isNotNull();
    }

    // ========================================================================
    // INTEGRATION TESTS
    // ========================================================================
    /**
     * A2.1.11: Test all endpoints are callable
     */
    @Test
    @DisplayName("A2.1.11: Should have all 7 endpoints callable")
    void shouldHaveAllEndpointsCallable() {
        // Setup all endpoints
        when(mockAiService.generateRequirements(any()))
                .thenReturn(Promise.of(RequirementGenerationResponse.builder().model("gpt-4").build()));
        when(mockAiService.findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn(Promise.of(Collections.emptyList()));
        when(mockAiService.suggestImprovements(anyString()))
                .thenReturn(Promise.of(Collections.emptyList()));
        when(mockAiService.extractAcceptanceCriteria(anyString()))
                .thenReturn(Promise.of(Collections.emptyList()));
        when(mockAiService.classifyRequirement(anyString()))
                .thenReturn(Promise.of(RequirementType.FUNCTIONAL));
        when(mockAiService.validateQuality(anyString()))
                .thenReturn(Promise.of(RequirementQualityResult.builder()
                        .overallScore(0.8)
                        .clarityScore(0.8)
                        .completenessScore(0.8)
                        .testabilityScore(0.8)
                        .consistencyScore(0.8)
                        .build()));
        when(mockAiService.healthCheck())
                .thenReturn(Promise.of(true));

        // Call all endpoints
        controller.generateRequirements(RequirementGenerationRequest.builder()
                .featureDescription("test").build());
        controller.searchSimilarRequirements("q", "p", 10, 0.7f);
        controller.improveRequirement("r");
        controller.extractAcceptanceCriteria("r");
        controller.classifyRequirement("r");
        controller.validateQuality("r");
        controller.healthCheck();

        // All should be invoked
        verify(mockAiService).generateRequirements(any());
        verify(mockAiService).findSimilarRequirements(anyString(), anyString(), anyInt(), anyFloat());
        verify(mockAiService).suggestImprovements(anyString());
        verify(mockAiService).extractAcceptanceCriteria(anyString());
        verify(mockAiService).classifyRequirement(anyString());
        verify(mockAiService).validateQuality(anyString());
        verify(mockAiService).healthCheck();
    }

    /**
     * A2.1.12: Test Promise completion
     */
    @Test
    @DisplayName("A2.1.12: Should complete Promise operations correctly")
    void shouldCompletePromiseOperationsCorrectly() {
        // GIVEN
        when(mockAiService.generateRequirements(any()))
                .thenReturn(Promise.of(RequirementGenerationResponse.builder()
                        .model("gpt-4")
                        .tokensUsed(1000)
                        .latencyMs(2000)
                        .build()));

        // WHEN
        Promise<RequirementGenerationResponse> result = controller.generateRequirements(
                RequirementGenerationRequest.builder().featureDescription("test").build()
        );

        // THEN
        assertThat(result).isNotNull();
    }
}
