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
@DisplayName("YappcAgentSystem AI Runtime Mode Tests [GH-90000]")
class YappcAgentSystemAiRuntimeModeTest extends EventloopTestBase {

    @Test
    @DisplayName("build rejects missing gateway when AI runtime mode is required [GH-90000]")
    void buildRejectsMissingGatewayWhenRequired() { // GH-90000
        MemoryStore memoryStore = mock(MemoryStore.class); // GH-90000

        assertThatThrownBy(() -> YappcAgentSystem.builder() // GH-90000
            .eventloop(eventloop()) // GH-90000
                .memoryStore(memoryStore) // GH-90000
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.REQUIRED) // GH-90000
                .build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("aiRuntimeMode=REQUIRED [GH-90000]");
    }

    @Test
    @DisplayName("build allows explicit stub mode without gateway [GH-90000]")
    void buildAllowsExplicitStubModeWithoutGateway() { // GH-90000
        MemoryStore memoryStore = mock(MemoryStore.class); // GH-90000

        YappcAgentSystem system = YappcAgentSystem.builder() // GH-90000
            .eventloop(eventloop()) // GH-90000
                .memoryStore(memoryStore) // GH-90000
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.STUB) // GH-90000
                .build(); // GH-90000

        assertThat(system.getAiRuntimeMode()).isEqualTo(YappcAgentSystem.AiRuntimeMode.STUB); // GH-90000
        assertThat(system.isInitialized()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("build allows required mode when gateway is supplied [GH-90000]")
    void buildAllowsRequiredModeWhenGatewaySupplied() { // GH-90000
        MemoryStore memoryStore = mock(MemoryStore.class); // GH-90000
        LLMGenerator.LLMGateway llmGateway = mock(LLMGenerator.LLMGateway.class); // GH-90000

        YappcAgentSystem system = YappcAgentSystem.builder() // GH-90000
            .eventloop(eventloop()) // GH-90000
                .memoryStore(memoryStore) // GH-90000
                .aiRuntimeMode(YappcAgentSystem.AiRuntimeMode.REQUIRED) // GH-90000
                .llmGateway(llmGateway) // GH-90000
                .build(); // GH-90000

        assertThat(system.getAiRuntimeMode()).isEqualTo(YappcAgentSystem.AiRuntimeMode.REQUIRED); // GH-90000
    }
}
