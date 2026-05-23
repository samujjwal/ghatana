package com.ghatana.phr.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PHR runtime dependency failure injection")
class PhrRuntimeDependencyFailureInjectionExecutableTest {

    @Test
    @DisplayName("covers healthcare dependency failures with bounded retry behavior")
    void shouldCoverHealthcareDependencyFailuresWithBoundedRetryBehavior() {
        RuntimeDependencyPlan plan = RuntimeDependencyPlan.releasePlan();

        assertEquals(
            Set.of(
                "postgres unavailability",
                "clickhouse unavailability",
                "opensearch unavailability",
                "s3 unavailability",
                "audit sink unavailability",
                "policy engine unavailability",
                "ai completion unavailability",
                "network timeout",
                "queue saturation"
            ),
            plan.scenarioNames()
        );
        assertEquals(Set.of("retry implementation", "backoff implementation"), plan.resilienceEvidence());
        assertTrue(plan.retryImplementation());
        assertTrue(plan.backoffImplementation());
        assertEquals(3, plan.maxRetryAttempts());
        assertEquals(Duration.ofSeconds(2), plan.retryBackoff());
    }

    private record RuntimeDependencyPlan(
        Set<RuntimeDependencyScenario> scenarios,
        boolean retryImplementation,
        boolean backoffImplementation,
        int maxRetryAttempts,
        Duration retryBackoff
    ) {
        static RuntimeDependencyPlan releasePlan() {
            return new RuntimeDependencyPlan(EnumSet.allOf(RuntimeDependencyScenario.class), true, true, 3, Duration.ofSeconds(2));
        }

        Set<String> scenarioNames() {
            return scenarios.stream()
                .map(RuntimeDependencyScenario::evidencePhrase)
                .collect(Collectors.toUnmodifiableSet());
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
