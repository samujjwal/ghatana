/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.dispatch;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.TypedAgent;
import com.ghatana.agent.AgentDescriptor;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.catalog.CatalogRegistry;
import com.ghatana.agent.dispatch.tier.LlmExecutionPlan;
import com.ghatana.agent.dispatch.tier.ServiceOrchestrationPlan;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link CatalogAgentDispatcher} — the three-tier agent dispatcher.
 *
 * <p>Covers Tier-J (Java TypedAgent), Tier-S (Service Orchestration),
 * Tier-L (LLM Execution), and UNRESOLVABLE resolution paths.
 *
 * @doc.type class
 * @doc.purpose Unit tests for CatalogAgentDispatcher
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("CatalogAgentDispatcher")
class CatalogAgentDispatcherTest extends EventloopTestBase {

    @Mock CatalogRegistry catalogRegistry;
    @Mock LlmExecutionPlan llmPlan;
    @Mock ServiceOrchestrationPlan servicePlan;
    @Mock AgentContext agentContext;
    @Mock TypedAgent<String, String> javaAgent;

    private CatalogAgentDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dispatcher = new CatalogAgentDispatcher(catalogRegistry, llmPlan, servicePlan);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Registration
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("registerJavaAgent")
    class Registration {

        @Test
        @DisplayName("should register Tier-J agent and resolve as JAVA_IMPLEMENTED")
        void shouldRegisterAndResolve() {
            dispatcher.registerJavaAgent("agent-1", javaAgent);

            ExecutionTier tier = dispatcher.resolve("agent-1");
            assertThat(tier).isEqualTo(ExecutionTier.JAVA_IMPLEMENTED);
        }

        @Test
        @DisplayName("should overwrite existing Tier-J registration")
        void shouldOverwriteExisting() {
            @SuppressWarnings("unchecked")
            TypedAgent<String, String> replacement = mock(TypedAgent.class);

            dispatcher.registerJavaAgent("agent-1", javaAgent);
            dispatcher.registerJavaAgent("agent-1", replacement);

            // Should dispatch to the replacement, not the original
            AgentResult<String> expectedResult = AgentResult.<String>builder()
                    .output("replaced")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent-1")
                    .build();
            when(replacement.process(any(), any())).thenReturn(Promise.of(expectedResult));

            AgentResult<String> result = runPromise(() -> dispatcher.dispatch("agent-1", "input", agentContext));
            assertThat(result.getOutput()).isEqualTo("replaced");
            verify(javaAgent, never()).process(any(), any());
        }

