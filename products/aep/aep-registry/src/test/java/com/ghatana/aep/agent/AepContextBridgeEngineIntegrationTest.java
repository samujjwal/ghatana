package com.ghatana.aep.agent;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AepContextBridge engine integration")
class AepContextBridgeEngineIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("shareToAep mirrors context into the engine event cloud and caches it locally")
    void shareToAepMirrorsContextIntoEventCloud() {
        EventCloud eventCloud = mock(EventCloud.class);
        AepEngine engine = mock(AepEngine.class);
        when(engine.eventCloud()).thenReturn(eventCloud);
        AepContextBridge bridge = new AepContextBridge(MemoryStore.noOp(), engine);

        runPromise(bridge::activate);
        runPromise(() -> bridge.shareToAep("tenant-1", "context-payload"));
        String shared = runPromise(() -> bridge.getFromAep("tenant-1"));

        assertThat(shared).isEqualTo("context-payload");
        verify(eventCloud).append(
            org.mockito.ArgumentMatchers.eq("tenant-1"),
            org.mockito.ArgumentMatchers.eq(AepContextBridge.CONTEXT_EVENT_TYPE),
            argThat(bytes -> Arrays.equals(bytes, "context-payload".getBytes(StandardCharsets.UTF_8)))
        );
    }

    @Test
    @DisplayName("toAgentContext propagates tenant trace and metadata from product execution context")
    void toAgentContextPropagatesExecutionMetadata() {
        AepContextBridge bridge = new AepContextBridge(MemoryStore.noOp());
        com.ghatana.aep.domain.agent.registry.AgentExecutionContext executionContext =
            new com.ghatana.aep.domain.agent.registry.AgentExecutionContext() {
                @Override
                public String tenantId() { return "tenant-7"; }

                @Override
                public String userId() { return "user-9"; }

                @Override
                public com.ghatana.aep.domain.agent.registry.SecurityContext securityContext() { return null; }

                @Override
                public String correlationId() { return "trace-123"; }

                @Override
                public java.util.Map<String, Object> metadata() { return java.util.Map.of("source", "agent-runtime"); }

                @Override
                public java.util.Set<String> enabledCapabilities() { return java.util.Set.of("read"); }

                @Override
                public long timeoutMs() { return 1_000L; }

                @Override
                public long createdAt() { return System.currentTimeMillis(); }

                @Override
                public com.ghatana.aep.domain.agent.registry.AgentExecutionContext copy() { return this; }
            };

        AgentContext agentContext = bridge.toAgentContext(executionContext, "fraud-detector");

        assertThat(agentContext.getTenantId()).isEqualTo("tenant-7");
        assertThat(agentContext.getUserId()).isEqualTo("user-9");
        assertThat(agentContext.getTraceId()).isEqualTo("trace-123");
        assertThat(agentContext.getLogger().getName()).isEqualTo("agent.fraud-detector");
        assertThat(agentContext.getMetadata()).containsEntry("source", "agent-runtime");
    }
}