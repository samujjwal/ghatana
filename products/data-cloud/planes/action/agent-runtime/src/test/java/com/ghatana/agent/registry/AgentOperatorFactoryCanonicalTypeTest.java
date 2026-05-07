package com.ghatana.agent.registry;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.AbstractTypedAgent;
import com.ghatana.agent.registry.AgentOperatorFactory.OperatorTree;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("AgentOperatorFactory Canonical Types")
class AgentOperatorFactoryCanonicalTypeTest {

    private AgentContext context;

    @BeforeEach
    void setUp() {
        context = AgentContext.builder()
            .turnId("at-turn")
            .agentId("at-agent")
            .tenantId("tenant-at")
            .memoryStore(mock(MemoryStore.class))
            .build();
    }

    @Test
    @DisplayName("AT-1: deterministic agents map to executable operator tree")
    void shouldCreateOperatorTreeForDeterministicAgent() {
        verifyCanonicalType(AgentType.DETERMINISTIC, "deterministic-agent");
    }

    @Test
    @DisplayName("AT-2: probabilistic agents map to executable operator tree")
    void shouldCreateOperatorTreeForProbabilisticAgent() {
        verifyCanonicalType(AgentType.PROBABILISTIC, "probabilistic-agent");
    }

    @Test
    @DisplayName("AT-3: stream-processor agents map to executable operator tree")
    void shouldCreateOperatorTreeForStreamProcessorAgent() {
        verifyCanonicalType(AgentType.STREAM_PROCESSOR, "stream-processor-agent");
    }

    @Test
    @DisplayName("AT-4: planning agents map to executable operator tree")
    void shouldCreateOperatorTreeForPlanningAgent() {
        verifyCanonicalType(AgentType.PLANNING, "planning-agent");
    }

    @Test
    @DisplayName("AT-5: hybrid agents map to executable operator tree")
    void shouldCreateOperatorTreeForHybridAgent() {
        verifyCanonicalType(AgentType.HYBRID, "hybrid-agent");
    }

    @Test
    @DisplayName("AT-6: adaptive agents map to executable operator tree")
    void shouldCreateOperatorTreeForAdaptiveAgent() {
        verifyCanonicalType(AgentType.ADAPTIVE, "adaptive-agent");
    }

    @Test
    @DisplayName("AT-7: composite agents map to executable operator tree")
    void shouldCreateOperatorTreeForCompositeAgent() {
        verifyCanonicalType(AgentType.COMPOSITE, "composite-agent");
    }

    @Test
    @DisplayName("AT-8: reactive agents map to executable operator tree")
    void shouldCreateOperatorTreeForReactiveAgent() {
        verifyCanonicalType(AgentType.REACTIVE, "reactive-agent");
    }

    @Test
    @DisplayName("AT-9: custom agents map to executable operator tree")
    void shouldCreateOperatorTreeForCustomAgent() {
        verifyCanonicalType(AgentType.CUSTOM, "custom-agent");
    }

    private void verifyCanonicalType(AgentType type, String agentId) {
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry();
        CanonicalTypeAgent agent = new CanonicalTypeAgent(agentId, type);
        AgentConfig config = AgentConfig.builder().agentId(agentId).type(type).build();

        runOnEventloop(() -> registry.register(agent, config));
        runOnEventloop(() -> agent.initialize(config));

        AgentOperatorFactory factory = new AgentOperatorFactory(registry);
        OperatorTree tree = runOnEventloop(() -> factory.createOperatorTree(agentId));

        assertThat(tree.getOperators()).hasSize(1);
        assertThat(tree.getOperators().getFirst().getAgentType()).isEqualTo(type);

        AgentResult<Map<String, Object>> result = runOnEventloop(() -> tree.execute(context, Map.of("seed", "x")));
        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS);
        assertThat(result.getOutput()).containsEntry("agentType", type.name());
        assertThat(result.getOutput()).containsEntry("agentId", agentId);
    }

    private <T> T runOnEventloop(java.util.function.Supplier<Promise<T>> supplier) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
        eventloop.post(() -> supplier.get().whenResult(result::set).whenException(error::set));
        eventloop.run();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return result.get();
    }

    private static final class CanonicalTypeAgent extends AbstractTypedAgent<Map<String, Object>, Map<String, Object>> {
        private final AgentDescriptor descriptor;

        private CanonicalTypeAgent(String agentId, AgentType type) {
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
        protected @NotNull Promise<AgentResult<Map<String, Object>>> doProcess(@NotNull AgentContext ctx, @NotNull Map<String, Object> input) {
            return Promise.of(AgentResult.success(
                Map.of(
                    "agentId", descriptor.getAgentId(),
                    "agentType", descriptor.getType().name(),
                    "tenantId", ctx.getTenantId(),
                    "inputSize", input.size()
                ),
                descriptor.getAgentId(),
                Duration.ofMillis(1)
            ));
        }
    }
}