        @Test
        @DisplayName("should reject null agentId")
        void shouldRejectNullId() {
            assertThatThrownBy(() -> dispatcher.registerJavaAgent(null, javaAgent))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("should reject null agent")
        void shouldRejectNullAgent() {
            assertThatThrownBy(() -> dispatcher.registerJavaAgent("agent-1", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tier-J Dispatch (Java-implemented agents)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tier-J dispatch")
    class TierJDispatch {

        @Test
        @DisplayName("should dispatch to registered Java agent and return result")
        void shouldDispatchToJavaAgent() {
            AgentResult<String> expectedResult = AgentResult.<String>builder()
                    .output("hello")
                    .status(AgentResultStatus.SUCCESS)
                    .confidence(1.0)
                    .agentId("agent-j1")
                    .build();

            when(javaAgent.process(agentContext, "world")).thenReturn(Promise.of(expectedResult));
            dispatcher.registerJavaAgent("agent-j1", javaAgent);

            AgentResult<String> result = runPromise(() -> dispatcher.dispatch("agent-j1", "world", agentContext));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("hello");
            assertThat(result.getAgentId()).isEqualTo("agent-j1");
            verify(javaAgent).process(agentContext, "world");
        }

        @Test
        @DisplayName("should take precedence over catalog entry with same ID")
        void shouldPrioritizeOverCatalog() {
            // Catalog has the same agent ID
            CatalogAgentEntry catalogEntry = CatalogAgentEntry.builder()
                    .id("agent-j1")
                    .name("Catalog Agent")
                    .generator(Map.of("type", "LLM"))
                    .build();
            when(catalogRegistry.findById("agent-j1")).thenReturn(Optional.of(catalogEntry));

            AgentResult<String> expectedResult = AgentResult.<String>builder()
                    .output("java-response")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent-j1")
                    .build();
            when(javaAgent.process(any(), any())).thenReturn(Promise.of(expectedResult));
            dispatcher.registerJavaAgent("agent-j1", javaAgent);

            AgentResult<String> result = runPromise(() -> dispatcher.dispatch("agent-j1", "input", agentContext));

            assertThat(result.getOutput()).isEqualTo("java-response");
            // LLM plan should NOT be called — Java takes precedence
            verify(llmPlan, never()).execute(any(), any(), any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tier-S Dispatch (Service Orchestration)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tier-S dispatch")
    class TierSDispatch {

        @Test
        @DisplayName("should dispatch PIPELINE with delegation to service plan")
        void shouldDispatchPipelineWithDelegation() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-s1")
                    .name("Orchestrated Agent")
                    .generator(Map.of("type", "PIPELINE"))
                    .delegation(Map.of("can_delegate_to", List.of("sub-agent-1", "sub-agent-2")))
                    .build();
            when(catalogRegistry.findById("agent-s1")).thenReturn(Optional.of(entry));

            AgentResult<Object> expectedResult = AgentResult.builder()
                    .output("orchestrated")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent-s1")
                    .build();
            when(servicePlan.execute(eq(entry), any(), eq(agentContext), eq(dispatcher)))
                    .thenReturn(Promise.of(expectedResult));

            AgentResult<Object> result = runPromise(() ->
                    dispatcher.dispatch("agent-s1", "input", agentContext));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("orchestrated");
            verify(servicePlan).execute(eq(entry), any(), eq(agentContext), eq(dispatcher));
            verify(llmPlan, never()).execute(any(), any(), any());
        }

        @Test
        @DisplayName("should dispatch RULE_BASED to service plan")
        void shouldDispatchRuleBased() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-rb")
                    .name("Rule-Based Agent")
                    .generator(Map.of("type", "RULE_BASED"))
                    .build();
            when(catalogRegistry.findById("agent-rb")).thenReturn(Optional.of(entry));

            AgentResult<Object> expectedResult = AgentResult.builder()
                    .output("rule-result")
                    .status(AgentResultStatus.SUCCESS)
                    .agentId("agent-rb")
                    .build();
            when(servicePlan.execute(eq(entry), any(), eq(agentContext), eq(dispatcher)))
                    .thenReturn(Promise.of(expectedResult));

            AgentResult<Object> result = runPromise(() ->
                    dispatcher.dispatch("agent-rb", "input", agentContext));

            assertThat(result.isSuccess()).isTrue();
            verify(servicePlan).execute(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should resolve PIPELINE without delegation and no LLM steps as SERVICE_ORCHESTRATED")
        void shouldResolvePipelineNoDelegationNoLlm() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-pipe")
                    .name("Simple Pipeline")
                    .generator(Map.of("type", "PIPELINE", "steps", List.of(
                            Map.of("type", "TRANSFORM"),
                            Map.of("type", "FILTER")
                    )))
                    .build();
            when(catalogRegistry.findById("agent-pipe")).thenReturn(Optional.of(entry));

            ExecutionTier tier = dispatcher.resolve("agent-pipe");
            assertThat(tier).isEqualTo(ExecutionTier.SERVICE_ORCHESTRATED);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tier-L Dispatch (LLM Execution)
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Tier-L dispatch")
    class TierLDispatch {

        @Test
        @DisplayName("should dispatch LLM generator type to LLM plan")
        void shouldDispatchLlmType() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-l1")
                    .name("LLM Agent")
                    .generator(Map.of("type", "LLM"))
                    .build();
            when(catalogRegistry.findById("agent-l1")).thenReturn(Optional.of(entry));

            AgentResult<Object> expectedResult = AgentResult.builder()
                    .output("llm-response")
                    .status(AgentResultStatus.SUCCESS)
                    .confidence(0.85)
                    .agentId("agent-l1")
                    .build();
            when(llmPlan.execute(eq(entry), any(), eq(agentContext)))
                    .thenReturn(Promise.of(expectedResult));

            AgentResult<Object> result = runPromise(() ->
                    dispatcher.dispatch("agent-l1", "input", agentContext));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("llm-response");
            assertThat(result.getConfidence()).isEqualTo(0.85);
            verify(llmPlan).execute(eq(entry), any(), eq(agentContext));
            verify(servicePlan, never()).execute(any(), any(), any(), any());
        }

        @Test
        @DisplayName("should dispatch PIPELINE with LLM step (no delegation) to LLM plan")
        void shouldDispatchPipelineWithLlmStep() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-l2")
                    .name("Pipeline LLM Agent")
                    .generator(Map.of(
                            "type", "PIPELINE",
                            "steps", List.of(
                                    Map.of("type", "TRANSFORM"),
                                    Map.of("type", "LLM", "model", "gpt-4"),
                                    Map.of("type", "FILTER")
                            )
                    ))
                    .build();
            when(catalogRegistry.findById("agent-l2")).thenReturn(Optional.of(entry));

            ExecutionTier tier = dispatcher.resolve("agent-l2");
            assertThat(tier).isEqualTo(ExecutionTier.LLM_EXECUTED);
        }

        @Test
        @DisplayName("should handle case-insensitive LLM type matching")
        void shouldHandleCaseInsensitiveLlm() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-l3")
                    .name("Lowercase LLM")
                    .generator(Map.of("type", "llm"))
                    .build();
            when(catalogRegistry.findById("agent-l3")).thenReturn(Optional.of(entry));

            ExecutionTier tier = dispatcher.resolve("agent-l3");
            assertThat(tier).isEqualTo(ExecutionTier.LLM_EXECUTED);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // UNRESOLVABLE
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("UNRESOLVABLE dispatch")
    class UnresolvableDispatch {

        @Test
        @DisplayName("should return FAILED result for unknown agent ID")
        void shouldReturnFailedForUnknown() {
            when(catalogRegistry.findById("ghost-agent")).thenReturn(Optional.empty());

            AgentResult<Object> result = runPromise(() ->
                    dispatcher.dispatch("ghost-agent", "input", agentContext));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
            assertThat(result.getConfidence()).isEqualTo(0.0);
            assertThat(result.getExplanation()).contains("ghost-agent");
            assertThat(result.getExplanation()).contains("not found");
        }

        @Test
        @DisplayName("should return UNRESOLVABLE tier for agent with empty generator")
        void shouldResolveUnresolvableForEmptyGenerator() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-empty")
                    .name("Empty Generator Agent")
                    .generator(Map.of())
                    .build();
            when(catalogRegistry.findById("agent-empty")).thenReturn(Optional.of(entry));

            ExecutionTier tier = dispatcher.resolve("agent-empty");
            assertThat(tier).isEqualTo(ExecutionTier.UNRESOLVABLE);
        }

        @Test
        @DisplayName("should return UNRESOLVABLE for unknown generator type")
        void shouldResolveUnresolvableForUnknownType() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-exotic")
                    .name("Exotic Generator Agent")
                    .generator(Map.of("type", "QUANTUM_COMPUTE"))
                    .build();
            when(catalogRegistry.findById("agent-exotic")).thenReturn(Optional.of(entry));

            ExecutionTier tier = dispatcher.resolve("agent-exotic");
            assertThat(tier).isEqualTo(ExecutionTier.UNRESOLVABLE);
        }

        @Test
        @DisplayName("should return UNRESOLVABLE for agent not in registry or catalog")
        void shouldResolveUnresolvableForMissing() {
            when(catalogRegistry.findById("nonexistent")).thenReturn(Optional.empty());

            ExecutionTier tier = dispatcher.resolve("nonexistent");
            assertThat(tier).isEqualTo(ExecutionTier.UNRESOLVABLE);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // resolve() method
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("resolve()")
    class ResolveMethod {

        @Test
        @DisplayName("should resolve SERVICE_CALL as SERVICE_ORCHESTRATED")
        void shouldResolveServiceCall() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-svc")
                    .name("Service Call Agent")
                    .generator(Map.of("type", "SERVICE_CALL"))
                    .build();
            when(catalogRegistry.findById("agent-svc")).thenReturn(Optional.of(entry));

            ExecutionTier tier = dispatcher.resolve("agent-svc");
            assertThat(tier).isEqualTo(ExecutionTier.SERVICE_ORCHESTRATED);
        }

        @Test
        @DisplayName("should resolve Tier-J over catalog match")
        void shouldPrioritizeJavaOverCatalog() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("dual-agent")
                    .name("Both Java and Catalog")
                    .generator(Map.of("type", "LLM"))
                    .build();
            when(catalogRegistry.findById("dual-agent")).thenReturn(Optional.of(entry));
            dispatcher.registerJavaAgent("dual-agent", javaAgent);

            ExecutionTier tier = dispatcher.resolve("dual-agent");
            assertThat(tier).isEqualTo(ExecutionTier.JAVA_IMPLEMENTED);
        }
    }
}
