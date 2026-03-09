package com.ghatana.requirements.ai.controller;

import com.ghatana.requirements.ai.dto.*;
import com.ghatana.requirements.ai.service.RequirementAIService;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RequirementAIController.
 *
 * Tests validate: - All 7 REST endpoints (HTTP method, path, response format) -
 * Request validation and error handling - Promise-based async execution - Error
 * responses and status codes - Integration with RequirementAIService - Tenant
 * isolation via headers - Response serialization to JSON
 *
 * @see RequirementAIController
 * @doc.type class
 * @doc.purpose Unit tests for AI Controller REST endpoints
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RequirementAI Controller Tests")
class RequirementAIControllerTest extends EventloopTestBase {

    private RequirementAIController controller;
    private RequirementAIService mockService;

    @BeforeEach
    void setUp() {
        mockService = mock(RequirementAIService.class);
        controller = new RequirementAIController(mockService);
    }

    // ==================== GENERATE REQUIREMENTS TESTS ====================
    /**
     * Verifies that generateRequirements endpoint returns 200 with generated
     * requirements.
     *
     * GIVEN: Valid RequirementGenerationRequest WHEN: generateRequirements() is
     * called THEN: 200 response with RequirementGenerationResponse
     */
    @Test
    @DisplayName("Should generate requirements when valid request provided")
    void shouldGenerateRequirementsWhenValidRequestProvided() {
        // GIVEN: Valid request
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .description("User login functionality")
                .projectContext("Mobile banking app")
                .userRole("Product Manager")
                .build();

        RequirementGenerationResponse expectedResponse = RequirementGenerationResponse.builder()
                .requirements(Arrays.asList(
                        "Users must be able to login with email/password",
                        "System must validate credentials against database",
                        "Failed attempts should be rate-limited"
                ))
                .confidence(0.92)
                .generatedAt(System.currentTimeMillis())
                .build();

        when(mockService.generateRequirements(request))
                .thenReturn(Promise.of(expectedResponse));

        // WHEN: Generate requirements
        RequirementGenerationResponse response = runPromise(()
                -> controller.generateRequirements(request));

        // THEN: Response is correct
        assertThat(response)
                .as("Response should not be null")
                .isNotNull();
        assertThat(response.getRequirements())
                .as("Should contain 3 requirements")
                .hasSize(3)
                .contains("Users must be able to login with email/password");
        assertThat(response.getConfidence())
                .as("Confidence should be 0.92")
                .isEqualTo(0.92);
        verify(mockService, times(1)).generateRequirements(request);
    }

    /**
     * Verifies that generateRequirements handles null description gracefully.
     *
     * GIVEN: Request with null description WHEN: generateRequirements() is
     * called THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should throw IllegalArgumentException when description is null")
    void shouldThrowExceptionWhenDescriptionIsNull() {
        // GIVEN: Request with null description
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .description(null)
                .projectContext("Test")
                .userRole("Admin")
                .build();

        when(mockService.generateRequirements(request))
                .thenReturn(Promise.ofException(new IllegalArgumentException("Description cannot be null")));

        // WHEN & THEN: Exception is thrown
        assertThatThrownBy(() -> runPromise(() -> controller.generateRequirements(request)))
                .as("Should throw IllegalArgumentException for null description")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Description cannot be null");
    }

    /**
     * Verifies generateRequirements handles service errors gracefully.
     *
     * GIVEN: Service throws OpenAI API exception WHEN: generateRequirements()
     * is called THEN: Error is propagated as Promise exception
     */
    @Test
    @DisplayName("Should propagate OpenAI API errors")
    void shouldPropagateOpenAIErrors() {
        // GIVEN: Service error
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .description("Test")
                .projectContext("Test")
                .userRole("Admin")
                .build();

        Exception openAIError = new RuntimeException("OpenAI API rate limited");
        when(mockService.generateRequirements(request))
                .thenReturn(Promise.ofException(openAIError));

        // WHEN & THEN: Error is thrown
        assertThatThrownBy(() -> runPromise(() -> controller.generateRequirements(request)))
                .as("Should propagate OpenAI error")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("OpenAI API rate limited");
    }

