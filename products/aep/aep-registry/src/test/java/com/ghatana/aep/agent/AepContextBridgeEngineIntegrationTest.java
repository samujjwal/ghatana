package com.ghatana.aep.agent;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AepContextBridge engine integration [GH-90000]")
class AepContextBridgeEngineIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("shareToAep mirrors context into the engine event cloud and caches it locally [GH-90000]")
    void shareToAepMirrorsContextIntoEventCloud() { // GH-90000
        EventCloud eventCloud = mock(EventCloud.class); // GH-90000
        AepEngine engine = mock(AepEngine.class); // GH-90000
        when(engine.eventCloud()).thenReturn(eventCloud); // GH-90000
        AepContextBridge bridge = new AepContextBridge(MemoryStore.noOp(), engine); // GH-90000

        runPromise(bridge::activate); // GH-90000
        runPromise(() -> bridge.shareToAep("tenant-1", "context-payload")); // GH-90000
        String shared = runPromise(() -> bridge.getFromAep("tenant-1 [GH-90000]"));

        assertThat(shared).isEqualTo("context-payload [GH-90000]");
        verify(eventCloud).append( // GH-90000
            org.mockito.ArgumentMatchers.eq("tenant-1 [GH-90000]"),
            org.mockito.ArgumentMatchers.eq(AepContextBridge.CONTEXT_EVENT_TYPE), // GH-90000
            argThat(bytes -> Arrays.equals(bytes, "context-payload".getBytes(StandardCharsets.UTF_8))) // GH-90000
        );
    }

    @Test
    @DisplayName("toAgentContext propagates tenant trace and metadata from product execution context [GH-90000]")
    void toAgentContextPropagatesExecutionMetadata() { // GH-90000
        AepContextBridge bridge = new AepContextBridge(MemoryStore.noOp()); // GH-90000
        com.ghatana.aep.domain.agent.registry.AgentExecutionContext executionContext =
            new com.ghatana.aep.domain.agent.registry.AgentExecutionContext() { // GH-90000
                @Override
                public String tenantId() { return "tenant-7"; } // GH-90000

                @Override
                public String userId() { return "user-9"; } // GH-90000

                @Override
                public com.ghatana.aep.domain.agent.registry.SecurityContext securityContext() { return null; } // GH-90000

                @Override
                public String correlationId() { return "trace-123"; } // GH-90000

                @Override
                public java.util.Map<String, Object> metadata() { return java.util.Map.of("source", "agent-runtime"); } // GH-90000

                @Override
                public java.util.Set<String> enabledCapabilities() { return java.util.Set.of("read [GH-90000]"); }

                @Override
                public long timeoutMs() { return 1_000L; } // GH-90000

                @Override
                public long createdAt() { return System.currentTimeMillis(); } // GH-90000

                @Override
                public com.ghatana.aep.domain.agent.registry.AgentExecutionContext copy() { return this; } // GH-90000
            };

        AgentContext agentContext = bridge.toAgentContext(executionContext, "fraud-detector"); // GH-90000

        assertThat(agentContext.getTenantId()).isEqualTo("tenant-7 [GH-90000]");
        assertThat(agentContext.getUserId()).isEqualTo("user-9 [GH-90000]");
        assertThat(agentContext.getTraceId()).isEqualTo("trace-123 [GH-90000]");
        assertThat(agentContext.getLogger().getName()).isEqualTo("agent.fraud-detector [GH-90000]");
        assertThat(agentContext.getMetadata()).containsEntry("source", "agent-runtime"); // GH-90000
    }
}
