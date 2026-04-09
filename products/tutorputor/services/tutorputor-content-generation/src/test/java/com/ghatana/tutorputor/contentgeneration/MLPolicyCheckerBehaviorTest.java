package com.ghatana.tutorputor.contentgeneration;

import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.http.HttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("ML Policy Checker Tests")
class MLPolicyCheckerBehaviorTest extends EventloopTestBase {

    @Test
    @DisplayName("Should flag configured hate speech phrases")
    void shouldFlagConfiguredHateSpeechPhrases() {
        MLPolicyChecker checker = new MLPolicyChecker(
                mock(HttpClient.class),
                NoopMetricsCollector.getInstance(),
                "http://ml-service"
        );
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-a",
                PolicyType.HATE_SPEECH,
                Map.of("terms", java.util.List.of("remove this group"))
        ));

        PolicyCheckResult result = runPromise(() -> checker.checkContent(
                "tenant-a",
                "We should remove this group from the community.",
                Set.of(PolicyType.HATE_SPEECH)
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.policyType()).isEqualTo(PolicyType.HATE_SPEECH);
        assertThat(result.violations()).hasSize(1);
    }

    @Test
    @DisplayName("Should fail content that is below the configured quality threshold")
    void shouldFailContentBelowConfiguredQualityThreshold() {
        MLPolicyChecker checker = new MLPolicyChecker(
                mock(HttpClient.class),
                NoopMetricsCollector.getInstance(),
                "http://ml-service"
        );
        runPromise(() -> checker.updatePolicyConfiguration(
                "tenant-a",
                PolicyType.QUALITY_THRESHOLD,
                Map.of("threshold", 0.95)
        ));

        PolicyCheckResult result = runPromise(() -> checker.checkContent(
                "tenant-a",
                "Too short",
                Set.of(PolicyType.QUALITY_THRESHOLD)
        ));

        assertThat(result.passed()).isFalse();
        assertThat(result.policyType()).isEqualTo(PolicyType.QUALITY_THRESHOLD);
        assertThat(result.score()).isLessThan(0.95);
        assertThat(result.violations()).isNotEmpty();
    }
}
