package com.ghatana.aep.engine.registry;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.platform.health.HealthStatus;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentExecutionService}.
 *
 * @doc.type class
 * @doc.purpose Verify execution history and memory persistence integration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentExecutionService [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AgentExecutionServiceTest extends EventloopTestBase {

    @Mock
    private AgentRegistry agentRegistry;

    @Mock
    private LLMGateway llmGateway;

    @Mock
    private AgentExecutionHistoryStore historyStore;

    @Mock
    private AgentMemoryPlaneClient memoryClient;

    private AgentExecutionService service;

    @BeforeEach
    void setUp() { // GH-90000
        service = new AgentExecutionService(agentRegistry, llmGateway, historyStore, memoryClient); // GH-90000
        lenient().when(historyStore.append(any(), any())).thenReturn(Promise.complete()); // GH-90000
        lenient().when(memoryClient.recordExecution(any(), any(), any(), any(), anyLong())).thenReturn(Promise.complete()); // GH-90000
    }

    @Test
    @DisplayName("execute records history and memory after a successful completion [GH-90000]")
    void executeRecordsHistoryAndMemory() { // GH-90000
        when(agentRegistry.resolve("agent-1 [GH-90000]"))
            .thenReturn(Promise.of(Optional.of(new TestAgent("agent-1 [GH-90000]"))));
        when(llmGateway.complete(any(CompletionRequest.class))) // GH-90000
            .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                .text("result-text [GH-90000]")
                .tokensUsed(42) // GH-90000
                .build())); // GH-90000

        AgentExecutionService.ExecutionResult result =
            runPromise(() -> service.execute("agent-1", Map.of("message", "hello"))); // GH-90000

        assertThat(result.status()).isEqualTo("success [GH-90000]");
        assertThat(result.output()).isEqualTo("result-text [GH-90000]");

        ArgumentCaptor<AgentExecutionService.ExecutionRecord> recordCaptor =
            ArgumentCaptor.forClass(AgentExecutionService.ExecutionRecord.class); // GH-90000
        verify(historyStore).append(eq("agent-1 [GH-90000]"), recordCaptor.capture());
        verify(memoryClient).recordExecution(eq("agent-1 [GH-90000]"), eq(result.executionId()),
            eq(Map.of("message", "hello")), eq("result-text [GH-90000]"), eq(result.durationMs()));

        AgentExecutionService.ExecutionRecord record = recordCaptor.getValue(); // GH-90000
        assertThat(record.status()).isEqualTo("success [GH-90000]");
        assertThat(record.output()).isEqualTo("result-text [GH-90000]");
    }

    @Test
    @DisplayName("getHistory delegates to the configured history store [GH-90000]")
    void getHistoryDelegates() { // GH-90000
        List<AgentExecutionService.ExecutionRecord> records = List.of( // GH-90000
            new AgentExecutionService.ExecutionRecord("exec-1", "success", "in", "out", 12L, "2026-04-15T00:00:00Z")); // GH-90000
        when(historyStore.getHistory("agent-9", 25)).thenReturn(Promise.of(records)); // GH-90000

        List<AgentExecutionService.ExecutionRecord> result =
            runPromise(() -> service.getHistory("agent-9", 25)); // GH-90000

        assertThat(result).containsExactlyElementsOf(records); // GH-90000
    }

    @Test
    @DisplayName("getMemory delegates to the configured memory client [GH-90000]")
    void getMemoryDelegates() { // GH-90000
        AgentExecutionService.AgentMemory memory = new AgentExecutionService.AgentMemory( // GH-90000
            List.of(Map.of("id", "ep-1")), // GH-90000
            Map.of("count", 1), // GH-90000
            Map.of("count", 0), // GH-90000
            "2026-04-15T00:00:00Z");
        when(memoryClient.getMemory("agent-9 [GH-90000]")).thenReturn(Promise.of(memory));

        AgentExecutionService.AgentMemory result =
            runPromise(() -> service.getMemory("agent-9 [GH-90000]"));

        assertThat(result).isEqualTo(memory); // GH-90000
    }

    @Test
    @DisplayName("execute rejects registry entries marked as discovery-only [GH-90000]")
    void executeRejectsDiscoveryOnlyAgents() { // GH-90000
        when(agentRegistry.resolve("agent-placeholder [GH-90000]"))
            .thenReturn(Promise.of(Optional.of(new TestAgent( // GH-90000
                AgentDescriptor.builder() // GH-90000
                    .agentId("agent-placeholder [GH-90000]")
                    .name("Placeholder Agent [GH-90000]")
                    .type(AgentType.PROBABILISTIC) // GH-90000
                    .metadata(Map.of( // GH-90000
                        "executable", false,
                        "registrationMode", "manifest-only"
                    ))
                    .build() // GH-90000
            ))));

        assertThatThrownBy(() -> runPromise(() -> service.execute("agent-placeholder", Map.of("message", "hello")))) // GH-90000
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("cannot be executed [GH-90000]");

        verify(llmGateway, never()).complete(any(CompletionRequest.class)); // GH-90000
    }

    private static final class TestAgent implements TypedAgent<Object, Object> {

        private final AgentDescriptor descriptor;

        private TestAgent(String agentId) { // GH-90000
            this(AgentDescriptor.builder() // GH-90000
                .agentId(agentId) // GH-90000
                .name("Test Agent [GH-90000]")
                .type(AgentType.PROBABILISTIC) // GH-90000
                .build()); // GH-90000
        }

        private TestAgent(AgentDescriptor descriptor) { // GH-90000
            this.descriptor = descriptor;
        }

        @Override
        public @NotNull AgentDescriptor descriptor() { // GH-90000
            return descriptor;
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull com.ghatana.agent.AgentConfig config) { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public @NotNull Promise<Void> shutdown() { // GH-90000
            return Promise.complete(); // GH-90000
        }

        @Override
        public @NotNull Promise<HealthStatus> healthCheck() { // GH-90000
            return Promise.of(HealthStatus.healthy("ok [GH-90000]"));
        }

        @Override
        public @NotNull Promise<AgentResult<Object>> process(@NotNull AgentContext ctx, @NotNull Object input) { // GH-90000
            return Promise.of(AgentResult.success(input, descriptor.getAgentId(), java.time.Duration.ZERO)); // GH-90000
        }
    }
}
