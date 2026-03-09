package com.ghatana.products.collection.domain.policy;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for RuleBasedPolicyChecker.
 *
 * Tests validate:
 * - Profanity detection with word lists
 * - PII detection (email, phone, SSN, credit card, IP, postal code)
 * - Spam detection (keywords, excessive caps)
 * - Tenant-specific configuration
 * - Batch checking
 * - Violation reporting
 *
 * @see RuleBasedPolicyChecker
 */
@DisplayName("RuleBasedPolicyChecker Tests")
class RuleBasedPolicyCheckerTest extends EventloopTestBase {

    private RuleBasedPolicyChecker checker;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        // GIVEN: Checker with noop metrics
        metrics = NoopMetricsCollector.getInstance();
        checker = new RuleBasedPolicyChecker(metrics);
    }

    /**
     * Verifies that clean content passes all checks.
     *
     * GIVEN: Clean content with no violations
     * WHEN: checkContent is called with all policies
     * THEN: Check passes with high score
     */
    @Test
    @DisplayName("Should pass when content is clean")
    void shouldPassWhenContentIsClean() {
        // GIVEN: Clean content
        String content = "This is perfectly acceptable content.";
        Set<PolicyType> policies = Set.of(
                PolicyType.PROFANITY,
                PolicyType.PII,
                PolicyType.SPAM
        );

        // WHEN: Check content
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, policies));

        // THEN: Check passes
        assertThat(result.passed())
                .as("Clean content should pass")
                .isTrue();
        assertThat(result.violations())
                .as("Should have no violations")
                .isEmpty();
        assertThat(result.score())
                .as("Score should be 1.0")
                .isEqualTo(1.0);
    }

    /**
     * Verifies profanity detection with default word list.
     *
     * GIVEN: Content with default profane word
     * WHEN: checkContent is called with PROFANITY policy
     * THEN: Violation detected with correct position
     */
    @Test
    @DisplayName("Should detect profanity with default word list")
    void shouldDetectProfanityWithDefaultWordList() {
        // GIVEN: Content with profane word
        String content = "This contains badword in the text.";

        // WHEN: Check for profanity
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PROFANITY)));

        // THEN: Profanity detected
        assertThat(result.passed())
                .as("Content with profanity should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should have one violation")
                .hasSize(1);
        assertThat(result.violations().get(0).type())
                .as("Violation type should be PROFANITY")
                .isEqualTo(PolicyType.PROFANITY);
        assertThat(result.violations().get(0).severity())
                .as("Severity should be HIGH")
                .isEqualTo("HIGH");
        assertThat(result.violations().get(0).location())
                .as("Should report correct position")
                .contains("position 14");
    }

    /**
     * Verifies profanity detection with custom word list.
     *
     * GIVEN: Tenant with custom profanity list
     * WHEN: checkContent is called
     * THEN: Custom words detected
     */
    @Test
    @DisplayName("Should detect profanity with custom word list")
    void shouldDetectProfanityWithCustomWordList() {
        // GIVEN: Custom profanity configuration
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-123",
                PolicyType.PROFANITY,
                Map.of("words", List.of("customBad", "alsoWrong"))
        ));

        String content = "This has customBad words.";

        // WHEN: Check for profanity
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PROFANITY)));

        // THEN: Custom word detected
        assertThat(result.passed())
                .as("Content with custom profanity should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect custom bad word")
                .isNotEmpty();
    }

    /**
     * Verifies case-insensitive profanity detection.
     *
     * GIVEN: Content with mixed-case profane words
     * WHEN: checkContent is called
     * THEN: All variants detected
     */
    @Test
    @DisplayName("Should detect profanity case-insensitively")
    void shouldDetectProfanityCaseInsensitively() {
        // GIVEN: Mixed-case profane content
        String content = "BADWORD BadWord badword BaDwOrD";

        // WHEN: Check for profanity
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PROFANITY)));

        // THEN: All variants detected
        assertThat(result.passed())
                .as("Mixed-case profanity should be detected")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect all 4 occurrences")
                .hasSize(4);
    }

    /**
     * Verifies email detection.
     *
     * GIVEN: Content with email addresses
     * WHEN: checkContent is called with PII policy
     * THEN: Emails detected as PII violations
     */
    @Test
    @DisplayName("Should detect email addresses as PII")
    void shouldDetectEmailAddressesAsPII() {
        // GIVEN: Content with emails
        String content = "Contact me at user@example.com or admin@test.org";

        // WHEN: Check for PII
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PII)));

        // THEN: Emails detected
        assertThat(result.passed())
                .as("Content with emails should fail PII check")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect 2 email addresses")
                .hasSize(2);
        assertThat(result.violations())
                .as("All violations should be PII type")
                .allMatch(v -> v.type() == PolicyType.PII);
        assertThat(result.violations())
                .as("All should be CRITICAL severity")
                .allMatch(v -> v.severity().equals("CRITICAL"));
    }

    /**
     * Verifies phone number detection.
     *
     * GIVEN: Content with phone numbers in various formats
     * WHEN: checkContent is called with PII policy
     * THEN: Phone numbers detected
     */
    @Test
    @DisplayName("Should detect phone numbers as PII")
    void shouldDetectPhoneNumbersAsPII() {
        // GIVEN: Content with phone numbers
        String content = "Call 415-555-1234 or (650) 555-9876 or 408.555.5555";

        // WHEN: Check for PII
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PII)));

        // THEN: Phone numbers detected
        assertThat(result.passed())
                .as("Content with phone numbers should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect phone numbers")
                .isNotEmpty();
        assertThat(result.violations())
                .as("All violations should mention phone numbers")
                .allMatch(v -> v.description().contains("PHONE"));
    }

    /**
     * Verifies SSN detection.
     *
     * GIVEN: Content with Social Security Numbers
     * WHEN: checkContent is called with PII policy
     * THEN: SSNs detected
     */
    @Test
    @DisplayName("Should detect SSN as PII")
    void shouldDetectSSNAsPII() {
        // GIVEN: Content with SSN
        String content = "My SSN is 123-45-6789 for verification.";

        // WHEN: Check for PII
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PII)));

        // THEN: SSN detected
        assertThat(result.passed())
                .as("Content with SSN should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect SSN")
                .anyMatch(v -> v.description().contains("SSN"));
    }

    /**
     * Verifies credit card detection.
     *
     * GIVEN: Content with credit card numbers
     * WHEN: checkContent is called with PII policy
     * THEN: Credit cards detected
     */
    @Test
    @DisplayName("Should detect credit card numbers as PII")
    void shouldDetectCreditCardNumbersAsPII() {
        // GIVEN: Content with credit card
        String content = "My card is 4111111111111111 for payment.";

        // WHEN: Check for PII
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PII)));

        // THEN: Credit card detected
        assertThat(result.passed())
                .as("Content with credit card should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect credit card")
                .anyMatch(v -> v.description().contains("CREDIT_CARD"));
    }

    /**
     * Verifies IP address detection.
     *
     * GIVEN: Content with IP addresses
     * WHEN: checkContent is called with PII policy
     * THEN: IPs detected
     */
    @Test
    @DisplayName("Should detect IP addresses as PII")
    void shouldDetectIPAddressesAsPII() {
        // GIVEN: Content with IP address
        String content = "Server at 192.168.1.100 is down.";

        // WHEN: Check for PII
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PII)));

        // THEN: IP detected
        assertThat(result.passed())
                .as("Content with IP should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect IP address")
                .anyMatch(v -> v.description().contains("IP_ADDRESS"));
    }

    /**
     * Verifies postal code detection.
     *
     * GIVEN: Content with ZIP codes
     * WHEN: checkContent is called with PII policy
     * THEN: ZIP codes detected
     */
    @Test
    @DisplayName("Should detect postal codes as PII")
    void shouldDetectPostalCodesAsPII() {
        // GIVEN: Content with ZIP code
        String content = "Address: 94105 San Francisco";

        // WHEN: Check for PII
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PII)));

        // THEN: ZIP code detected
        assertThat(result.passed())
                .as("Content with ZIP code should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect postal code")
                .anyMatch(v -> v.description().contains("POSTAL_CODE"));
    }

    /**
     * Verifies spam keyword detection.
     *
     * GIVEN: Content with spam keywords
     * WHEN: checkContent is called with SPAM policy
     * THEN: Spam keywords detected
     */
    @Test
    @DisplayName("Should detect spam keywords")
    void shouldDetectSpamKeywords() {
        // GIVEN: Content with spam keywords
        String content = "Click here to buy now! Limited time offer!";

        // WHEN: Check for spam
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.SPAM)));

        // THEN: Spam detected
        assertThat(result.passed())
                .as("Content with spam keywords should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect spam keywords")
                .hasSizeGreaterThan(0);
        assertThat(result.violations())
                .as("All violations should be SPAM type")
                .allMatch(v -> v.type() == PolicyType.SPAM);
    }

    /**
     * Verifies excessive capitalization detection.
     *
     * GIVEN: Content with excessive caps
     * WHEN: checkContent is called with SPAM policy
     * THEN: Excessive caps detected
     */
    @Test
    @DisplayName("Should detect excessive capitalization as spam")
    void shouldDetectExcessiveCapitalizationAsSpam() {
        // GIVEN: Content with excessive caps (>50%)
        String content = "THIS IS SHOUTING AND SPAMMY!!!";

        // WHEN: Check for spam
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.SPAM)));

        // THEN: Excessive caps detected
        assertThat(result.passed())
                .as("Content with excessive caps should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect excessive capitalization")
                .anyMatch(v -> v.description().contains("capitalization"));
    }

    /**
     * Verifies custom spam keyword configuration.
     *
     * GIVEN: Tenant with custom spam keywords
     * WHEN: checkContent is called
     * THEN: Custom keywords detected
     */
    @Test
    @DisplayName("Should detect spam with custom keywords")
    void shouldDetectSpamWithCustomKeywords() {
        // GIVEN: Custom spam configuration
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-123",
                PolicyType.SPAM,
                Map.of("keywords", List.of("urgent", "verify account"))
        ));

        String content = "Urgent: verify account immediately!";

        // WHEN: Check for spam
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.SPAM)));

        // THEN: Custom keywords detected
        assertThat(result.passed())
                .as("Content with custom spam keywords should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect custom spam keywords")
                .hasSizeGreaterThan(0);
    }

    /**
     * Verifies batch checking.
     *
     * GIVEN: Multiple content items
     * WHEN: checkBatch is called
     * THEN: Each item checked independently
     */
    @Test
    @DisplayName("Should check batch of content items")
    void shouldCheckBatchOfContentItems() {
        // GIVEN: Multiple content items
        List<String> contents = List.of(
                "Clean content",
                "Content with badword",
                "Content with user@example.com"
        );

        // WHEN: Check batch
        List<PolicyCheckResult> results = runPromise(() ->
                checker.checkBatch("tenant-123", contents, Set.of(PolicyType.PROFANITY, PolicyType.PII)));

        // THEN: Each checked independently
        assertThat(results)
                .as("Should have result for each item")
                .hasSize(3);
        assertThat(results.get(0).passed())
                .as("First item should pass")
                .isTrue();
        assertThat(results.get(1).passed())
                .as("Second item should fail (profanity)")
                .isFalse();
        assertThat(results.get(2).passed())
                .as("Third item should fail (PII)")
                .isFalse();
    }

    /**
     * Verifies multiple policy checking.
     *
     * GIVEN: Content violating multiple policies
     * WHEN: checkContent is called with multiple policies
     * THEN: All violations detected
     */
    @Test
    @DisplayName("Should detect violations of multiple policies")
    void shouldDetectViolationsOfMultiplePolicies() {
        // GIVEN: Content violating multiple policies
        String content = "Click here badword to contact user@example.com now!";

        // WHEN: Check all policies
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content,
                        Set.of(PolicyType.PROFANITY, PolicyType.PII, PolicyType.SPAM)));

        // THEN: All violations detected
        assertThat(result.passed())
                .as("Content with multiple violations should fail")
                .isFalse();
        assertThat(result.violations())
                .as("Should detect multiple violation types")
                .hasSizeGreaterThanOrEqualTo(3);
        
        // Verify each policy type present
        Set<PolicyType> violationTypes = new HashSet<>();
        result.violations().forEach(v -> violationTypes.add(v.type()));
        assertThat(violationTypes)
                .as("Should have violations from all three policies")
                .containsExactlyInAnyOrder(PolicyType.PROFANITY, PolicyType.PII, PolicyType.SPAM);
    }

    /**
     * Verifies score calculation.
     *
     * GIVEN: Content with varying violation counts
     * WHEN: checkContent is called
     * THEN: Score decreases with more violations
     */
    @Test
    @DisplayName("Should calculate score based on violation count")
    void shouldCalculateScoreBasedOnViolationCount() {
        // GIVEN: Content with one violation
        String oneViolation = "This has badword.";
        PolicyCheckResult result1 = runPromise(() ->
                checker.checkContent("tenant-123", oneViolation, Set.of(PolicyType.PROFANITY)));

        // Content with multiple violations
        String multiViolation = "badword offensive inappropriate";
        PolicyCheckResult result2 = runPromise(() ->
                checker.checkContent("tenant-123", multiViolation, Set.of(PolicyType.PROFANITY)));

        // THEN: More violations = lower score
        assertThat(result2.score())
                .as("More violations should result in lower score")
                .isLessThan(result1.score());
        assertThat(result2.score())
                .as("Score should still be non-negative")
                .isGreaterThanOrEqualTo(0.0);
    }

    /**
     * Verifies supported policies.
     *
     * GIVEN: Checker instance
     * WHEN: getSupportedPolicies is called
     * THEN: Returns correct policy types
     */
    @Test
    @DisplayName("Should return supported policies")
    void shouldReturnSupportedPolicies() {
        // WHEN: Get supported policies
        Set<PolicyType> supported = checker.getSupportedPolicies();

        // THEN: Contains expected policies
        assertThat(supported)
                .as("Should support rule-based policies")
                .containsExactlyInAnyOrder(
                        PolicyType.PROFANITY,
                        PolicyType.PII,
                        PolicyType.SPAM
                );
    }

    /**
     * Verifies null parameter validation.
     *
     * GIVEN: Null parameters
     * WHEN: checkContent is called
     * THEN: NullPointerException thrown
     */
    @Test
    @DisplayName("Should throw NullPointerException for null parameters")
    void shouldThrowNullPointerExceptionForNullParameters() {
        // WHEN/THEN: Null tenantId
        assertThatThrownBy(() ->
                runPromise(() -> checker.checkContent(null, "content", Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");

        // WHEN/THEN: Null content
        assertThatThrownBy(() ->
                runPromise(() -> checker.checkContent("tenant-123", null, Set.of(PolicyType.PROFANITY))))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("content");

        // WHEN/THEN: Null policies
        assertThatThrownBy(() ->
                runPromise(() -> checker.checkContent("tenant-123", "content", null)))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("policiesToCheck");
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
        // GIVEN: Different configurations for two tenants
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-1",
                PolicyType.PROFANITY,
                Map.of("words", List.of("tenant1bad"))
        ));

        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-2",
                PolicyType.PROFANITY,
                Map.of("words", List.of("tenant2bad"))
        ));

        String content1 = "This has tenant1bad word.";
        String content2 = "This has tenant2bad word.";

        // WHEN: Check with tenant-1 config
        PolicyCheckResult result1 = runPromise(() ->
                checker.checkContent("tenant-1", content1, Set.of(PolicyType.PROFANITY)));

        PolicyCheckResult result2a = runPromise(() ->
                checker.checkContent("tenant-1", content2, Set.of(PolicyType.PROFANITY)));

        // WHEN: Check with tenant-2 config
        PolicyCheckResult result2 = runPromise(() ->
                checker.checkContent("tenant-2", content2, Set.of(PolicyType.PROFANITY)));

        PolicyCheckResult result1a = runPromise(() ->
                checker.checkContent("tenant-2", content1, Set.of(PolicyType.PROFANITY)));

        // THEN: Each tenant uses their own word list
        assertThat(result1.passed())
                .as("Tenant-1 should detect tenant1bad")
                .isFalse();
        assertThat(result2a.passed())
                .as("Tenant-1 should not detect tenant2bad")
                .isTrue();
        
        assertThat(result2.passed())
                .as("Tenant-2 should detect tenant2bad")
                .isFalse();
        assertThat(result1a.passed())
                .as("Tenant-2 should not detect tenant1bad")
                .isTrue();
    }

    /**
     * Verifies violation suggestion messages.
     *
     * GIVEN: Content with violations
     * WHEN: checkContent is called
     * THEN: Suggestions provided for remediation
     */
    @Test
    @DisplayName("Should provide suggestions for violations")
    void shouldProvideSuggestionsForViolations() {
        // GIVEN: Content with profanity
        String content = "This has badword in it.";

        // WHEN: Check content
        PolicyCheckResult result = runPromise(() ->
                checker.checkContent("tenant-123", content, Set.of(PolicyType.PROFANITY)));

        // THEN: Suggestions provided
        assertThat(result.violations())
                .as("All violations should have suggestions")
                .allMatch(v -> v.suggestion() != null && !v.suggestion().isBlank());
        assertThat(result.violations().get(0).suggestion())
                .as("Suggestion should be actionable")
                .contains("Remove");
    }
}
