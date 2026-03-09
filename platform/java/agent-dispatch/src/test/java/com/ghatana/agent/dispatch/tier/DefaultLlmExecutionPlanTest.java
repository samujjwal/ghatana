/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.agent.dispatch.tier;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link DefaultLlmExecutionPlan} — Tier-L execution via LLM prompt.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DefaultLlmExecutionPlan
 * @doc.layer framework
 * @doc.pattern Test
 */
@DisplayName("DefaultLlmExecutionPlan")
class DefaultLlmExecutionPlanTest extends EventloopTestBase {

    @Mock LlmProvider llmProvider;
    @Mock AgentContext agentContext;

    private DefaultLlmExecutionPlan plan;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        plan = new DefaultLlmExecutionPlan(llmProvider);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Successful LLM execution
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Successful execution")
    class SuccessfulExecution {

        @Test
        @DisplayName("should invoke LLM provider with correct params and return SUCCESS")
        void shouldInvokeLlmAndReturnSuccess() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-llm")
                    .name("Test LLM Agent")
                    .generator(Map.of(
                            "type", "PIPELINE",
                            "steps", List.of(Map.of(
                                    "type", "LLM",
                                    "provider", "OPENAI",
                                    "model", "gpt-4",
                                    "prompt", "Analyze: {{input}}",
                                    "temperature", 0.3,
                                    "max_tokens", 1500
                            ))
                    ))
                    .build();

            when(llmProvider.invoke(eq("OPENAI"), eq("gpt-4"), anyString(), eq(0.3), eq(1500)))
                    .thenReturn(Promise.of((Object) "LLM generated output"));

            AgentResult<Object> result = runPromise(() -> plan.execute(entry, "test input", agentContext));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).isEqualTo("LLM generated output");
            assertThat(result.getConfidence()).isEqualTo(0.85);
            assertThat(result.getAgentId()).isEqualTo("agent-llm");
            assertThat(result.getExplanation()).contains("OPENAI/gpt-4");
            assertThat(result.getProcessingTime()).isNotNull();
        }

        @Test
        @DisplayName("should substitute prompt variables correctly")
        void shouldSubstitutePromptVariables() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-prompt")
                    .name("Prompt Test Agent")
                    .generator(Map.of(
                            "type", "PIPELINE",
                            "steps", List.of(Map.of(
                                    "type", "LLM",
                                    "provider", "ANTHROPIC",
                                    "model", "claude-3",
                                    "prompt", "Agent {{agent_id}} ({{agent_name}}): Analyze {{input}}"
                            ))
                    ))
                    .build();

            when(llmProvider.invoke(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Promise.of((Object) "response"));

            runPromise(() -> plan.execute(entry, "user data", agentContext));

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmProvider).invoke(eq("ANTHROPIC"), eq("claude-3"), promptCaptor.capture(), anyDouble(), anyInt());

            String prompt = promptCaptor.getValue();
            assertThat(prompt).contains("agent-prompt");
            assertThat(prompt).contains("Prompt Test Agent");
            assertThat(prompt).contains("user data");
            assertThat(prompt).doesNotContain("{{agent_id}}");
            assertThat(prompt).doesNotContain("{{agent_name}}");
            assertThat(prompt).doesNotContain("{{input}}");
        }

        @Test
        @DisplayName("should use default provider/model when not specified in step")
        void shouldUseDefaults() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-default")
                    .name("Default Config Agent")
                    .generator(Map.of(
                            "type", "PIPELINE",
                            "steps", List.of(Map.of("type", "LLM"))
                    ))
                    .build();

            when(llmProvider.invoke(eq("ANTHROPIC"), eq("claude-3-5-sonnet-20241022"), anyString(), eq(0.2), eq(2000)))
                    .thenReturn(Promise.of((Object) "default response"));

            AgentResult<Object> result = runPromise(() -> plan.execute(entry, "input", agentContext));

            assertThat(result.isSuccess()).isTrue();
            verify(llmProvider).invoke("ANTHROPIC", "claude-3-5-sonnet-20241022", "", 0.2, 2000);
        }

        @Test
        @DisplayName("should include metrics with tier, provider, and model")
        void shouldIncludeMetrics() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-metrics")
                    .name("Metrics Agent")
                    .generator(Map.of(
                            "type", "PIPELINE",
                            "steps", List.of(Map.of(
                                    "type", "LLM",
                                    "provider", "COHERE",
                                    "model", "command-r"
                            ))
                    ))
                    .build();

            when(llmProvider.invoke(anyString(), anyString(), anyString(), anyDouble(), anyInt()))
                    .thenReturn(Promise.of((Object) "response"));

            AgentResult<Object> result = runPromise(() -> plan.execute(entry, "input", agentContext));

            assertThat(result.getMetrics())
                    .containsEntry("tier", "LLM_EXECUTED")
                    .containsEntry("provider", "COHERE")
                    .containsEntry("model", "command-r");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Failure paths
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Failure paths")
    class FailurePaths {

        @Test
        @DisplayName("should return FAILED when no LLM step in generator")
        void shouldFailWhenNoLlmStep() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-no-llm")
                    .name("No LLM Agent")
                    .generator(Map.of(
                            "type", "PIPELINE",
                            "steps", List.of(
                                    Map.of("type", "TRANSFORM"),
                                    Map.of("type", "FILTER")
                            )
                    ))
                    .build();

            AgentResult<Object> result = runPromise(() -> plan.execute(entry, "input", agentContext));

            assertThat(result.isFailed()).isTrue();
            assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED);
            assertThat(result.getConfidence()).isEqualTo(0.0);
            assertThat(result.getExplanation()).contains("No LLM step");
            verify(llmProvider, never()).invoke(anyString(), anyString(), anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("should return FAILED when generator has no steps")
        void shouldFailWhenNoSteps() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-no-steps")
                    .name("No Steps Agent")
                    .generator(Map.of("type", "PIPELINE"))
                    .build();

            AgentResult<Object> result = runPromise(() -> plan.execute(entry, "input", agentContext));

            assertThat(result.isFailed()).isTrue();
            verify(llmProvider, never()).invoke(anyString(), anyString(), anyString(), anyDouble(), anyInt());
        }

        @Test
        @DisplayName("should return FAILED when generator is null")
        void shouldFailWhenNullGenerator() {
            CatalogAgentEntry entry = CatalogAgentEntry.builder()
                    .id("agent-null-gen")
                    .name("Null Generator Agent")
                    .build();

            AgentResult<Object> result = runPromise(() -> plan.execute(entry, "input", agentContext));

            assertThat(result.isFailed()).isTrue();
        }
    }
}