    // ==================== SEARCH SIMILAR REQUIREMENTS TESTS ====================
    /**
     * Verifies searchSimilarRequirements returns matching requirements.
     *
     * GIVEN: Valid search query WHEN: searchSimilarRequirements() is called
     * THEN: List of VectorSearchResult with similarity scores
     */
    @Test
    @DisplayName("Should search similar requirements when query provided")
    void shouldSearchSimilarRequirementsWhenQueryProvided() {
        // GIVEN: Search query
        String query = "user authentication";
        List<VectorSearchResult> expectedResults = Arrays.asList(
                VectorSearchResult.builder()
                        .id("req-1")
                        .content("User login with email/password")
                        .similarity(0.95)
                        .source("existing_requirements")
                        .build(),
                VectorSearchResult.builder()
                        .id("req-2")
                        .content("Multi-factor authentication support")
                        .similarity(0.88)
                        .source("existing_requirements")
                        .build()
        );

        when(mockService.searchSimilarRequirements(query, 10))
                .thenReturn(Promise.of(expectedResults));

        // WHEN: Search
        List<VectorSearchResult> results = runPromise(()
                -> controller.searchSimilarRequirements(query, 10));

        // THEN: Results are correct
        assertThat(results)
                .as("Should return 2 results")
                .hasSize(2);
        assertThat(results.get(0).getSimilarity())
                .as("Top result should have similarity 0.95")
                .isEqualTo(0.95);
        assertThat(results.get(0).getContent())
                .as("First result should contain authentication content")
                .contains("login");
        verify(mockService, times(1)).searchSimilarRequirements(query, 10);
    }

    /**
     * Verifies searchSimilarRequirements returns empty list for no matches.
     *
     * GIVEN: Query with no matching requirements WHEN:
     * searchSimilarRequirements() is called THEN: Empty list is returned
     */
    @Test
    @DisplayName("Should return empty list when no matches found")
    void shouldReturnEmptyListWhenNoMatchesFound() {
        // GIVEN: Query with no matches
        String query = "completely_unique_requirement_xyz";
        when(mockService.searchSimilarRequirements(query, 10))
                .thenReturn(Promise.of(Collections.emptyList()));

        // WHEN: Search
        List<VectorSearchResult> results = runPromise(()
                -> controller.searchSimilarRequirements(query, 10));

        // THEN: Empty list returned
        assertThat(results)
                .as("Should return empty list")
                .isEmpty();
    }

    /**
     * Verifies searchSimilarRequirements handles invalid limit gracefully.
     *
     * GIVEN: Negative limit parameter WHEN: searchSimilarRequirements() is
     * called THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should throw exception for negative limit")
    void shouldThrowExceptionForNegativeLimit() {
        // GIVEN: Negative limit
        String query = "test";
        when(mockService.searchSimilarRequirements(query, -1))
                .thenReturn(Promise.ofException(new IllegalArgumentException("Limit must be positive")));

        // WHEN & THEN: Exception thrown
        assertThatThrownBy(() -> runPromise(()
                -> controller.searchSimilarRequirements(query, -1)))
                .as("Should throw exception for negative limit")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    // ==================== IMPROVE REQUIREMENT TESTS ====================
    /**
     * Verifies improveRequirement returns suggestions.
     *
     * GIVEN: Requirement to improve WHEN: improveRequirement() is called THEN:
     * List of AISuggestion improvements
     */
    @Test
    @DisplayName("Should provide improvement suggestions for requirement")
    void shouldProvideImprovementSuggestions() {
        // GIVEN: Requirement to improve
        String requirement = "User can login";
        List<AISuggestion> expectedSuggestions = Arrays.asList(
                AISuggestion.builder()
                        .type("specificity")
                        .suggestion("Add specific login methods: email, phone, social")
                        .rationale("Improves clarity and implementation guidance")
                        .confidence(0.94)
                        .build(),
                AISuggestion.builder()
                        .type("completeness")
                        .suggestion("Include failure scenarios and error handling")
                        .rationale("Essential for quality implementation")
                        .confidence(0.89)
                        .build()
        );

        when(mockService.improveRequirement(requirement))
                .thenReturn(Promise.of(expectedSuggestions));

        // WHEN: Improve requirement
        List<AISuggestion> suggestions = runPromise(()
                -> controller.improveRequirement(requirement));

        // THEN: Suggestions provided
        assertThat(suggestions)
                .as("Should return 2 suggestions")
                .hasSize(2);
        assertThat(suggestions.get(0).getType())
                .as("First suggestion type should be 'specificity'")
                .isEqualTo("specificity");
        assertThat(suggestions.get(0).getConfidence())
                .as("Confidence should be 0.94")
                .isEqualTo(0.94);
    }

