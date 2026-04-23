package com.ghatana.platform.pac;

import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verify circuit-breaking policy engine fails closed and stops hammering failing backends
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("CircuitBreakingPolicyAsCodeEngine")
class CircuitBreakingPolicyAsCodeEngineTest extends EventloopTestBase {

    @Test
    @DisplayName("returns deny decision after delegate failure and opens circuit for subsequent calls")
    void failsClosedAndOpensCircuit() { // GH-90000
        AtomicInteger calls = new AtomicInteger(); // GH-90000
        PolicyAsCodeEngine failingDelegate = (tenantId, policyName, input) -> { // GH-90000
            calls.incrementAndGet(); // GH-90000
            return Promise.ofException(new RuntimeException("policy store unavailable"));
        };
        CircuitBreaker breaker = CircuitBreaker.builder("pac-test")
                .failureThreshold(1) // GH-90000
                .resetTimeout(Duration.ofMinutes(5)) // GH-90000
                .build(); // GH-90000
        CircuitBreakingPolicyAsCodeEngine engine = new CircuitBreakingPolicyAsCodeEngine( // GH-90000
                failingDelegate,
                eventloop(), // GH-90000
                breaker);

        PolicyEvalResult first = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of("toolName", "delete"))); // GH-90000
        PolicyEvalResult second = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of("toolName", "delete"))); // GH-90000

        assertThat(first.allowed()).isFalse(); // GH-90000
        assertThat(first.reasons()).contains("Policy engine unavailable; failing closed");
        assertThat(second.allowed()).isFalse(); // GH-90000
        assertThat(calls).hasValue(1); // GH-90000
        assertThat(engine.circuitState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
    }

    @Test
    @DisplayName("passes through successful policy decisions")
    void delegatesSuccessfulEvaluations() { // GH-90000
        PolicyAsCodeEngine allowDelegate = (tenantId, policyName, input) -> // GH-90000
                Promise.of(PolicyEvalResult.allow(policyName)); // GH-90000
        CircuitBreakingPolicyAsCodeEngine engine = new CircuitBreakingPolicyAsCodeEngine( // GH-90000
                allowDelegate,
                eventloop()); // GH-90000

        PolicyEvalResult result = runPromise(() -> // GH-90000
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of("toolName", "read"))); // GH-90000

        assertThat(result.allowed()).isTrue(); // GH-90000
        assertThat(engine.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
    }
}