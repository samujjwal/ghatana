package com.ghatana.agent.resilience;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.resilience.CircuitBreaker;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DisplayName("ResilientTypedAgent Resilience Coverage [GH-90000]")
class ResilientTypedAgentResilienceTest extends EventloopTestBase {

    @Override
    protected boolean breakOnFatalError() {
        // These tests intentionally exercise failure/timeout paths.
        return false;
    }

    @Test
    @DisplayName("AT-11: retries transient failures and succeeds within retry budget")
    void shouldRetryAndRecoverTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        TypedAgent<String, Map<String, Object>> delegate = new FailableTypedAgent("retry-agent", AgentType.PROBABILISTIC) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(@NotNull AgentContext ctx, @NotNull String input) {
                int attempt = attempts.incrementAndGet();
                if (attempt <= 2) {
                    return Promise.ofException(new IllegalStateException("transient failure"));
                }
                return Promise.of(AgentResult.success(Map.of("attempt", attempt), "retry-agent", Duration.ofMillis(1)));
            }
        };

        AgentConfig config = AgentConfig.builder()
            .agentId("retry-agent")
            .type(AgentType.PROBABILISTIC)
            .maxRetries(2)
            .retryBackoff(Duration.ofMillis(1))
            .maxRetryBackoff(Duration.ofMillis(1))
            .build();

        ResilientTypedAgent<String, Map<String, Object>> resilient = ResilienceDecorator.decorate(delegate, config, eventloop());
        AgentResult<Map<String, Object>> result = runPromise(() -> resilient.process(contextFor("retry-agent"), "payload"));

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        assertThat(result.getOutput()).containsEntry("attempt", 3);
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("AT-11: opens circuit after threshold and rejects next invocation")
    void shouldOpenCircuitAndRejectSubsequentCall() {
        AtomicInteger attempts = new AtomicInteger();
        TypedAgent<String, Map<String, Object>> delegate = new FailableTypedAgent("circuit-agent", AgentType.DETERMINISTIC) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(@NotNull AgentContext ctx, @NotNull String input) {
                attempts.incrementAndGet();
                return Promise.ofException(new IllegalStateException("hard failure"));
            }
        };

        AgentConfig config = AgentConfig.builder()
            .agentId("circuit-agent")
            .type(AgentType.DETERMINISTIC)
            .maxRetries(0)
            .circuitBreakerThreshold(1)
            .circuitBreakerReset(Duration.ofSeconds(30))
            .build();

        ResilientTypedAgent<String, Map<String, Object>> resilient = ResilienceDecorator.decorate(delegate, config, eventloop());

        assertThatThrownBy(() -> runPromise(() -> resilient.process(contextFor("circuit-agent"), "first")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("hard failure");

        assertThatThrownBy(() -> runPromise(() -> resilient.process(contextFor("circuit-agent"), "second")))
            .isInstanceOf(CircuitBreaker.CircuitBreakerOpenException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("AT-11: enforces processing timeout in resilient wrapper")
    void shouldFailWhenProcessingExceedsTimeout() {
        TypedAgent<String, Map<String, Object>> delegate = new FailableTypedAgent("timeout-agent", AgentType.PROBABILISTIC) {
            @Override
            public @NotNull Promise<AgentResult<Map<String, Object>>> process(@NotNull AgentContext ctx, @NotNull String input) {
                return Promise.ofCallback(callback -> {
                    // Intentionally never resolves to exercise timeout enforcement.
                });
            }
        };

        AgentConfig config = AgentConfig.builder()
            .agentId("timeout-agent")
            .type(AgentType.PROBABILISTIC)
            .maxRetries(0)
            .timeout(Duration.ofMillis(10))
            .build();

        ResilientTypedAgent<String, Map<String, Object>> resilient = ResilienceDecorator.decorate(delegate, config, eventloop());

        assertThatThrownBy(() -> runPromise(() -> resilient.process(contextFor("timeout-agent"), "payload")))
            .hasMessageContaining("timeout");
    }

    private AgentContext contextFor(String agentId) {
        return AgentContext.builder()
            .turnId("at11-turn")
            .agentId(agentId)
            .tenantId("tenant-at11")
            .memoryStore(mock(MemoryStore.class))
            .build();
    }

    private abstract static class FailableTypedAgent implements TypedAgent<String, Map<String, Object>> {
        private final AgentDescriptor descriptor;

        protected FailableTypedAgent(String agentId, AgentType type) {
            this.descriptor = AgentDescriptor.builder()
                .agentId(agentId)
                .name(agentId)
                .type(type)
                .build();
        }

        @Override
        public @NotNull AgentDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull AgentConfig config) {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<Void> shutdown() {
            return Promise.complete();
        }

        @Override
        public @NotNull Promise<HealthStatus> healthCheck() {
            return Promise.of(HealthStatus.ok());
        }
    }
}




