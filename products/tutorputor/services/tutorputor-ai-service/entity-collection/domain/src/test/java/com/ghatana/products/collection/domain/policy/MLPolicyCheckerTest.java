package com.ghatana.products.collection.domain.policy;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MLPolicyChecker.
 *
 * Tests validate:
 * - Hate speech detection via ML API
 * - NSFW detection via ML API
 * - Quality threshold checks via ML API
 * - HTTP API mocking and response parsing
 * - Timeout handling
 * - Tenant-specific configuration
 * - Batch checking with parallel execution
 * - Error handling and recovery
 *
 * @see MLPolicyChecker
 */
@DisplayName("MLPolicyChecker Tests")
class MLPolicyCheckerTest extends EventloopTestBase {

    private MLPolicyChecker checker;
    private HttpClient httpClient;
    private MetricsCollector metrics;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // GIVEN: Mocked HTTP client and metrics
        httpClient = mock(HttpClient.class);
        metrics = NoopMetricsCollector.getInstance();
        objectMapper = new ObjectMapper();
        
        checker = new MLPolicyChecker(
                httpClient,
                metrics,
                "https://ml-api.example.com"
        );
    }

    /**
     * Verifies hate speech detection passes when score below threshold.
     *
     * GIVEN: ML API returns score below threshold
     * WHEN: checkContent is called with HATE_SPEECH policy
     * THEN: Check passes with high score
     */
    @Test
    @DisplayName("Should pass hate speech check when score below threshold")
    void shouldPassHateSpeechCheckWhenScoreBelowThreshold() {
        // GIVEN: Mock successful API response
        String mockResponse = "{ \"score\": 0.3, \"categories\": [] }";
        mockHttpResponse(200, mockResponse);

        String content = "This is a friendly message.";

        // WHEN: Check for hate speech
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.HATE_SPEECH)));

        // THEN: Check passes
        assertThat(result.passed())
                .as("Friendly content should pass hate speech check")
                .isTrue();
        assertThat(result.violations())
                .as("Should have no violations")
                .isEmpty();
        assertThat(result.score())
                .as("Score should be 1.0 - 0.3 = 0.7")
                .isEqualTo(0.7);
    }

    /**
     * Verifies hate speech detection fails when score above threshold.
     *
     * GIVEN: ML API returns high score
     * WHEN: checkContent is called with HATE_SPEECH policy
     * THEN: Check fails with violation
     */
    @Test
    @DisplayName("Should detect hate speech when score above threshold")
    void shouldDetectHateSpeechWhenScoreAboveThreshold() {
        // GIVEN: Mock hate speech API response
        String mockResponse = "{ \"score\": 0.85, \"categories\": " +
                "[{\"name\": \"targeted_harassment\", \"score\": 0.9}] }";
        mockHttpResponse(200, mockResponse);

        String content = "Hateful content here.";

        // WHEN: Check for hate speech
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.HATE_SPEECH)));

        // THEN: Check fails with violations
        assertThat(result.passed())
                .as("Content with hate speech should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should have violations for hate speech")
                .hasSizeGreaterThan(0);
        assertThat(result.violations())
                .as("All violations should be HATE_SPEECH type")
                .allMatch(v -> v.type() == PolicyType.HATE_SPEECH);
        assertThat(result.violations().get(0).severity())
                .as("Severity should be CRITICAL for high score")
                .isEqualTo("CRITICAL");
    }

    /**
     * Verifies category-specific violations are included.
     *
     * GIVEN: ML API returns category scores
     * WHEN: checkContent is called
     * THEN: Category-level violations detected
     */
    @Test
    @DisplayName("Should include category-specific violations in results")
    void shouldIncludeCategorySpecificViolations() {
        // GIVEN: Mock response with multiple categories
        String mockResponse = "{ \"score\": 0.8, " +
                "\"categories\": [" +
                "{\"name\": \"toxicity\", \"score\": 0.9}," +
                "{\"name\": \"harassment\", \"score\": 0.85}" +
                "]}";
        mockHttpResponse(200, mockResponse);

        // WHEN: Check content
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content", Set.of(PolicyType.HATE_SPEECH)));

        // THEN: Category violations included
        assertThat(result.violations())
                .as("Should have violations for each high-scoring category")
                .hasSizeGreaterThan(1);
        assertThat(result.violations())
                .as("Violations should mention category names")
                .anyMatch(v -> v.description().contains("toxicity") || 
                              v.description().contains("harassment"));
    }

    /**
     * Verifies NSFW detection.
     *
     * GIVEN: ML API returns NSFW content indication
     * WHEN: checkContent is called with NSFW policy
     * THEN: NSFW violation detected
     */
    @Test
    @DisplayName("Should detect NSFW content")
    void shouldDetectNSFWContent() {
        // GIVEN: Mock NSFW response
        String mockResponse = "{ \"score\": 0.95 }";
        mockHttpResponse(200, mockResponse);

        // WHEN: Check for NSFW
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content", Set.of(PolicyType.NSFW)));

        // THEN: NSFW detected
        assertThat(result.passed())
                .as("NSFW content should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should have NSFW violation")
                .anyMatch(v -> v.type() == PolicyType.NSFW);
    }

    /**
     * Verifies quality threshold scoring.
     *
     * GIVEN: ML API returns quality metrics
     * WHEN: checkContent is called with QUALITY_THRESHOLD policy
     * THEN: Quality scored and violations reported
     */
    @Test
    @DisplayName("Should check content quality with multiple dimensions")
    void shouldCheckContentQualityWithMultipleDimensions() {
        // GIVEN: Mock quality response
        String mockResponse = "{ \"quality_score\": 0.5, " +
                "\"grammar_score\": 0.4, " +
                "\"coherence_score\": 0.3, " +
                "\"readability_score\": 0.6 }";
        mockHttpResponse(200, mockResponse);

        // WHEN: Check quality
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content", Set.of(PolicyType.QUALITY_THRESHOLD)));

        // THEN: Quality issues detected
        assertThat(result.passed())
                .as("Low quality should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should have quality violations")
                .hasSizeGreaterThan(0);
    }

    /**
     * Verifies batch checking with parallel execution.
     *
     * GIVEN: Multiple content items
     * WHEN: checkBatch is called
     * THEN: All items checked in parallel
     */
    @Test
    @DisplayName("Should check batch of content in parallel")
    void shouldCheckBatchOfContentInParallel() {
        // GIVEN: Mock responses for multiple calls
        String passResponse = "{ \"score\": 0.1 }";
        mockHttpResponse(200, passResponse);
        mockHttpResponse(200, passResponse);
        mockHttpResponse(200, passResponse);

        List<String> contents = List.of("Content 1", "Content 2", "Content 3");

        // WHEN: Check batch
        List<PolicyCheckResult> results = runPromise(() ->
                checker.checkBatch("tenant-123", contents, Set.of(PolicyType.HATE_SPEECH)));

        // THEN: All items checked
        assertThat(results)
                .as("Should have result for each item")
                .hasSize(3);
        assertThat(results)
                .as("All should pass with low scores")
                .allMatch(PolicyCheckResult::passed);
    }

    /**
     * Verifies tenant-specific endpoint configuration.
     *
     * GIVEN: Tenant with custom endpoint
     * WHEN: checkContent is called
     * THEN: Custom endpoint used
     */
    @Test
    @DisplayName("Should use tenant-specific endpoint configuration")
    void shouldUseTenantSpecificEndpointConfiguration() {
        // GIVEN: Custom endpoint configured
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-123",
                PolicyType.HATE_SPEECH,
                Map.of("endpoint", "/custom/hate-speech-checker")
        ));

        mockHttpResponse(200, "{ \"score\": 0.2 }");

        // WHEN: Check content
        runPromise(() ->
                checker.checkContent("tenant-123", "Content", Set.of(PolicyType.HATE_SPEECH)));

        // THEN: Custom endpoint should have been called
        verify(httpClient, times(1)).request(argThat(request ->
                request.getUrl().toString().contains("/custom/hate-speech-checker")
        ));
    }

    /**
     * Verifies threshold configuration.
     *
     * GIVEN: Tenant with custom threshold
     * WHEN: checkContent is called with score near threshold
     * THEN: Threshold applied correctly
     */
    @Test
    @DisplayName("Should apply tenant-specific threshold configuration")
    void shouldApplyTenantSpecificThresholdConfiguration() {
        // GIVEN: Low threshold (0.3)
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-123",
                PolicyType.HATE_SPEECH,
                Map.of("threshold", 0.3)
        ));

        // Mock response with score 0.35 (above 0.3)
        mockHttpResponse(200, "{ \"score\": 0.35 }");

        // WHEN: Check content
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content", Set.of(PolicyType.HATE_SPEECH)));

        // THEN: Score 0.35 should fail with threshold 0.3
        assertThat(result.passed())
                .as("Score 0.35 should fail with threshold 0.3")
                .isFalse();
    }

    /**
     * Verifies multiple policies in single request.
     *
     * GIVEN: Content to check against multiple policies
     * WHEN: checkContent is called with multiple policies
     * THEN: All policies checked in parallel
     */
    @Test
    @DisplayName("Should check multiple policies in parallel")
    void shouldCheckMultiplePoliciesInParallel() {
        // GIVEN: Mock responses for all policies
        mockHttpResponse(200, "{ \"score\": 0.2 }");  // Hate speech - pass
        mockHttpResponse(200, "{ \"score\": 0.2 }");  // NSFW - pass
        mockHttpResponse(200, "{ \"quality_score\": 0.8 }");  // Quality - pass

        // WHEN: Check all three policies
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content",
                        Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW, PolicyType.QUALITY_THRESHOLD)));

        // THEN: All should pass
        assertThat(result.passed())
                .as("All policies should pass")
                .isTrue();
    }

    /**
     * Verifies API error handling.
     *
     * GIVEN: ML API returns error
     * WHEN: checkContent is called
     * THEN: Exception propagated
     */
    @Test
    @DisplayName("Should handle API errors gracefully")
    void shouldHandleAPIErrorsGracefully() {
        // GIVEN: Mock API error response
        mockHttpResponse(500, "Internal Server Error");

        // WHEN/THEN: Check should fail with exception
        assertThatThrownBy(() ->
                runPromise(() ->
                        checker.checkContent("tenant-123", "Content", Set.of(PolicyType.HATE_SPEECH))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("500");
    }

    /**
     * Verifies request validation.
     *
     * GIVEN: Null parameters
     * WHEN: checkContent is called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should validate null parameters")
    void shouldValidateNullParameters() {
        // WHEN/THEN: Null tenantId
        assertThatThrownBy(() ->
                runPromise(() -> checker.checkContent(null, "content", Set.of(PolicyType.HATE_SPEECH))))
                .isInstanceOf(NullPointerException.class);

        // WHEN/THEN: Null content
        assertThatThrownBy(() ->
                runPromise(() -> checker.checkContent("tenant-123", null, Set.of(PolicyType.HATE_SPEECH))))
                .isInstanceOf(NullPointerException.class);

        // WHEN/THEN: Null policies
        assertThatThrownBy(() ->
                runPromise(() -> checker.checkContent("tenant-123", "content", null)))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies supported policies.
     *
     * GIVEN: Checker instance
     * WHEN: getSupportedPolicies is called
     * THEN: Returns ML-based policies
     */
    @Test
    @DisplayName("Should return ML-based supported policies")
    void shouldReturnMLBasedSupportedPolicies() {
        // WHEN: Get supported policies
        Set<PolicyType> supported = checker.getSupportedPolicies();

        // THEN: Contains ML-based policies
        assertThat(supported)
                .as("Should support ML-based policies")
                .containsExactlyInAnyOrder(
                        PolicyType.HATE_SPEECH,
                        PolicyType.NSFW,
                        PolicyType.QUALITY_THRESHOLD
                );
    }

    /**
     * Verifies configuration update.
     *
     * GIVEN: Policy configuration to update
     * WHEN: updatePolicyConfiguration is called
     * THEN: Configuration stored
     */
    @Test
    @DisplayName("Should update policy configuration")
    void shouldUpdatePolicyConfiguration() {
        // GIVEN: Configuration update
        Map<String, Object> config = Map.of(
                "endpoint", "/v1/custom",
                "threshold", 0.8
        );

        // WHEN: Update configuration
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-123",
                PolicyType.HATE_SPEECH,
                config
        ));

        // THEN: Configuration should be used
        mockHttpResponse(200, "{ \"score\": 0.75 }");
        
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content", Set.of(PolicyType.HATE_SPEECH)));

        // Score 0.75 should fail with threshold 0.8
        assertThat(result.passed())
                .as("Configuration should be applied")
                .isFalse();
    }

    /**
     * Verifies tenant isolation.
     *
     * GIVEN: Different tenants with different configurations
     * WHEN: checkContent is called for each tenant
     * THEN: Each uses their own configuration
     */
    @Test
    @DisplayName("Should enforce tenant isolation in configuration")
    void shouldEnforceTenantIsolationInConfiguration() {
        // GIVEN: Different thresholds for two tenants
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-1",
                PolicyType.HATE_SPEECH,
                Map.of("threshold", 0.9)
        ));

        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-2",
                PolicyType.HATE_SPEECH,
                Map.of("threshold", 0.1)
        ));

        // Mock responses
        mockHttpResponse(200, "{ \"score\": 0.5 }");
        mockHttpResponse(200, "{ \"score\": 0.5 }");

        // WHEN: Check with tenant-1 (threshold 0.9, score 0.5 should pass)
        PolicyCheckResult result1 = runPromise(() ->
                checker.checkContent("tenant-1", "Content", Set.of(PolicyType.HATE_SPEECH)));

        // WHEN: Check with tenant-2 (threshold 0.1, score 0.5 should fail)
        PolicyCheckResult result2 = runPromise(() ->
                checker.checkContent("tenant-2", "Content", Set.of(PolicyType.HATE_SPEECH)));

        // THEN: Each tenant's threshold applied
        assertThat(result1.passed())
                .as("Tenant-1 should pass (threshold 0.9 > score 0.5)")
                .isTrue();
        assertThat(result2.passed())
                .as("Tenant-2 should fail (threshold 0.1 < score 0.5)")
                .isFalse();
    }

    /**
     * Verifies unsupported policy type handling.
     *
     * GIVEN: Unsupported policy type
     * WHEN: updatePolicyConfiguration is called
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should reject unsupported policy types")
    void shouldRejectUnsupportedPolicyTypes() {
        // WHEN/THEN: Unsupported policy should fail
        assertThatThrownBy(() ->
                runPromise(() -> checker.updatePolicyConfiguration(
                        "tenant-123",
                        PolicyType.PROFANITY,
                        Map.of("words", List.of("bad"))
                )))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unsupported");
    }

    /**
     * Verifies score calculation in aggregated results.
     *
     * GIVEN: Multiple policy checks with varying scores
     * WHEN: checkContent is called with multiple policies
     * THEN: Aggregated score is average
     */
    @Test
    @DisplayName("Should aggregate scores from multiple policies")
    void shouldAggregateScoresFromMultiplePolicies() {
        // GIVEN: Mock responses with different scores
        mockHttpResponse(200, "{ \"score\": 0.2 }");  // 0.8 after inversion
        mockHttpResponse(200, "{ \"score\": 0.4 }");  // 0.6 after inversion
        mockHttpResponse(200, "{ \"quality_score\": 0.8 }");  // 0.8

        // WHEN: Check multiple policies
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", "Content",
                        Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW, PolicyType.QUALITY_THRESHOLD)));

        // THEN: Score should be average: (0.8 + 0.6 + 0.8) / 3 = 0.73
        assertThat(result.score())
                .as("Score should be average of all policy scores")
                .isGreaterThan(0.6)
                .isLessThan(0.9);
    }

    /**
     * Helper method to mock HTTP responses.
     *
     * @param statusCode HTTP status code
     * @param body response body
     */
    private void mockHttpResponse(int statusCode, String body) {
        HttpResponse response = HttpResponse.ok()
                .withStatus(statusCode)
                .withBody(body.getBytes());

        when(httpClient.request(any(HttpRequest.class)))
                .thenReturn(Promise.of(response));
    }
}