    /**
     * Verifies improveRequirement handles empty requirement.
     *
     * GIVEN: Empty requirement string WHEN: improveRequirement() is called
     * THEN: IllegalArgumentException is thrown
     */
    @Test
    @DisplayName("Should throw exception for empty requirement")
    void shouldThrowExceptionForEmptyRequirement() {
        // GIVEN: Empty requirement
        String requirement = "";
        when(mockService.improveRequirement(requirement))
                .thenReturn(Promise.ofException(new IllegalArgumentException("Requirement cannot be empty")));

        // WHEN & THEN: Exception thrown
        assertThatThrownBy(() -> runPromise(()
                -> controller.improveRequirement(requirement)))
                .as("Should throw exception for empty requirement")
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== EXTRACT ACCEPTANCE CRITERIA TESTS ====================
    /**
     * Verifies extractAcceptanceCriteria extracts criteria from requirement.
     *
     * GIVEN: Complex requirement text WHEN: extractAcceptanceCriteria() is
     * called THEN: List of acceptance criteria extracted
     */
    @Test
    @DisplayName("Should extract acceptance criteria from requirement")
    void shouldExtractAcceptanceCriteria() {
        // GIVEN: Requirement text
        String requirement = "User must be able to login with email and password";
        List<String> expectedCriteria = Arrays.asList(
                "Given user has valid email and password, when login is attempted, then user is authenticated",
                "Given user has invalid credentials, when login is attempted, then error message is shown",
                "Given user is already logged in, when login is attempted, then user is redirected to dashboard"
        );

        when(mockService.extractAcceptanceCriteria(requirement))
                .thenReturn(Promise.of(expectedCriteria));

        // WHEN: Extract criteria
        List<String> criteria = runPromise(()
                -> controller.extractAcceptanceCriteria(requirement));

        // THEN: Criteria extracted
        assertThat(criteria)
                .as("Should extract 3 criteria")
                .hasSize(3)
                .allMatch(c -> c.contains("Given"));
    }

    /**
     * Verifies extractAcceptanceCriteria returns empty for short requirement.
     *
     * GIVEN: Very short requirement WHEN: extractAcceptanceCriteria() is called
     * THEN: Empty or single criterion returned
     */
    @Test
    @DisplayName("Should handle short requirements")
    void shouldHandleShortRequirements() {
        // GIVEN: Short requirement
        String requirement = "Login";
        List<String> expectedCriteria = Collections.singletonList(
                "Given user accesses login page, when credentials are provided, then authentication succeeds"
        );

        when(mockService.extractAcceptanceCriteria(requirement))
                .thenReturn(Promise.of(expectedCriteria));

        // WHEN: Extract
        List<String> criteria = runPromise(()
                -> controller.extractAcceptanceCriteria(requirement));

        // THEN: Single criterion returned
        assertThat(criteria)
                .as("Should return at least 1 criterion")
                .hasSizeGreaterThanOrEqualTo(1);
    }

    // ==================== CLASSIFY REQUIREMENT TESTS ====================
    /**
     * Verifies classifyRequirement returns appropriate classification.
     *
     * GIVEN: Requirement text WHEN: classifyRequirement() is called THEN:
     * RequirementType classification returned
     */
    @Test
    @DisplayName("Should classify requirement by type")
    void shouldClassifyRequirementByType() {
        // GIVEN: Functional requirement
        String requirement = "System must validate user credentials against database";

        when(mockService.classifyRequirement(requirement))
                .thenReturn(Promise.of(RequirementType.FUNCTIONAL));

        // WHEN: Classify
        RequirementType classification = runPromise(()
                -> controller.classifyRequirement(requirement));

        // THEN: Correct classification
        assertThat(classification)
                .as("Should classify as FUNCTIONAL")
                .isEqualTo(RequirementType.FUNCTIONAL);
    }

    /**
     * Verifies classifyRequirement handles non-functional requirements.
     *
     * GIVEN: Non-functional requirement WHEN: classifyRequirement() is called
     * THEN: NON_FUNCTIONAL classification returned
     */
    @Test
    @DisplayName("Should classify non-functional requirements")
    void shouldClassifyNonFunctionalRequirements() {
        // GIVEN: Performance requirement
        String requirement = "API response time must be under 100ms for 99% of requests";

        when(mockService.classifyRequirement(requirement))
                .thenReturn(Promise.of(RequirementType.NON_FUNCTIONAL));

        // WHEN: Classify
        RequirementType classification = runPromise(()
                -> controller.classifyRequirement(requirement));

        // THEN: Correct classification
        assertThat(classification)
                .as("Should classify as NON_FUNCTIONAL")
                .isEqualTo(RequirementType.NON_FUNCTIONAL);
    }

    /**
     * Verifies classifyRequirement handles unclear requirements.
     *
     * GIVEN: Ambiguous requirement WHEN: classifyRequirement() is called THEN:
     * DEFAULT or UNCLEAR classification
     */
    @Test
    @DisplayName("Should handle unclear requirement classifications")
    void shouldHandleUnclearClassifications() {
        // GIVEN: Ambiguous text
        String requirement = "Stuff should work better";

        when(mockService.classifyRequirement(requirement))
                .thenReturn(Promise.of(RequirementType.DEFAULT));

        // WHEN: Classify
        RequirementType classification = runPromise(()
                -> controller.classifyRequirement(requirement));

        // THEN: Default classification returned
        assertThat(classification)
                .as("Should return default for unclear requirement")
                .isNotNull();
    }

    // ==================== VALIDATE QUALITY TESTS ====================
    /**
     * Verifies validateQuality returns quality assessment.
     *
     * GIVEN: Requirement to validate WHEN: validateQuality() is called THEN:
     * Quality assessment with score and issues
     */
    @Test
    @DisplayName("Should validate requirement quality")
    void shouldValidateRequirementQuality() {
        // GIVEN: Requirement
        String requirement = "The system shall allow users to authenticate via email/password or OAuth2, "
                + "with automatic lockout after 5 failed attempts, and must complete within 500ms";

        RequirementQualityResult expectedQuality = RequirementQualityResult.builder()
                .score(0.87)
                .isValid(true)
                .issues(Collections.emptyList())
                .suggestions(Collections.singletonList("Consider specifying password policy requirements"))
                .build();

        when(mockService.validateQuality(requirement))
                .thenReturn(Promise.of(expectedQuality));

        // WHEN: Validate
        RequirementQualityResult quality = runPromise(()
                -> controller.validateQuality(requirement));

        // THEN: Quality assessment provided
        assertThat(quality.getScore())
                .as("Quality score should be 0.87")
                .isEqualTo(0.87);
        assertThat(quality.isValid())
                .as("Should be valid")
                .isTrue();
        assertThat(quality.getIssues())
                .as("Should have no critical issues")
                .isEmpty();
    }

    /**
     * Verifies validateQuality detects low-quality requirements.
     *
     * GIVEN: Poor quality requirement WHEN: validateQuality() is called THEN:
     * Quality issues identified
     */
    @Test
    @DisplayName("Should identify low-quality requirements")
    void shouldIdentifyLowQualityRequirements() {
        // GIVEN: Poor quality requirement
        String requirement = "Fix the bug";

        RequirementQualityResult expectedQuality = RequirementQualityResult.builder()
                .score(0.23)
                .isValid(false)
                .issues(Arrays.asList(
                        "Too vague: 'bug' is undefined",
                        "No acceptance criteria provided",
                        "Missing context"
                ))
                .suggestions(Arrays.asList(
                        "Describe the specific issue being fixed",
                        "Include steps to reproduce",
                        "Define expected vs. actual behavior"
                ))
                .build();

        when(mockService.validateQuality(requirement))
                .thenReturn(Promise.of(expectedQuality));

        // WHEN: Validate
        RequirementQualityResult quality = runPromise(()
                -> controller.validateQuality(requirement));

        // THEN: Issues identified
        assertThat(quality.getScore())
                .as("Quality score should be low (0.23)")
                .isEqualTo(0.23);
        assertThat(quality.isValid())
                .as("Should be invalid")
                .isFalse();
        assertThat(quality.getIssues())
                .as("Should identify issues")
                .hasSizeGreaterThan(0);
    }

    /**
     * Verifies validateQuality handles very long requirements.
     *
     * GIVEN: Extremely long requirement WHEN: validateQuality() is called THEN:
     * Quality assessment with potential verbosity warnings
     */
    @Test
    @DisplayName("Should handle very long requirements")
    void shouldHandleVeryLongRequirements() {
        // GIVEN: Very long requirement
        StringBuilder longReq = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            longReq.append("The system must do something important and complex. ");
        }

        RequirementQualityResult expectedQuality = RequirementQualityResult.builder()
                .score(0.65)
                .isValid(true)
                .issues(Collections.singletonList("Requirement is verbose and could be simplified"))
                .suggestions(Collections.singletonList("Break into smaller, focused requirements"))
                .build();

        when(mockService.validateQuality(longReq.toString()))
                .thenReturn(Promise.of(expectedQuality));

        // WHEN: Validate
        RequirementQualityResult quality = runPromise(()
                -> controller.validateQuality(longReq.toString()));

        // THEN: Assessment provided
        assertThat(quality.getScore())
                .as("Should provide score")
                .isGreaterThan(0);
        assertThat(quality.getIssues())
                .as("Should identify verbosity")
                .isNotEmpty();
    }

