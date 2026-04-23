package com.ghatana.agent.dispatch.tier;

import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.catalog.CatalogAgentEntry;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.DefaultAgentContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @doc.type class
 * @doc.purpose Verify Tier-L prompt templating and failure handling
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DefaultLlmExecutionPlan")
@ExtendWith(MockitoExtension.class) // GH-90000
class DefaultLlmExecutionPlanTest extends EventloopTestBase {

    @Mock
    private LlmProvider llmProvider;

    @Test
    @DisplayName("execute hydrates the prompt and forwards provider and model settings")
    void executeHydratesPromptAndForwardsRoutingSettings() { // GH-90000
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); // GH-90000
        CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
            .id("fraud-triage")
            .name("Fraud Triage")
            .generator(Map.of( // GH-90000
                "steps", List.of(Map.of( // GH-90000
                    "type", "LLM",
                    "provider", "OPENAI",
                    "model", "gpt-4o-mini",
                    "prompt", "Agent {{agent_name}}/{{agent_id}} sees {{input}} within {{context}}",
                    "temperature", 0.35,
                    "max_tokens", 512))))
            .build(); // GH-90000
        AgentContext context = AgentContext.builder() // GH-90000
            .turnId("turn-1")
            .agentId("fraud-triage")
            .tenantId("tenant-a")
            .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp()) // GH-90000
            .build(); // GH-90000

        when(llmProvider.invoke( // GH-90000
                "OPENAI",
                "gpt-4o-mini",
                "Agent Fraud Triage/fraud-triage sees suspicious payment within " + context,
                0.35,
                512,
                context))
            .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                .text("triage-result")
                .modelUsed("gpt-4o-mini")
                .promptTokens(120) // GH-90000
                .completionTokens(60) // GH-90000
                .tokensUsed(180) // GH-90000
                .latencyMs(240) // GH-90000
                .build())); // GH-90000

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "suspicious payment", context)); // GH-90000

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        assertThat(result.getOutput()).isEqualTo("triage-result");
        assertThat(result.getMetrics()) // GH-90000
            .containsEntry("tier", "LLM_EXECUTED") // GH-90000
            .containsEntry("provider", "OPENAI") // GH-90000
            .containsEntry("model", "gpt-4o-mini") // GH-90000
            .containsEntry("prompt_tokens", 120) // GH-90000
            .containsEntry("completion_tokens", 60) // GH-90000
            .containsEntry("total_tokens", 180); // GH-90000
    }

    @Test
    @DisplayName("execute uses the default provider and token limit when optional values are omitted")
    void executeUsesDefaultProviderAndTokenLimit() { // GH-90000
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); // GH-90000
        CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
            .id("summary-agent")
            .name("Summary Agent")
            .generator(Map.of( // GH-90000
                "steps", List.of(Map.of( // GH-90000
                    "type", "LLM",
                    "model", "claude-3-5-sonnet-20241022",
                    "prompt", "Summarize {{input}} for {{agent_id}}"))))
            .build(); // GH-90000
        AgentContext context = AgentContext.empty(); // GH-90000

        when(llmProvider.invoke( // GH-90000
                "ANTHROPIC",
                "claude-3-5-sonnet-20241022",
                "Summarize inbox backlog for summary-agent",
                0.2,
                2000,
                context))
            .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                .text("summary")
                .modelUsed("claude-3-5-sonnet-20241022")
                .promptTokens(40) // GH-90000
                .completionTokens(20) // GH-90000
                .tokensUsed(60) // GH-90000
                .latencyMs(120) // GH-90000
                .build())); // GH-90000

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "inbox backlog", context)); // GH-90000

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        assertThat(result.getExplanation()).contains("ANTHROPIC/claude-3-5-sonnet-20241022");
    }

    @Test
    @DisplayName("execute records token metrics and deducts cost from the agent budget")
    void executeRecordsTokenMetricsAndDeductsCost() { // GH-90000
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); // GH-90000
        CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
            .id("budget-agent")
            .name("Budget Agent")
            .generator(Map.of( // GH-90000
                "steps", List.of(Map.of( // GH-90000
                    "type", "LLM",
                    "provider", "OPENAI",
                    "model", "gpt-4o-mini",
                    "prompt", "Assess {{input}}"))))
            .build(); // GH-90000
        DefaultAgentContext context = (DefaultAgentContext) AgentContext.builder() // GH-90000
            .turnId("turn-budget")
            .agentId("budget-agent")
            .tenantId("tenant-budget")
            .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp()) // GH-90000
            .remainingBudget(5.0) // GH-90000
            .addConfig("llm.cost.inputPer1kUsd", 0.02) // GH-90000
            .addConfig("llm.cost.outputPer1kUsd", 0.04) // GH-90000
            .build(); // GH-90000

        when(llmProvider.invoke( // GH-90000
                "OPENAI",
                "gpt-4o-mini",
                "Assess risky transfer",
                0.2,
                2000,
                context))
            .thenReturn(Promise.of(CompletionResult.builder() // GH-90000
                .text("approved")
                .modelUsed("gpt-4o-mini")
                .promptTokens(100) // GH-90000
                .completionTokens(50) // GH-90000
                .tokensUsed(150) // GH-90000
                .latencyMs(321) // GH-90000
                .build())); // GH-90000

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "risky transfer", context)); // GH-90000

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); // GH-90000
        assertThat(result.getMetrics()).containsEntry("cost_usd", 0.004d); // GH-90000
        assertThat(context.getMetrics()) // GH-90000
            .containsEntry("llm.cost", 0.004d) // GH-90000
            .containsEntry("llm.tokens.input", 100.0) // GH-90000
            .containsEntry("llm.tokens.output", 50.0) // GH-90000
            .containsEntry("llm.tokens.total", 150.0) // GH-90000
            .containsEntry("llm.latency", 321.0); // GH-90000
        assertThat(context.getRemainingBudget()).isCloseTo(4.996, offset(0.000001)); // GH-90000
    }

    @Test
    @DisplayName("execute returns a failed result when no LLM step exists in the generator")
    void executeFailsWhenNoLlmStepExists() { // GH-90000
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); // GH-90000
        CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
            .id("rule-agent")
            .name("Rule Agent")
            .generator(Map.of( // GH-90000
                "steps", List.of(Map.of( // GH-90000
                    "type", "SERVICE",
                    "agent", "fallback-agent"))))
            .build(); // GH-90000

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, Map.of("signal", "x"), AgentContext.empty())); // GH-90000

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); // GH-90000
        assertThat(result.getExplanation()).contains("No LLM step found");
    }

    @Test
    @DisplayName("execute returns a failed result when the LLM step omits the model")
    void executeFailsWhenModelIsMissing() { // GH-90000
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); // GH-90000
        CatalogAgentEntry entry = CatalogAgentEntry.builder() // GH-90000
            .id("broken-agent")
            .name("Broken Agent")
            .generator(Map.of( // GH-90000
                "steps", List.of(Map.of( // GH-90000
                    "type", "LLM",
                    "prompt", "Broken {{input}}"))))
            .build(); // GH-90000

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "request", AgentContext.empty())); // GH-90000

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); // GH-90000
        assertThat(result.getExplanation()).contains("missing required 'model' property");
    }
}