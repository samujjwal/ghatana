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

@DisplayName("AepAgentAdapter lifecycle")
class AepAgentAdapterLifecycleTest extends EventloopTestBase {

    @Test
    @DisplayName("executeTask delegates to the configured output generator after initialize")
    void executeTaskDelegatesAfterInitialize() { 
        AtomicReference<AgentContext> capturedContext = new AtomicReference<>(); 
        AgentDefinition definition = AgentDefinition.builder() 
            .id("fraud-detector")
            .version("1.0.0")
            .type(AgentType.DETERMINISTIC) 
            .build(); 
        AepAgentAdapter adapter = new AepAgentAdapter(definition, (input, context) -> { 
            capturedContext.set(context); 
            return Promise.of("processed:" + input); 
        });

        runPromise(adapter::initialize); 
        String result = runPromise(() -> adapter.executeTask("evt-42"));

        assertThat(result).isEqualTo("processed:evt-42");
        assertThat(capturedContext.get().getAgentId()).isEqualTo("fraud-detector");
        assertThat(capturedContext.get().getTenantId()).isEqualTo("default");
    }

    @Test
    @DisplayName("executeTask fails while the adapter is disconnected")
    void executeTaskFailsWhenDisconnected() { 
        AepAgentAdapter adapter = new AepAgentAdapter("fraud-detector");

        assertThatThrownBy(() -> runPromise(() -> adapter.executeTask("evt-42")))
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("Adapter not initialized");
    }

    @Test
    @DisplayName("executeTurn records adapter metadata without requiring initialize")
    void executeTurnRecordsMetadata() { 
        AgentDefinition definition = AgentDefinition.builder() 
            .id("risk-scorer")
            .version("1.0.0")
            .type(AgentType.PROBABILISTIC) 
            .build(); 
        AepAgentAdapter adapter = new AepAgentAdapter(definition, (input, context) -> Promise.of(input.toUpperCase())); 
        AgentContext context = AgentContext.builder() 
            .turnId("turn-1")
            .agentId("risk-scorer")
            .tenantId("tenant-x")
            .startTime(Instant.now()) 
            .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp()) 
            .build(); 

        String result = runPromise(() -> adapter.executeTurn("payload", context)); 

        assertThat(result).isEqualTo("PAYLOAD");
        assertThat(context.getMetadata()).containsEntry("aep.adapter.agentId", "risk-scorer"); 
        assertThat(context.getMetadata()).containsEntry("aep.adapter.input", "payload"); 
        assertThat(context.getMetadata()).containsEntry("aep.adapter.output", "PAYLOAD"); 
    }
}