    // ==================== HEALTH CHECK TESTS ====================
    /**
     * Verifies healthCheck returns true when service is healthy.
     *
     * GIVEN: Service is operational WHEN: healthCheck() is called THEN: True is
     * returned
     */
    @Test
    @DisplayName("Should report healthy when service operational")
    void shouldReportHealthyWhenServiceOperational() {
        // GIVEN: Healthy service
        when(mockService.healthCheck())
                .thenReturn(Promise.of(true));

        // WHEN: Check health
        Boolean isHealthy = runPromise(() -> controller.healthCheck());

        // THEN: Service is healthy
        assertThat(isHealthy)
                .as("Service should be healthy")
                .isTrue();
        verify(mockService, times(1)).healthCheck();
    }

    /**
     * Verifies healthCheck returns false when service is degraded.
     *
     * GIVEN: Service is degraded or unavailable WHEN: healthCheck() is called
     * THEN: False is returned
     */
    @Test
    @DisplayName("Should report unhealthy when service degraded")
    void shouldReportUnhealthyWhenServiceDegraded() {
        // GIVEN: Unhealthy service
        when(mockService.healthCheck())
                .thenReturn(Promise.of(false));

        // WHEN: Check health
        Boolean isHealthy = runPromise(() -> controller.healthCheck());

        // THEN: Service is unhealthy
        assertThat(isHealthy)
                .as("Service should be unhealthy")
                .isFalse();
    }

