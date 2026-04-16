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
    void failsClosedAndOpensCircuit() {
        AtomicInteger calls = new AtomicInteger();
        PolicyAsCodeEngine failingDelegate = (tenantId, policyName, input) -> {
            calls.incrementAndGet();
            return Promise.ofException(new RuntimeException("policy store unavailable"));
        };
        CircuitBreaker breaker = CircuitBreaker.builder("pac-test")
                .failureThreshold(1)
                .resetTimeout(Duration.ofMinutes(5))
                .build();
        CircuitBreakingPolicyAsCodeEngine engine = new CircuitBreakingPolicyAsCodeEngine(
                failingDelegate,
                eventloop(),
                breaker);

        PolicyEvalResult first = runPromise(() ->
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of("toolName", "delete")));
        PolicyEvalResult second = runPromise(() ->
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of("toolName", "delete")));

        assertThat(first.allowed()).isFalse();
        assertThat(first.reasons()).contains("Policy engine unavailable; failing closed");
        assertThat(second.allowed()).isFalse();
        assertThat(calls).hasValue(1);
        assertThat(engine.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("passes through successful policy decisions")
    void delegatesSuccessfulEvaluations() {
        PolicyAsCodeEngine allowDelegate = (tenantId, policyName, input) ->
                Promise.of(PolicyEvalResult.allow(policyName));
        CircuitBreakingPolicyAsCodeEngine engine = new CircuitBreakingPolicyAsCodeEngine(
                allowDelegate,
                eventloop());

        PolicyEvalResult result = runPromise(() ->
                engine.evaluate("tenant-1", "tool_execution_policy", Map.of("toolName", "read")));

        assertThat(result.allowed()).isTrue();
        assertThat(engine.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}