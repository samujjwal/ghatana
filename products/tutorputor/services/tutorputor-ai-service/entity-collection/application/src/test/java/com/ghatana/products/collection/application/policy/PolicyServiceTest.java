package com.ghatana.products.collection.application.policy;

import com.ghatana.products.collection.domain.policy.*;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/**
 * Unit tests for PolicyService.
 *
 * Tests validate:
 * - Content validation with multiple policies
 * - Routing to appropriate checkers (rule-based vs ML)
 * - Batch validation with parallel execution
 * - Policy configuration management
 * - Aggregation of results from multiple checkers
 * - Tenant isolation
 * - Error handling
 *
 * @see PolicyService
 */
@DisplayName("PolicyService Tests")
class PolicyServiceTest extends EventloopTestBase {

    private PolicyService policyService;
    private ContentPolicyChecker ruleBasedChecker;
    private ContentPolicyChecker mlChecker;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        // GIVEN: Mock checkers
        ruleBasedChecker = mock(ContentPolicyChecker.class);
        mlChecker = mock(ContentPolicyChecker.class);
        metrics = NoopMetricsCollector.getInstance();

        // Setup mock checker policies
        when(ruleBasedChecker.getSupportedPolicies())
                .thenReturn(Set.of(PolicyType.PROFANITY, PolicyType.PII, PolicyType.SPAM));
        when(mlChecker.getSupportedPolicies())
                .thenReturn(Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW, PolicyType.QUALITY_THRESHOLD));

        policyService = new PolicyService(ruleBasedChecker, mlChecker, metrics);
    }

    /**
     * Verifies clean content passes all checks.
     *
     * GIVEN: Clean content
     * WHEN: validateContent is called
     * THEN: Check passes
     */
    @Test
    @DisplayName("Should pass when content is clean")
    void shouldPassWhenContentIsClean() {
        // GIVEN: Clean content passes both checkers
        when(ruleBasedChecker.checkContent(eq("tenant-123"), anyString(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));
        when(mlChecker.checkContent(eq("tenant-123"), anyString(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));

        // WHEN: Validate content with multiple policies
        PolicyCheckResult result = runPromise(() ->
                policyService.validateContent("tenant-123", "Clean content",
                        Set.of(PolicyType.PROFANITY, PolicyType.HATE_SPEECH, PolicyType.PII)));

        // THEN: Check passes
        assertThat(result.passed())
                .as("Clean content should pass")
                .isTrue();
    }

    /**
     * Verifies rule-based policies routed to correct checker.
     *
     * GIVEN: Rule-based policies requested
     * WHEN: validateContent is called
     * WHEN: Only rule-based checker should be called
     */
    @Test
    @DisplayName("Should route rule-based policies to rule checker")
    void shouldRouteRuleBasedPoliciesToRuleChecker() {
        // GIVEN: Mock responses
        when(ruleBasedChecker.checkContent(eq("tenant-123"), anyString(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));

        // WHEN: Validate with only rule-based policies
        runPromise(() ->
                policyService.validateContent("tenant-123", "Content",
                        Set.of(PolicyType.PROFANITY, PolicyType.PII)));

        // THEN: Only rule-based checker called
        verify(ruleBasedChecker, times(1)).checkContent(
                eq("tenant-123"), eq("Content"),
                argThat(s -> s.contains(PolicyType.PROFANITY) || s.contains(PolicyType.PII))
        );
        verify(mlChecker, never()).checkContent(any(), any(), any());
    }

    /**
     * Verifies ML-based policies routed to ML checker.
     *
     * GIVEN: ML-based policies requested
     * WHEN: validateContent is called
     * THEN: Only ML checker should be called
     */
    @Test
    @DisplayName("Should route ML-based policies to ML checker")
    void shouldRouteMLBasedPoliciesToMLChecker() {
        // GIVEN: Mock responses
        when(mlChecker.checkContent(eq("tenant-123"), anyString(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));

        // WHEN: Validate with only ML-based policies
        runPromise(() ->
                policyService.validateContent("tenant-123", "Content",
                        Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW)));

        // THEN: Only ML checker called
        verify(mlChecker, times(1)).checkContent(any(), any(), any());
        verify(ruleBasedChecker, never()).checkContent(any(), any(), any());
    }

    /**
     * Verifies mixed policies routed to both checkers.
     *
     * GIVEN: Both rule-based and ML-based policies
     * WHEN: validateContent is called
     * THEN: Both checkers invoked in parallel
     */
    @Test
    @DisplayName("Should route mixed policies to both checkers")
    void shouldRouteMixedPoliciesToBothCheckers() {
        // GIVEN: Mock responses from both checkers
        when(ruleBasedChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));
        when(mlChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));

        // WHEN: Validate with mixed policies
        runPromise(() ->
                policyService.validateContent("tenant-123", "Content",
                        Set.of(PolicyType.PROFANITY, PolicyType.HATE_SPEECH)));

        // THEN: Both checkers called
        verify(ruleBasedChecker, times(1)).checkContent(any(), any(), any());
        verify(mlChecker, times(1)).checkContent(any(), any(), any());
    }

    /**
     * Verifies batch validation.
     *
     * GIVEN: Multiple content items
     * WHEN: validateBatch is called
     * THEN: Each item validated independently
     */
    @Test
    @DisplayName("Should validate batch of content")
    void shouldValidateBatchOfContent() {
        // GIVEN: Mock responses
        when(ruleBasedChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));

        List<String> contents = List.of("Content 1", "Content 2", "Content 3");

        // WHEN: Validate batch
        List<PolicyCheckResult> results = runPromise(() ->
                policyService.validateBatch("tenant-123", contents,
                        Set.of(PolicyType.PROFANITY)));

        // THEN: Each item validated
        assertThat(results)
                .as("Should have result for each item")
                .hasSize(3);
        assertThat(results)
                .as("All should pass")
                .allMatch(PolicyCheckResult::passed);
    }

    /**
     * Verifies failure aggregation.
     *
     * GIVEN: Content failing multiple policies
     * WHEN: validateContent is called
     * THEN: Aggregated result fails with all violations
     */
    @Test
    @DisplayName("Should aggregate failures from multiple checkers")
    void shouldAggregateFailuresFromMultipleCheckers() {
        // GIVEN: Rule checker detects profanity
        List<PolicyCheckResult.PolicyViolation> ruleBased = List.of(
                new PolicyCheckResult.PolicyViolation(
                        PolicyType.PROFANITY, "HIGH", "pos 10", "Profanity", "Remove")
        );
        when(ruleBasedChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.failWithViolations(null, ruleBased, 0.5)));

        // Given: ML checker detects hate speech
        List<PolicyCheckResult.PolicyViolation> mlBased = List.of(
                new PolicyCheckResult.PolicyViolation(
                        PolicyType.HATE_SPEECH, "CRITICAL", "global", "Hate speech", "Remove")
        );
        when(mlChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.failWithViolations(null, mlBased, 0.3)));

        // WHEN: Validate content
        PolicyCheckResult result = runPromise(() ->
                policyService.validateContent("tenant-123", "Content",
                        Set.of(PolicyType.PROFANITY, PolicyType.HATE_SPEECH)));

        // THEN: Aggregated result fails with all violations
        assertThat(result.passed())
                .as("Should fail when any checker fails")
                .isFalse();
        assertThat(result.violations())
                .as("Should contain violations from both checkers")
                .hasSize(2);
    }

    /**
     * Verifies policy configuration delegation.
     *
     * GIVEN: Policy to configure
     * WHEN: configurePolicy is called
     * THEN: Delegated to appropriate checker
     */
    @Test
    @DisplayName("Should delegate policy configuration to appropriate checker")
    void shouldDelegatePolicyConfigurationToAppropriateChecker() {
        // GIVEN: Mock configuration updates
        when(ruleBasedChecker.updatePolicyConfiguration(any(), any(), any()))
                .thenReturn(Promise.complete());
        when(mlChecker.updatePolicyConfiguration(any(), any(), any()))
                .thenReturn(Promise.complete());

        // WHEN: Configure rule-based policy
        runPromise(() -> policyService.configurePolicy("tenant-123", PolicyType.PROFANITY,
                Map.of("words", List.of("bad"))));

        // THEN: Delegated to rule-based checker
        verify(ruleBasedChecker, times(1))
                .updatePolicyConfiguration(eq("tenant-123"), eq(PolicyType.PROFANITY), any());

        // WHEN: Configure ML-based policy
        runPromise(() -> policyService.configurePolicy("tenant-123", PolicyType.HATE_SPEECH,
                Map.of("threshold", 0.7)));

        // THEN: Delegated to ML checker
        verify(mlChecker, times(1))
                .updatePolicyConfiguration(eq("tenant-123"), eq(PolicyType.HATE_SPEECH), any());
    }

    /**
     * Verifies getSupportedPolicies returns all policies.
     *
     * GIVEN: Service with multiple checkers
     * WHEN: getSupportedPolicies is called
     * THEN: Returns union of all supported policies
     */
    @Test
    @DisplayName("Should return all supported policies from all checkers")
    void shouldReturnAllSupportedPoliciesFromAllCheckers() {
        // WHEN: Get supported policies
        Set<PolicyType> supported = policyService.getSupportedPolicies();

        // THEN: Contains all 6 policy types
        assertThat(supported)
                .as("Should contain all supported policies")
                .hasSize(6)
                .containsExactlyInAnyOrder(
                        PolicyType.PROFANITY,
                        PolicyType.PII,
                        PolicyType.SPAM,
                        PolicyType.HATE_SPEECH,
                        PolicyType.NSFW,
                        PolicyType.QUALITY_THRESHOLD
                );
    }

    /**
     * Verifies isSupported for each policy type.
     *
     * GIVEN: Various policy types
     * WHEN: isSupported is called
     * THEN: Returns true for all supported types
     */
    @Test
    @DisplayName("Should correctly identify supported policies")
    void shouldCorrectlyIdentifySupportedPolicies() {
        // WHEN/THEN: Check each policy
        assertThat(policyService.isSupported(PolicyType.PROFANITY)).isTrue();
        assertThat(policyService.isSupported(PolicyType.PII)).isTrue();
        assertThat(policyService.isSupported(PolicyType.SPAM)).isTrue();
        assertThat(policyService.isSupported(PolicyType.HATE_SPEECH)).isTrue();
        assertThat(policyService.isSupported(PolicyType.NSFW)).isTrue();
        assertThat(policyService.isSupported(PolicyType.QUALITY_THRESHOLD)).isTrue();

        // Should also handle unknown types gracefully
        assertThat(policyService.isSupported(PolicyType.MALICIOUS_CONTENT)).isFalse();
        assertThat(policyService.isSupported(PolicyType.COPYRIGHT_VIOLATION)).isFalse();
    }

    /**
     * Verifies parameter validation.
     *
     * GIVEN: Invalid parameters
     * WHEN: validateContent is called
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should validate parameters")
    void shouldValidateParameters() {
        // WHEN/THEN: Null tenantId
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateContent(null, "content",
                        Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(NullPointerException.class);

        // WHEN/THEN: Null content
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateContent("tenant-123", null,
                        Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(NullPointerException.class);

        // WHEN/THEN: Blank content
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateContent("tenant-123", "   ",
                        Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");

        // WHEN/THEN: Null policies
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateContent("tenant-123", "content", null)))
                .isInstanceOf(NullPointerException.class);

        // WHEN/THEN: Empty policies
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateContent("tenant-123", "content", Set.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    /**
     * Verifies batch parameter validation.
     *
     * GIVEN: Invalid batch parameters
     * WHEN: validateBatch is called
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should validate batch parameters")
    void shouldValidateBatchParameters() {
        // WHEN/THEN: Empty contents list
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateBatch("tenant-123", List.of(),
                        Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");

        // WHEN/THEN: Null contents
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateBatch("tenant-123", null,
                        Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(NullPointerException.class);
    }

    /**
     * Verifies tenant isolation.
     *
     * GIVEN: Different tenants
     * WHEN: validateContent is called for each
     * THEN: Each routed independently
     */
    @Test
    @DisplayName("Should enforce tenant isolation")
    void shouldEnforceTenantIsolation() {
        // GIVEN: Mock responses
        when(ruleBasedChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 1.0)));

        String content = "Test content";

        // WHEN: Validate for tenant-1
        runPromise(() -> policyService.validateContent("tenant-1", content,
                Set.of(PolicyType.PROFANITY)));

        // WHEN: Validate for tenant-2
        runPromise(() -> policyService.validateContent("tenant-2", content,
                Set.of(PolicyType.PROFANITY)));

        // THEN: Each tenant's request separate
        verify(ruleBasedChecker, times(2)).checkContent(any(), eq(content), any());
        
        // Verify both tenants were checked
        verify(ruleBasedChecker).checkContent(eq("tenant-1"), any(), any());
        verify(ruleBasedChecker).checkContent(eq("tenant-2"), any(), any());
    }

    /**
     * Verifies score aggregation in results.
     *
     * GIVEN: Multiple checkers with different scores
     * WHEN: validateContent is called
     * THEN: Scores averaged
     */
    @Test
    @DisplayName("Should aggregate scores from multiple checkers")
    void shouldAggregateScoresFromMultipleCheckers() {
        // GIVEN: Rule checker with 0.8 score, ML checker with 0.6 score
        when(ruleBasedChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 0.8)));
        when(mlChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.of(PolicyCheckResult.pass(null, 0.6)));

        // WHEN: Validate with both checkers
        PolicyCheckResult result = runPromise(() ->
                policyService.validateContent("tenant-123", "Content",
                        Set.of(PolicyType.PROFANITY, PolicyType.HATE_SPEECH)));

        // THEN: Score is average: (0.8 + 0.6) / 2 = 0.7
        assertThat(result.score())
                .as("Score should be average of checker scores")
                .isEqualTo(0.7);
    }

    /**
     * Verifies error propagation.
     *
     * GIVEN: Checker throws exception
     * WHEN: validateContent is called
     * THEN: Exception propagated
     */
    @Test
    @DisplayName("Should propagate errors from checkers")
    void shouldPropagateErrorsFromCheckers() {
        // GIVEN: Rule checker throws exception
        when(ruleBasedChecker.checkContent(any(), any(), any()))
                .thenReturn(Promise.ofException(new RuntimeException("Checker error")));

        // WHEN/THEN: Exception propagated
        assertThatThrownBy(() ->
                runPromise(() -> policyService.validateContent("tenant-123", "Content",
                        Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Checker error");
    }

    /**
     * Verifies configuration error handling.
     *
     * GIVEN: Unsupported policy type for configuration
     * WHEN: configurePolicy is called
     * THEN: Exception thrown
     */
    @Test
    @DisplayName("Should handle unsupported policy type in configuration")
    void shouldHandleUnsupportedPolicyTypeInConfiguration() {
        // Create service where neither checker supports MALICIOUS_CONTENT
        policyService = new PolicyService(ruleBasedChecker, mlChecker, metrics);

        // WHEN/THEN: Unsupported policy should fail
        assertThatThrownBy(() ->
                runPromise(() -> policyService.configurePolicy("tenant-123",
                        PolicyType.MALICIOUS_CONTENT, Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    /**
     * Verifies batch returns results in same order as input.
     *
     * GIVEN: Batch of 3 items with different outcomes
     * WHEN: validateBatch is called
     * THEN: Results in same order
     */
    @Test
    @DisplayName("Should return batch results in input order")
    void shouldReturnBatchResultsInInputOrder() {
        // GIVEN: Different results for each item
        PolicyCheckResult pass = PolicyCheckResult.pass(null, 1.0);
        List<PolicyCheckResult.PolicyViolation> violations = List.of(
                new PolicyCheckResult.PolicyViolation(PolicyType.PROFANITY, "HIGH", "pos", "Bad", "Remove")
        );
        PolicyCheckResult fail = PolicyCheckResult.failWithViolations(null, violations, 0.5);

        // Setup sequential responses
        when(ruleBasedChecker.checkContent(any(), eq("Good content"), any()))
                .thenReturn(Promise.of(pass));
        when(ruleBasedChecker.checkContent(any(), eq("Bad content"), any()))
                .thenReturn(Promise.of(fail));
        when(ruleBasedChecker.checkContent(any(), eq("Good again"), any()))
                .thenReturn(Promise.of(pass));

        // WHEN: Validate batch
        List<PolicyCheckResult> results = runPromise(() ->
                policyService.validateBatch("tenant-123",
                        List.of("Good content", "Bad content", "Good again"),
                        Set.of(PolicyType.PROFANITY)));

        // THEN: Results in same order
        assertThat(results).hasSize(3);
        assertThat(results.get(0).passed()).isTrue();   // First item pass
        assertThat(results.get(1).passed()).isFalse();  // Second item fail
        assertThat(results.get(2).passed()).isTrue();   // Third item pass
    }
}