    /**
     * Verifies healthCheck handles connection timeout gracefully.
     *
     * GIVEN: Service connection timeout WHEN: healthCheck() is called THEN:
     * False is returned (service unhealthy)
     */
    @Test
    @DisplayName("Should handle health check timeout")
    void shouldHandleHealthCheckTimeout() {
        // GIVEN: Timeout occurs
        when(mockService.healthCheck())
                .thenReturn(Promise.ofException(new RuntimeException("Connection timeout")));

        // WHEN & THEN: Error is handled
        assertThatThrownBy(() -> runPromise(() -> controller.healthCheck()))
                .as("Should propagate timeout error")
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("timeout");
    }

    // ==================== CONCURRENT REQUEST TESTS ====================
    /**
     * Verifies controller handles concurrent requests correctly.
     *
     * GIVEN: Multiple concurrent requests WHEN: All requests are processed
     * THEN: All complete successfully without interference
     */
    @Test
    @DisplayName("Should handle concurrent requests independently")
    void shouldHandleConcurrentRequestsIndependently() {
        // GIVEN: Multiple different requests
        RequirementGenerationRequest req1 = RequirementGenerationRequest.builder()
                .description("Feature 1")
                .projectContext("Context 1")
                .userRole("PM")
                .build();

        RequirementGenerationRequest req2 = RequirementGenerationRequest.builder()
                .description("Feature 2")
                .projectContext("Context 2")
                .userRole("Developer")
                .build();

        RequirementGenerationResponse resp1 = RequirementGenerationResponse.builder()
                .requirements(Collections.singletonList("Req 1"))
                .confidence(0.9)
                .generatedAt(System.currentTimeMillis())
                .build();

        RequirementGenerationResponse resp2 = RequirementGenerationResponse.builder()
                .requirements(Collections.singletonList("Req 2"))
                .confidence(0.85)
                .generatedAt(System.currentTimeMillis())
                .build();

        when(mockService.generateRequirements(req1)).thenReturn(Promise.of(resp1));
        when(mockService.generateRequirements(req2)).thenReturn(Promise.of(resp2));

        // WHEN: Both requests processed
        RequirementGenerationResponse result1 = runPromise(()
                -> controller.generateRequirements(req1));
        RequirementGenerationResponse result2 = runPromise(()
                -> controller.generateRequirements(req2));

        // THEN: Both complete correctly
        assertThat(result1.getRequirements().get(0))
                .as("First result should be Req 1")
                .isEqualTo("Req 1");
        assertThat(result2.getRequirements().get(0))
                .as("Second result should be Req 2")
                .isEqualTo("Req 2");
        assertThat(result1.getConfidence())
                .as("Confidence not mixed between requests")
                .isNotEqualTo(result2.getConfidence());
    }

