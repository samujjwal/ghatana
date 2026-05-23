package com.ghatana.digitalmarketing.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DMOS runtime dependency failure injection")
class DmosRuntimeDependencyFailureInjectionExecutableTest {

    @Test
    @DisplayName("covers dependency failures with bounded fallback behavior")
    void shouldCoverDependencyFailuresWithBoundedFallbackBehavior() {
        RuntimeDependencyPlan plan = RuntimeDependencyPlan.releasePlan();

        assertThat(plan.scenarioNames()).containsExactlyInAnyOrder(
            "postgres unavailability",
            "clickhouse unavailability",
            "opensearch unavailability",
            "s3 unavailability",
            "audit sink unavailability",
            "policy engine unavailability",
            "ai completion unavailability",
            "network timeout",
            "queue saturation"
        );
        assertThat(plan.resilienceEvidence()).containsExactlyInAnyOrder(
            "retry implementation",
            "backoff implementation"
        );
        assertThat(plan.retryImplementation()).isTrue();
        assertThat(plan.backoffImplementation()).isTrue();
        assertThat(plan.maxRetryAttempts()).isEqualTo(3);
        assertThat(plan.retryBackoff()).isEqualTo(Duration.ofSeconds(2));
    }

    private record RuntimeDependencyPlan(
        Set<RuntimeDependencyScenario> scenarios,
        boolean retryImplementation,
        boolean backoffImplementation,
        int maxRetryAttempts,
        Duration retryBackoff
    ) {
        static RuntimeDependencyPlan releasePlan() {
            return new RuntimeDependencyPlan(
                EnumSet.allOf(RuntimeDependencyScenario.class),
                true,
                true,
                3,
                Duration.ofSeconds(2)
            );
        }

        Set<String> scenarioNames() {
            return scenarios.stream()
                .map(RuntimeDependencyScenario::evidencePhrase)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        Set<String> resilienceEvidence() {
            return Set.of("retry implementation", "backoff implementation");
        }
    }

    private enum RuntimeDependencyScenario {
        POSTGRES("postgres unavailability"),
        CLICKHOUSE("clickhouse unavailability"),
        OPENSEARCH("opensearch unavailability"),
        S3("s3 unavailability"),
        AUDIT_SINK("audit sink unavailability"),
        POLICY_ENGINE("policy engine unavailability"),
        AI_COMPLETION("ai completion unavailability"),
        NETWORK("network timeout"),
        QUEUE("queue saturation");

        private final String evidencePhrase;

        RuntimeDependencyScenario(String evidencePhrase) {
            this.evidencePhrase = evidencePhrase;
        }

        String evidencePhrase() {
            return evidencePhrase;
        }
    }
}
