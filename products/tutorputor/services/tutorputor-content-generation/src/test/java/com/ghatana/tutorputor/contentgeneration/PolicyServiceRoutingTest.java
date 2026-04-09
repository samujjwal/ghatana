package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Policy Service Routing Tests")
class PolicyServiceRoutingTest extends EventloopTestBase {

    @Test
    @DisplayName("Should route rule and ML policies to their respective checkers")
    void shouldRouteRuleAndMlPoliciesToTheirRespectivCheckers() {
        ContentPolicyChecker ruleChecker = mock(ContentPolicyChecker.class);
        ContentPolicyChecker mlChecker = mock(ContentPolicyChecker.class);
        PolicyService service = new PolicyService(ruleChecker, mlChecker, NoopMetricsCollector.getInstance());

        when(ruleChecker.getSupportedPolicies()).thenReturn(Set.of(PolicyType.PROFANITY, PolicyType.PII, PolicyType.SPAM));
        when(mlChecker.getSupportedPolicies()).thenReturn(Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW, PolicyType.QUALITY_THRESHOLD));
        when(ruleChecker.checkContent("tenant-a", "hello", Set.of(PolicyType.PROFANITY)))
                .thenReturn(Promise.of(PolicyCheckResult.pass(PolicyType.PROFANITY, 1.0)));
        when(mlChecker.checkContent("tenant-a", "hello", Set.of(PolicyType.NSFW)))
                .thenReturn(Promise.of(PolicyCheckResult.failWithViolations(
                        PolicyType.NSFW,
                        List.of(new PolicyCheckResult.PolicyViolation(
                                PolicyType.NSFW,
                                "HIGH",
                                "global",
                                "Explicit content detected",
                                "Remove adult content"
                        )),
                        0.2
                )));

        PolicyCheckResult result = runPromise(() -> service.validateContent(
                "tenant-a",
                "hello",
                Set.of(PolicyType.PROFANITY, PolicyType.NSFW)
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).hasSize(1);
        assertThat(result.policyType()).isEqualTo(PolicyType.NSFW);
        assertThat(result.score()).isEqualTo(0.6);
        verify(ruleChecker).checkContent("tenant-a", "hello", Set.of(PolicyType.PROFANITY));
        verify(mlChecker).checkContent("tenant-a", "hello", Set.of(PolicyType.NSFW));
    }

    @Test
    @DisplayName("Should expose the union of supported policies")
    void shouldExposeTheUnionOfSupportedPolicies() {
        ContentPolicyChecker ruleChecker = mock(ContentPolicyChecker.class);
        ContentPolicyChecker mlChecker = mock(ContentPolicyChecker.class);
        PolicyService service = new PolicyService(ruleChecker, mlChecker, NoopMetricsCollector.getInstance());

        when(ruleChecker.getSupportedPolicies()).thenReturn(Set.of(PolicyType.PROFANITY, PolicyType.PII));
        when(mlChecker.getSupportedPolicies()).thenReturn(Set.of(PolicyType.HATE_SPEECH, PolicyType.NSFW));

        assertThat(service.getSupportedPolicies()).containsExactlyInAnyOrder(
                PolicyType.PROFANITY,
                PolicyType.PII,
                PolicyType.HATE_SPEECH,
                PolicyType.NSFW
        );
    }
}
