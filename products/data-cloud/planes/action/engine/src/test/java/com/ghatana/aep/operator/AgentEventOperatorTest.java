package com.ghatana.aep.operator;

import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for AgentEventOperator hardening (AEP-003).
 * Validates that the agent-as-event-operator path includes production truth binding,
 * environment validation, input validation, and agent context validation.
 *
 * @doc.type class
 * @doc.purpose Validates AgentEventOperator hardening features
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("AgentEventOperator Hardening Tests")
class AgentEventOperatorTest {

    private TypedAgent<Map<String, Object>, Map<String, Object>> mockAgent;
    private AgentDescriptor mockDescriptor;
    private AgentEventOperator operator;
    private AgentContext mockContext;

    @BeforeEach
    void setUp() {
        mockAgent = mock(TypedAgent.class);
        mockDescriptor = mock(AgentDescriptor.class);
        mockContext = mock(AgentContext.class);

        when(mockAgent.descriptor()).thenReturn(mockDescriptor);
        when(mockDescriptor.getAgentId()).thenReturn("test-agent");
        when(mockContext.getTenantId()).thenReturn("tenant-1");

        operator = new AgentEventOperator(mockAgent);
    }

    @Nested
    @DisplayName("Agent Context Validation")
    class AgentContextValidation {

        @Test
        @DisplayName("rejects null agent context")
        void rejectsNullAgentContext() {
            Map<String, Object> event = Map.of("key", "value");

            assertThatThrownBy(() -> 
                operator.submit(null, event)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("AgentContext must not be null");
        }

        @Test
        @DisplayName("rejects agent context with null tenant ID")
        void rejectsNullTenantId() {
            when(mockContext.getTenantId()).thenReturn(null);
            Map<String, Object> event = Map.of("key", "value");

            assertThatThrownBy(() -> 
                operator.submit(mockContext, event)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("AgentContext tenantId must not be null or empty");
        }

        @Test
        @DisplayName("rejects agent context with empty tenant ID")
        void rejectsEmptyTenantId() {
            when(mockContext.getTenantId()).thenReturn("");
            Map<String, Object> event = Map.of("key", "value");

            assertThatThrownBy(() -> 
                operator.submit(mockContext, event)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("AgentContext tenantId must not be null or empty");
        }

        @Test
        @DisplayName("accepts valid agent context")
        void acceptsValidAgentContext() {
            when(mockContext.getTenantId()).thenReturn("tenant-1");
            Map<String, Object> event = Map.of("key", "value");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(Map.of("result", "success"));
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThat(promise).isNotNull();
        }
    }

    @Nested
    @DisplayName("Event Structure Validation")
    class EventStructureValidation {

        @Test
        @DisplayName("rejects null event")
        void rejectsNullEvent() {
            assertThatThrownBy(() -> 
                operator.submit(mockContext, null)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Event must not be null");
        }

        @Test
        @DisplayName("rejects empty event")
        void rejectsEmptyEvent() {
            Map<String, Object> event = Map.of();

            assertThatThrownBy(() -> 
                operator.submit(mockContext, event)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Event must not be empty");
        }

        @Test
        @DisplayName("accepts non-empty event")
        void acceptsNonEmptyEvent() {
            Map<String, Object> event = Map.of("key", "value");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(Map.of("result", "success"));
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThat(promise).isNotNull();
        }
    }

    @Nested
    @DisplayName("Commit SHA Binding (AEP-003)")
    class CommitShaBinding {

        @Test
        @DisplayName("production environment requires commit SHA")
        void productionRequiresCommitSha() {
            operator.setEnvironment("production");
            Map<String, Object> event = Map.of("key", "value");

            assertThatThrownBy(() -> 
                operator.submit(mockContext, event)
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Commit SHA must be set for production environment");
        }

        @Test
        @DisplayName("production environment accepts valid commit SHA")
        void productionAcceptsValidCommitSha() {
            operator.setEnvironment("production");
            operator.setCommitSha("7f84bc08e9e4e6d7e209cb49a855f199f7c90347");
            Map<String, Object> event = Map.of("key", "value");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(Map.of("result", "success"));
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThat(promise).isNotNull();
        }

        @Test
        @DisplayName("rejects invalid commit SHA format")
        void rejectsInvalidCommitShaFormat() {
            operator.setCommitSha("invalid-sha");
            Map<String, Object> event = Map.of("key", "value");

            assertThatThrownBy(() -> 
                operator.submit(mockContext, event)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Invalid commit SHA format");
        }

        @Test
        @DisplayName("non-production environment does not require commit SHA")
        void nonProductionDoesNotRequireCommitSha() {
            operator.setEnvironment("development");
            Map<String, Object> event = Map.of("key", "value");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(Map.of("result", "success"));
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThat(promise).isNotNull();
        }

        @Test
        @DisplayName("no environment set does not require commit SHA")
        void noEnvironmentDoesNotRequireCommitSha() {
            Map<String, Object> event = Map.of("key", "value");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(Map.of("result", "success"));
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThat(promise).isNotNull();
        }
    }

    @Nested
    @DisplayName("Agent Execution")
    class AgentExecution {

        @Test
        @DisplayName("delegates to agent process method")
        void delegatesToAgentProcess() {
            Map<String, Object> event = Map.of("key", "value");
            Map<String, Object> output = Map.of("result", "success");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(output);
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            verify(mockAgent).process(mockContext, event);
        }

        @Test
        @DisplayName("returns agent output on success")
        void returnsAgentOutputOnSuccess() {
            Map<String, Object> event = Map.of("key", "value");
            Map<String, Object> output = Map.of("result", "success");
            AgentResult<Map<String, Object>> result = mock(AgentResult.class);
            when(result.getOutput()).thenReturn(output);
            when(mockAgent.process(any(), any())).thenReturn(Promise.of(result));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThat(promise.getResult()).isEqualTo(output);
        }

        @Test
        @DisplayName("propagates agent failure")
        void propagatesAgentFailure() {
            Map<String, Object> event = Map.of("key", "value");
            RuntimeException error = new RuntimeException("Agent failed");
            when(mockAgent.process(any(), any())).thenReturn(Promise.ofException(error));

            Promise<Map<String, Object>> promise = operator.submit(mockContext, event);

            assertThatThrownBy(() -> promise.getResult())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Agent failed");
        }
    }

    @Nested
    @DisplayName("Agent ID")
    class AgentId {

        @Test
        @DisplayName("returns agent ID from descriptor")
        void returnsAgentIdFromDescriptor() {
            when(mockDescriptor.getAgentId()).thenReturn("my-agent");

            String agentId = operator.getAgentId();

            assertThat(agentId).isEqualTo("my-agent");
        }
    }
}
