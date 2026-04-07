package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.runtime.generators.LLMGenerator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YappcAgentSystem} AI runtime mode enforcement.
 *
 * @doc.type class
 * @doc.purpose Verify explicit AI runtime mode contract for unified agent system boot
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("YappcAgentSystem AI Runtime Mode Tests")
class YappcAgentSystemAiRuntimeModeTest extends EventloopTestBase {

    @Test
    @DisplayName("build rejects missing gateway when AI runtime mode is required")
    void buildRejectsMissingGatewayWhenRequired() {
        MemoryStore memoryStore = mock(MemoryStore.class);

        assertThatThrownBy(() -> YappcAgentSystem.builder()
            .eventloop(eventloop())
                .memoryStore(memoryStore)
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.REQUIRED)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aiRuntimeMode=REQUIRED");
    }

    @Test
    @DisplayName("build allows explicit stub mode without gateway")
    void buildAllowsExplicitStubModeWithoutGateway() {
        MemoryStore memoryStore = mock(MemoryStore.class);

        YappcAgentSystem system = YappcAgentSystem.builder()
            .eventloop(eventloop())
                .memoryStore(memoryStore)
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB)
                .build();

        assertThat(system.getAiRuntimeMode()).isEqualTo(YappcAgentSystem.AiRuntimeMode.STUB);
        assertThat(system.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("build allows required mode when gateway is supplied")
    void buildAllowsRequiredModeWhenGatewaySupplied() {
        MemoryStore memoryStore = mock(MemoryStore.class);
        LLMGenerator.LLMGateway llmGateway = mock(LLMGenerator.LLMGateway.class);

        YappcAgentSystem system = YappcAgentSystem.builder()
            .eventloop(eventloop())
                .memoryStore(memoryStore)
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.REQUIRED)
                .llmGateway(llmGateway)
                .build();

        assertThat(system.getAiRuntimeMode()).isEqualTo(YappcAgentSystem.AiRuntimeMode.REQUIRED);
    }
}