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
@ExtendWith(MockitoExtension.class) 
class DefaultLlmExecutionPlanTest extends EventloopTestBase {

    @Mock
    private LlmProvider llmProvider;

    @Test
    @DisplayName("execute hydrates the prompt and forwards provider and model settings")
    void executeHydratesPromptAndForwardsRoutingSettings() { 
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); 
        CatalogAgentEntry entry = CatalogAgentEntry.builder() 
            .id("fraud-triage")
            .name("Fraud Triage")
            .generator(Map.of( 
                "steps", List.of(Map.of( 
                    "type", "LLM",
                    "provider", "OPENAI",
                    "model", "gpt-4o-mini",
                    "prompt", "Agent {{agent_name}}/{{agent_id}} sees {{input}} within {{context}}",
                    "temperature", 0.35,
                    "max_tokens", 512))))
            .build(); 
        AgentContext context = AgentContext.builder() 
            .turnId("turn-1")
            .agentId("fraud-triage")
            .tenantId("tenant-a")
            .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp()) 
            .build(); 

        when(llmProvider.invoke( 
                "OPENAI",
                "gpt-4o-mini",
                "Agent Fraud Triage/fraud-triage sees suspicious payment within " + context,
                0.35,
                512,
                context))
            .thenReturn(Promise.of(CompletionResult.builder() 
                .text("triage-result")
                .modelUsed("gpt-4o-mini")
                .promptTokens(120) 
                .completionTokens(60) 
                .tokensUsed(180) 
                .latencyMs(240) 
                .build())); 

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "suspicious payment", context)); 

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        assertThat(result.getOutput()).isEqualTo("triage-result");
        assertThat(result.getMetrics()) 
            .containsEntry("tier", "LLM_EXECUTED") 
            .containsEntry("provider", "OPENAI") 
            .containsEntry("model", "gpt-4o-mini") 
            .containsEntry("prompt_tokens", 120) 
            .containsEntry("completion_tokens", 60) 
            .containsEntry("total_tokens", 180); 
    }

    @Test
    @DisplayName("execute uses the default provider and token limit when optional values are omitted")
    void executeUsesDefaultProviderAndTokenLimit() { 
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); 
        CatalogAgentEntry entry = CatalogAgentEntry.builder() 
            .id("summary-agent")
            .name("Summary Agent")
            .generator(Map.of( 
                "steps", List.of(Map.of( 
                    "type", "LLM",
                    "model", "claude-3-5-sonnet-20241022",
                    "prompt", "Summarize {{input}} for {{agent_id}}"))))
            .build(); 
        AgentContext context = AgentContext.empty(); 

        when(llmProvider.invoke( 
                "ANTHROPIC",
                "claude-3-5-sonnet-20241022",
                "Summarize inbox backlog for summary-agent",
                0.2,
                2000,
                context))
            .thenReturn(Promise.of(CompletionResult.builder() 
                .text("summary")
                .modelUsed("claude-3-5-sonnet-20241022")
                .promptTokens(40) 
                .completionTokens(20) 
                .tokensUsed(60) 
                .latencyMs(120) 
                .build())); 

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "inbox backlog", context)); 

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        assertThat(result.getExplanation()).contains("ANTHROPIC/claude-3-5-sonnet-20241022");
    }

    @Test
    @DisplayName("execute records token metrics and deducts cost from the agent budget")
    void executeRecordsTokenMetricsAndDeductsCost() { 
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); 
        CatalogAgentEntry entry = CatalogAgentEntry.builder() 
            .id("budget-agent")
            .name("Budget Agent")
            .generator(Map.of( 
                "steps", List.of(Map.of( 
                    "type", "LLM",
                    "provider", "OPENAI",
                    "model", "gpt-4o-mini",
                    "prompt", "Assess {{input}}"))))
            .build(); 
        DefaultAgentContext context = (DefaultAgentContext) AgentContext.builder() 
            .turnId("turn-budget")
            .agentId("budget-agent")
            .tenantId("tenant-budget")
            .memoryStore(com.ghatana.agent.framework.memory.MemoryStore.noOp()) 
            .remainingBudget(5.0) 
            .addConfig("llm.cost.inputPer1kUsd", 0.02) 
            .addConfig("llm.cost.outputPer1kUsd", 0.04) 
            .build(); 

        when(llmProvider.invoke( 
                "OPENAI",
                "gpt-4o-mini",
                "Assess risky transfer",
                0.2,
                2000,
                context))
            .thenReturn(Promise.of(CompletionResult.builder() 
                .text("approved")
                .modelUsed("gpt-4o-mini")
                .promptTokens(100) 
                .completionTokens(50) 
                .tokensUsed(150) 
                .latencyMs(321) 
                .build())); 

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "risky transfer", context)); 

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.SUCCESS); 
        assertThat(result.getMetrics()).containsEntry("cost_usd", 0.004d); 
        assertThat(context.getMetrics()) 
            .containsEntry("llm.cost", 0.004d) 
            .containsEntry("llm.tokens.input", 100.0) 
            .containsEntry("llm.tokens.output", 50.0) 
            .containsEntry("llm.tokens.total", 150.0) 
            .containsEntry("llm.latency", 321.0); 
        assertThat(context.getRemainingBudget()).isCloseTo(4.996, offset(0.000001)); 
    }

    @Test
    @DisplayName("execute returns a failed result when no LLM step exists in the generator")
    void executeFailsWhenNoLlmStepExists() { 
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); 
        CatalogAgentEntry entry = CatalogAgentEntry.builder() 
            .id("rule-agent")
            .name("Rule Agent")
            .generator(Map.of( 
                "steps", List.of(Map.of( 
                    "type", "SERVICE",
                    "agent", "fallback-agent"))))
            .build(); 

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, Map.of("signal", "x"), AgentContext.empty())); 

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); 
        assertThat(result.getExplanation()).contains("No LLM step found");
    }

    @Test
    @DisplayName("execute returns a failed result when the LLM step omits the model")
    void executeFailsWhenModelIsMissing() { 
        DefaultLlmExecutionPlan plan = new DefaultLlmExecutionPlan(llmProvider); 
        CatalogAgentEntry entry = CatalogAgentEntry.builder() 
            .id("broken-agent")
            .name("Broken Agent")
            .generator(Map.of( 
                "steps", List.of(Map.of( 
                    "type", "LLM",
                    "prompt", "Broken {{input}}"))))
            .build(); 

        AgentResult<Object> result = runPromise(() -> plan.execute(entry, "request", AgentContext.empty())); 

        assertThat(result.getStatus()).isEqualTo(AgentResultStatus.FAILED); 
        assertThat(result.getExplanation()).contains("missing required 'model' property");
    }
}