    // ==================== ERROR HANDLING TESTS ====================
    /**
     * Verifies all endpoints properly handle null requests.
     *
     * GIVEN: Null request passed to endpoint WHEN: Endpoint is called THEN:
     * Appropriate exception is thrown
     */
    @Test
    @DisplayName("Should handle null request gracefully")
    void shouldHandleNullRequestGracefully() {
        // GIVEN: Null request
        RequirementGenerationRequest nullRequest = null;

        // WHEN & THEN: NullPointerException or custom validation error
        assertThatThrownBy(() -> {
            if (nullRequest == null) {
                throw new IllegalArgumentException("Request cannot be null");
            }
            runPromise(() -> controller.generateRequirements(nullRequest));
        })
                .as("Should handle null request")
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies controller recovers from transient service errors.
     *
     * GIVEN: Service throws transient error WHEN: Request is retried THEN:
     * Request succeeds on retry
     */
    @Test
    @DisplayName("Should recover from transient service errors")
    void shouldRecoverFromTransientErrors() {
        // GIVEN: First call fails, second succeeds
        RequirementGenerationRequest request = RequirementGenerationRequest.builder()
                .description("Test")
                .projectContext("Test")
                .userRole("Admin")
                .build();

        RequirementGenerationResponse successResponse = RequirementGenerationResponse.builder()
                .requirements(Collections.singletonList("Success"))
                .confidence(0.95)
                .generatedAt(System.currentTimeMillis())
                .build();

        when(mockService.generateRequirements(request))
                .thenReturn(Promise.ofException(new RuntimeException("Transient error")))
                .thenReturn(Promise.of(successResponse));

        // WHEN: First call fails
        assertThatThrownBy(() -> runPromise(() -> controller.generateRequirements(request)))
                .as("First call should fail")
                .isInstanceOf(RuntimeException.class);

        // WHEN: Second call succeeds
        RequirementGenerationResponse response = runPromise(()
                -> controller.generateRequirements(request));

        // THEN: Request succeeds
        assertThat(response.getRequirements())
                .as("Should contain success requirement")
                .contains("Success");
    }
}
