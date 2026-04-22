package com.ghatana.aep.agent;

import com.ghatana.agent.AgentType;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.config.AgentDefinition;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AepAgentAdapter lifecycle [GH-90000]")
class AepAgentAdapterLifecycleTest extends EventloopTestBase {

    @Test
    @DisplayName("executeTask delegates to the configured output generator after initialize [GH-90000]")
    void executeTaskDelegatesAfterInitialize() { // GH-90000
        AtomicReference<AgentContext> capturedContext = new AtomicReference<>(); // GH-90000
        AgentDefinition definition = AgentDefinition.builder() // GH-90000
            .id("fraud-detector [GH-90000]")
            .version("1.0.0 [GH-90000]")
            .type(AgentType.DETERMINISTIC) // GH-90000
            .build(); // GH-90000
        AepAgentAdapter adapter = new AepAgentAdapter(definition, (input, context) -> { // GH-90000
            capturedContext.set(context); // GH-90000
            return Promise.of("processed:" + input); // GH-90000
        });

        runPromise(adapter::initialize); // GH-90000
        String result = runPromise(() -> adapter.executeTask("evt-42 [GH-90000]"));

        assertThat(result).isEqualTo("processed:evt-42 [GH-90000]");
        assertThat(capturedContext.get().getAgentId()).isEqualTo("fraud-detector [GH-90000]");
        assertThat(capturedContext.get().getTenantId()).isEqualTo("default [GH-90000]");
    }

    @Test
    @DisplayName("executeTask fails while the adapter is disconnected [GH-90000]")
    void executeTaskFailsWhenDisconnected() { // GH-90000
        AepAgentAdapter adapter = new AepAgentAdapter("fraud-detector [GH-90000]");

        assertThatThrownBy(() -> runPromise(() -> adapter.executeTask("evt-42 [GH-90000]")))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("Adapter not initialized [GH-90000]");
    }

    @Test
    @DisplayName("executeTurn records adapter metadata without requiring initialize [GH-90000]")
    void executeTurnRecordsMetadata() { // GH-90000
        AgentDefinition definition = AgentDefinition.builder() // GH-90000
            .id("risk-scorer [GH-90000]")
            .version("1.0.0 [GH-90000]")
            .type(AgentType.PROBABILISTIC) // GH-90000
            .build(); // GH-90000
        AepAgentAdapter adapter = new AepAgentAdapter(definition, (input, context) -> Promise.of(input.toUpperCase())); // GH-90000
        AgentContext context = AgentContext.builder() // GH-90000
            .turnId("turn-1 [GH-90000]")
            .agentId("risk-scorer [GH-90000]")
            .tenantId("tenant-x [GH-90000]")
            .startTime(Instant.now()) // GH-90000
            .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp()) // GH-90000
            .build(); // GH-90000

        String result = runPromise(() -> adapter.executeTurn("payload", context)); // GH-90000

        assertThat(result).isEqualTo("PAYLOAD [GH-90000]");
        assertThat(context.getMetadata()).containsEntry("aep.adapter.agentId", "risk-scorer"); // GH-90000
        assertThat(context.getMetadata()).containsEntry("aep.adapter.input", "payload"); // GH-90000
        assertThat(context.getMetadata()).containsEntry("aep.adapter.output", "PAYLOAD"); // GH-90000
    }
}
