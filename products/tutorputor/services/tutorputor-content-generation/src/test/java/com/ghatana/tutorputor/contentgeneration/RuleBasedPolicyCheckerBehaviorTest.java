package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rule-Based Policy Checker Tests")
class RuleBasedPolicyCheckerBehaviorTest extends EventloopTestBase {

    @Test
    @DisplayName("Should detect configured profanity terms")
    void shouldDetectConfiguredProfanityTerms() {
        RuleBasedPolicyChecker checker = new RuleBasedPolicyChecker(NoopMetricsCollector.getInstance());
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-a",
                PolicyType.PROFANITY,
                Map.of("words", List.of("forbiddenterm"))
        ));

        PolicyCheckResult result = runPromise(() -> checker.checkContent(
                "tenant-a",
                "This contains forbiddenterm in the middle.",
                Set.of(PolicyType.PROFANITY)
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.policyType()).isEqualTo(PolicyType.PROFANITY);
        assertThat(result.violations()).hasSize(1);
        assertThat(result.violations().getFirst().description()).contains("forbiddenterm");
    }

    @Test
    @DisplayName("Should detect personally identifiable information")
    void shouldDetectPersonallyIdentifiableInformation() {
        RuleBasedPolicyChecker checker = new RuleBasedPolicyChecker(NoopMetricsCollector.getInstance());

        PolicyCheckResult result = runPromise(() -> checker.checkContent(
                "tenant-a",
                "Reach me at user@example.com or call 415-555-1212.",
                Set.of(PolicyType.PII)
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).isNotEmpty();
        assertThat(result.violations())
                .extracting(PolicyCheckResult.PolicyViolation::description)
                .anyMatch(description -> description.contains("EMAIL"))
                .anyMatch(description -> description.contains("PHONE"));
    }
}
