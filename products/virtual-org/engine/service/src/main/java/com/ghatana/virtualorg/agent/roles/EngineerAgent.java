package com.ghatana.virtualorg.agent.roles;

import com.ghatana.virtualorg.agent.AbstractVirtualOrgAgent;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.LLMResponse;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.v1.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Engineer Agent for feature implementation, bug fixes, and routine development tasks.
 *
 * <p><b>Purpose</b><br>
 * Mid-level development agent capable of implementing well-defined features, fixing bugs,
 * writing unit tests, and contributing to code reviews. Operates within bounded scope with
 * escalation paths to Senior Engineer for complex decisions.
 *
 * <p><b>Architecture Role</b><br>
 * Mid-level technical role in agent hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with engineer-level capabilities</li>
 *   <li>Role: ENGINEER in organizational hierarchy</li>
 *   <li>Reports to: SeniorEngineerAgent for technical decisions</li>
 *   <li>Collaborates with: QaEngineerAgent for testing, DevOpsEngineerAgent for deployment</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Core development activities:
 * <ul>
 *   <li>Feature Implementation: Well-defined features up to 200 LOC</li>
 *   <li>Bug Fixes: Diagnose and resolve reported defects</li>
 *   <li>Unit Testing: Write and maintain unit tests to 80%+ coverage</li>
 *   <li>Code Review Participation: Review peer code, address review feedback</li>
 *   <li>Documentation: Inline code comments, method JavaDoc</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Allowed to make decisions autonomously:
 * <ul>
 *   <li>BUG_FIX: Fix bugs within current module (up to 200 LOC changes)</li>
 *   <li>UNIT_TEST: Write and extend test coverage</li>
 *   <li>REFACTOR: Minor refactoring within a single file</li>
 * </ul>
 *
 * <p>Must escalate to SeniorEngineerAgent:
 * <ul>
 *   <li>IMPLEMENT_FEATURE: Non-trivial features spanning multiple files</li>
 *   <li>ARCHITECTURE: Any architectural decisions</li>
 *   <li>DESIGN: Cross-module design questions</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Available tools for task execution:
 * <ul>
 *   <li>GitTool: Branch, commit, push operations</li>
 *   <li>FileOperationsTool: Read and write source files</li>
 *   <li>BuildTool: Compile and run unit tests</li>
 *   <li>HttpTool: Call APIs, fetch documentation</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Uses balanced LLM settings for routine engineering tasks:
 * <ul>
 *   <li>Model: GPT-4 (precision-focused for code generation)</li>
 *   <li>Temperature: 0.3 (low temperature for consistent, correct code)</li>
 *   <li>Max Tokens: 2048 (moderate features and fixes)</li>
 * </ul>
 *
 * @see AbstractVirtualOrgAgent
 * @see SeniorEngineerAgent
 * @see QaEngineerAgent
 * @doc.type class
 * @doc.purpose Mid-level engineer agent for features, bug fixes, and unit testing
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class EngineerAgent extends AbstractVirtualOrgAgent {

    private static final Logger log = LoggerFactory.getLogger(EngineerAgent.class);

    public EngineerAgent(
            @NotNull String agentId,
            @NotNull DecisionAuthorityProto authority,
            @NotNull Eventloop eventloop,
            @NotNull LLMClient llmClient,
            @NotNull AgentMemory memory,
            @NotNull ToolRegistry toolRegistry,
            @NotNull ToolExecutor toolExecutor,
            @NotNull MeterRegistry meterRegistry,
            @NotNull Tracer tracer,
            @NotNull LLMConfigProto llmConfig,
            @NotNull MemoryConfigProto memoryConfig) {

        super(
                agentId,
                AgentRoleProto.AGENT_ROLE_ENGINEER,
                authority,
                eventloop,
                llmClient,
                memory,
                toolRegistry,
                toolExecutor,
                meterRegistry,
                tracer,
                llmConfig,
                memoryConfig
        );
    }

    @Override
    protected void onStart() throws Exception {
        log.info("Engineer Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("Engineer Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("Engineer processing task: taskId={}, type={}, title={}",
                task.getTaskId(), task.getType(), task.getTitle());

        List<ToolProto> tools = getTools();

        return memory.retrieveContext(task)
                .whenException(e -> log.warn("Failed to retrieve context, using empty", e))
                .map(context -> context != null ? context : "")
                .then(context ->
                        llmClient.reason(task, context, tools)
                                .then(llmResponse -> {
                                    log.debug("LLM reasoning complete: taskId={}, tokensUsed={}",
                                            task.getTaskId(), llmResponse.tokensUsed());

                                    Promise<List<ToolCallProto>> toolCallsPromise =
                                            llmResponse.toolCalls().isEmpty()
                                                    ? Promise.of(List.of())
                                                    : toolExecutor.executeAll(llmResponse.toolCalls())
                                                            .whenException(e -> log.warn("Tool execution failed", e))
                                                            .map(calls -> calls != null ? calls : List.of());

                                    return toolCallsPromise.map(executedToolCalls -> {
                                        String result = buildResult(task, llmResponse, executedToolCalls);

                                        return TaskResponseProto.newBuilder()
                                                .setTaskId(task.getTaskId())
                                                .setSuccess(true)
                                                .setResult(result)
                                                .setReasoning(llmResponse.reasoning())
                                                .addAllToolCalls(executedToolCalls)
                                                .setMetrics(AgentMetricsProto.newBuilder()
                                                        .setProcessingTimeMs(0)
                                                        .setTokensUsed(llmResponse.tokensUsed())
                                                        .setConfidenceScore(llmResponse.confidence())
                                                        .build())
                                                .build();
                                    });
                                })
                )
                .then(response ->
                        memory.store(task, response)
                                .whenException(e -> log.warn("Failed to store in memory", e))
                                .map((Void ignored) -> response)
                );
    }

    @Override
    @NotNull
    protected DecisionProto doMakeDecision(
            @NotNull DecisionTypeProto decisionType,
            @NotNull Map<String, String> context,
            @NotNull List<OptionProto> options) throws Exception {

        log.info("Engineer making decision: type={}", decisionType);

        String systemPrompt = buildDecisionSystemPrompt(decisionType);
        String userPrompt = buildDecisionUserPrompt(context, options);

        String reasoning = llmClient.generate(systemPrompt, userPrompt, 0.3f, 2048)
                .toCompletableFuture().join();

        OptionProto chosenOption = options.isEmpty() ? null : options.get(0);

        return DecisionProto.newBuilder()
                .setDecisionId(generateId())
                .setAgentId(getAgentId())
                .setType(decisionType)
                .putAllContext(context)
                .addAllOptionsConsidered(options)
                .setChosenOption(chosenOption)
                .setReasoning(reasoning)
                .setConfidence(0.75f)
                .setCreatedAt(currentTimestamp())
                .build();
    }

    // =============================
    // Private helpers
    // =============================

    private String buildResult(TaskProto task, LLMResponse llmResponse, List<ToolCallProto> toolCalls) {
        StringBuilder result = new StringBuilder();
        result.append("# Task: ").append(task.getTitle()).append("\n\n");
        result.append("## Implementation\n");
        result.append(llmResponse.reasoning()).append("\n\n");

        if (!toolCalls.isEmpty()) {
            result.append("## Tools Executed\n");
            for (ToolCallProto toolCall : toolCalls) {
                result.append("- ").append(toolCall.getToolName());
                result.append(toolCall.getSuccess() ? " ✓\n" : " ✗ (" + toolCall.getError() + ")\n");
            }
            result.append("\n");
        }

        result.append("## Status\nTask completed by Engineer Agent.\n");
        return result.toString();
    }

    private String buildDecisionSystemPrompt(DecisionTypeProto decisionType) {
        return String.format("""
                You are a Software Engineer evaluating %s.

                Your scope:
                - Implement well-defined features and bug fixes
                - Write clean, tested, maintainable code
                - Follow established patterns and conventions
                - Escalate architectural decisions to Senior Engineer

                Consider:
                - Technical correctness and completeness
                - Code readability and maintainability
                - Test coverage implications
                - Risk of regression

                Provide clear reasoning and select the most conservative, safe option.
                """, decisionType.name());
    }

    private String buildDecisionUserPrompt(Map<String, String> context, List<OptionProto> options) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Context:\n");
        context.forEach((k, v) -> prompt.append("  ").append(k).append(": ").append(v).append("\n"));
        prompt.append("\nOptions:\n");
        for (int i = 0; i < options.size(); i++) {
            prompt.append(i + 1).append(". ").append(options.get(i).getDescription()).append("\n");
        }
        prompt.append("\nAs a Software Engineer, which option do you recommend and why?");
        return prompt.toString();
    }
}
