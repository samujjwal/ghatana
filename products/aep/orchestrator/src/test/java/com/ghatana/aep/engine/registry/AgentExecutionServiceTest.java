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
@DisplayName("AgentExecutionService")
@ExtendWith(MockitoExtension.class) 
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
    void setUp() { 
        service = new AgentExecutionService(agentRegistry, llmGateway, historyStore, memoryClient); 
        lenient().when(historyStore.append(any(), any())).thenReturn(Promise.complete()); 
        lenient().when(memoryClient.recordExecution(any(), any(), any(), any(), anyLong())).thenReturn(Promise.complete()); 
    }

    @Test
    @DisplayName("execute records history and memory after a successful completion")
    void executeRecordsHistoryAndMemory() { 
        when(agentRegistry.resolve("agent-1"))
            .thenReturn(Promise.of(Optional.of(new TestAgent("agent-1"))));
        when(llmGateway.complete(any(CompletionRequest.class))) 
            .thenReturn(Promise.of(CompletionResult.builder() 
                .text("result-text")
                .tokensUsed(42) 
                .build())); 

        AgentExecutionService.ExecutionResult result =
            runPromise(() -> service.execute("agent-1", Map.of("message", "hello"))); 

        assertThat(result.status()).isEqualTo("success");
        assertThat(result.output()).isEqualTo("result-text");

        ArgumentCaptor<AgentExecutionService.ExecutionRecord> recordCaptor =
            ArgumentCaptor.forClass(AgentExecutionService.ExecutionRecord.class); 
        verify(historyStore).append(eq("agent-1"), recordCaptor.capture());
        verify(memoryClient).recordExecution(eq("agent-1"), eq(result.executionId()),
            eq(Map.of("message", "hello")), eq("result-text"), eq(result.durationMs()));

        AgentExecutionService.ExecutionRecord record = recordCaptor.getValue(); 
        assertThat(record.status()).isEqualTo("success");
        assertThat(record.output()).isEqualTo("result-text");
    }

    @Test
    @DisplayName("getHistory delegates to the configured history store")
    void getHistoryDelegates() { 
        List<AgentExecutionService.ExecutionRecord> records = List.of( 
            new AgentExecutionService.ExecutionRecord("exec-1", "success", "in", "out", 12L, "2026-04-15T00:00:00Z")); 
        when(historyStore.getHistory("agent-9", 25)).thenReturn(Promise.of(records)); 

        List<AgentExecutionService.ExecutionRecord> result =
            runPromise(() -> service.getHistory("agent-9", 25)); 

        assertThat(result).containsExactlyElementsOf(records); 
    }

    @Test
    @DisplayName("getMemory delegates to the configured memory client")
    void getMemoryDelegates() { 
        AgentExecutionService.AgentMemory memory = new AgentExecutionService.AgentMemory( 
            List.of(Map.of("id", "ep-1")), 
            Map.of("count", 1), 
            Map.of("count", 0), 
            "2026-04-15T00:00:00Z");
        when(memoryClient.getMemory("agent-9")).thenReturn(Promise.of(memory));

        AgentExecutionService.AgentMemory result =
            runPromise(() -> service.getMemory("agent-9"));

        assertThat(result).isEqualTo(memory); 
    }

    @Test
    @DisplayName("execute rejects registry entries marked as discovery-only")
    void executeRejectsDiscoveryOnlyAgents() { 
        when(agentRegistry.resolve("agent-placeholder"))
            .thenReturn(Promise.of(Optional.of(new TestAgent( 
                AgentDescriptor.builder() 
                    .agentId("agent-placeholder")
                    .name("Placeholder Agent")
                    .type(AgentType.PROBABILISTIC) 
                    .metadata(Map.of( 
                        "executable", false,
                        "registrationMode", "manifest-only"
                    ))
                    .build() 
            ))));

        assertThatThrownBy(() -> runPromise(() -> service.execute("agent-placeholder", Map.of("message", "hello")))) 
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("cannot be executed");

        verify(llmGateway, never()).complete(any(CompletionRequest.class)); 
    }

    private static final class TestAgent implements TypedAgent<Object, Object> {

        private final AgentDescriptor descriptor;

        private TestAgent(String agentId) { 
            this(AgentDescriptor.builder() 
                .agentId(agentId) 
                .name("Test Agent")
                .type(AgentType.PROBABILISTIC) 
                .build()); 
        }

        private TestAgent(AgentDescriptor descriptor) { 
            this.descriptor = descriptor;
        }

        @Override
        public @NotNull AgentDescriptor descriptor() { 
            return descriptor;
        }

        @Override
        public @NotNull Promise<Void> initialize(@NotNull com.ghatana.agent.AgentConfig config) { 
            return Promise.complete(); 
        }

        @Override
        public @NotNull Promise<Void> shutdown() { 
            return Promise.complete(); 
        }

        @Override
        public @NotNull Promise<HealthStatus> healthCheck() { 
            return Promise.of(HealthStatus.healthy("ok"));
        }

        @Override
        public @NotNull Promise<AgentResult<Object>> process(@NotNull AgentContext ctx, @NotNull Object input) { 
            return Promise.of(AgentResult.success(input, descriptor.getAgentId(), java.time.Duration.ZERO)); 
        }
    }
}
