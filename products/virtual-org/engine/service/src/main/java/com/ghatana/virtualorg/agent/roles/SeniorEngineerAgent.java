package com.ghatana.virtualorg.agent.roles;

import com.ghatana.virtualorg.agent.AbstractVirtualOrgAgent;
import com.ghatana.virtualorg.llm.LLMClient;
import com.ghatana.virtualorg.llm.LLMResponse;
import com.ghatana.virtualorg.memory.AgentMemory;
import com.ghatana.virtualorg.tool.ToolExecutor;
import com.ghatana.virtualorg.tool.ToolRegistry;
import com.ghatana.virtualorg.v1.*;
import com.google.protobuf.Timestamp;
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
import com.ghatana.virtualorg.util.DecisionExtractor;

/**
 * Senior Engineer Agent for complex feature implementation and technical leadership.
 *
 * <p><b>Purpose</b><br>
 * Autonomous agent capable of implementing complex features, conducting code reviews,
 * optimizing performance, and mentoring junior engineers. Operates with elevated
 * decision authority for technical implementations.
 *
 * <p><b>Architecture Role</b><br>
 * High-capability technical role in agent hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with senior-level capabilities</li>
 *   <li>Role: SENIOR_ENGINEER in organizational hierarchy</li>
 *   <li>Reports to: ArchitectAgent for architectural decisions</li>
 *   <li>Supervises: EngineerAgent, JuniorEngineerAgent</li>
 *   <li>Collaborates with: QAAgent for quality, DevOpsAgent for deployments</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Core technical capabilities:
 * <ul>
 *   <li>Complex Feature Implementation: Multi-module features with 100-500 LOC</li>
 *   <li>System Design: Design decisions for subsystems (escalate major architecture)</li>
 *   <li>Code Review: Review PRs from engineers and junior engineers</li>
 *   <li>Mentoring: Guide junior engineers with feedback and suggestions</li>
 *   <li>Performance Optimization: Profile, analyze, optimize bottlenecks</li>
 *   <li>Refactoring: Improve code quality, reduce tech debt (up to 200 LOC)</li>
 *   <li>Bug Resolution: Debug and fix complex production issues</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Allowed to make decisions autonomously:
 * <ul>
 *   <li>IMPLEMENTATION: Up to 500 LOC feature implementations</li>
 *   <li>REFACTORING: Up to 200 LOC refactoring initiatives</li>
 *   <li>CODE_REVIEW: Approve/reject PRs with detailed feedback</li>
 *   <li>OPTIMIZATION: Performance improvements without architectural changes</li>
 *   <li>BUG_FIX: Production bug fixes (critical priority)</li>
 * </ul>
 *
 * <p>Must escalate to ArchitectAgent:
 * <ul>
 *   <li>ARCHITECTURE: Major architectural changes</li>
 *   <li>DESIGN: Cross-module design decisions</li>
 *   <li>TECHNOLOGY: Introduction of new libraries/frameworks</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Available tools for task execution:
 * <ul>
 *   <li>GitTool: Clone, branch, commit, push, pull, merge</li>
 *   <li>FileOperationsTool: Read, write, search, refactor code files</li>
 *   <li>HttpTool: Call APIs, fetch documentation</li>
 *   <li>BuildTool: Run tests, compile, package (custom tool)</li>
 *   <li>CodeAnalysisTool: Static analysis, complexity metrics (custom tool)</li>
 * </ul>
 *
 * <p><b>LLM Configuration</b><br>
 * Uses advanced LLM settings for complex reasoning:
 * <ul>
 *   <li>Model: GPT-4 or Claude 3 Opus (high-capability models)</li>
 *   <li>Temperature: 0.3 (balanced creativity and precision)</li>
 *   <li>Max Tokens: 4000 (complex implementations)</li>
 *   <li>System Prompt: Senior engineer with 8+ years experience</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * SeniorEngineerAgent agent = new SeniorEngineerAgent(
 *     "senior-eng-001",
 *     DecisionAuthorityProto.newBuilder()
 *         .addAllowedDecisionTypes(DecisionTypeProto.IMPLEMENTATION)
 *         .addAllowedDecisionTypes(DecisionTypeProto.REFACTORING)
 *         .setMaxComplexity(500)
 *         .build(),
 *     eventloop, llmClient, memory, toolRegistry, toolExecutor,
 *     meterRegistry, tracer, llmConfig, memoryConfig
 * );
 *
 * TaskRequestProto task = TaskRequestProto.newBuilder()
 *     .setTaskId("task-123")
 *     .setDescription("Implement user authentication with JWT")
 *     .setType(TaskTypeProto.FEATURE_IMPLEMENTATION)
 *     .build();
 *
 * agent.processTask(task).whenResult(result ->
 *     log.info("Task completed: {}", result.getStatus())
 * );
 * }</pre>
 *
 * @see AbstractVirtualOrgAgent
 * @see ArchitectAgent
 * @see EngineerAgent
 * @doc.type class
 * @doc.purpose Senior engineer agent for complex features and technical leadership
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class SeniorEngineerAgent extends AbstractVirtualOrgAgent {

    private static final Logger log = LoggerFactory.getLogger(SeniorEngineerAgent.class);

    public SeniorEngineerAgent(
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
                AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER,
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
        log.info("Senior Engineer Agent starting: {}", getAgentId());
        // Initialize any agent-specific resources
    }

    @Override
    protected void onStop() throws Exception {
        log.info("Senior Engineer Agent stopping: {}", getAgentId());
        // Cleanup resources
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("Senior Engineer processing task: taskId={}, type={}, title={}", 
            task.getTaskId(), task.getType(), task.getTitle());

        // Get available tools (synchronous)
        List<ToolProto> tools = getTools();

        // Chain all async operations
        return memory.retrieveContext(task)
            .whenException(e -> log.warn("Failed to retrieve context, using empty", e))
            .map(context -> context != null ? context : "")
            .then(context -> 
                // Use LLM to reason about the task
                llmClient.reason(task, context, tools)
                    .then(llmResponse -> {
                        log.debug("LLM reasoning complete: taskId={}, tokensUsed={}", task.getTaskId(), llmResponse.tokensUsed());

                        // Execute tool calls if any
                        Promise<List<ToolCallProto>> toolCallsPromise = llmResponse.toolCalls().isEmpty()
                                ? Promise.of(List.of())
                                : toolExecutor.executeAll(llmResponse.toolCalls())
                                        .whenException(e -> log.warn("Tool execution failed", e))
                                        .map(calls -> calls != null ? calls : List.of());

                        return toolCallsPromise.map(executedToolCalls -> {
                            // Build final result
                            String result = buildResult(task, llmResponse, executedToolCalls);

                            // Create response
                            return TaskResponseProto.newBuilder()
                                    .setTaskId(task.getTaskId())
                                    .setSuccess(true)
                                    .setResult(result)
                                    .setReasoning(llmResponse.reasoning())
                                    .addAllToolCalls(executedToolCalls)
                                    .setMetrics(AgentMetricsProto.newBuilder()
                                            .setProcessingTimeMs(0) // Will be updated by caller
                                            .setTokensUsed(llmResponse.tokensUsed())
                                            .setConfidenceScore(llmResponse.confidence())
                                            .build())
                                    .build();
                        });
                    })
            )
            .then(response -> 
                // Store experience in memory
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

        log.info("Senior Engineer making decision: type={}", decisionType);

        // Use LLM to evaluate options
        String systemPrompt = buildDecisionSystemPrompt(decisionType);
        String userPrompt = buildDecisionUserPrompt(context, options);

        String reasoning = llmClient.generate(systemPrompt, userPrompt, 0.7f, 2048)
            .toCompletableFuture().join();

        // Select best option (simplified - should parse LLM response)
        OptionProto chosenOption = options.get(0); // TODO: Parse LLM's choice

        return DecisionProto.newBuilder()
                .setDecisionId(generateId())
                .setAgentId(getAgentId())
                .setType(decisionType)
                .putAllContext(context)
                .addAllOptionsConsidered(options)
                .setChosenOption(chosenOption)
                .setReasoning(reasoning)
                .setConfidence(0.85f)
                .setCreatedAt(currentTimestamp())
                .build();
    }

    // =============================
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
                if (toolCall.getSuccess()) {
                    result.append(" ✓\n");
                } else {
                    result.append(" ✗ (").append(toolCall.getError()).append(")\n");
                }
            }
            result.append("\n");
        }

        result.append("## Status\n");
        result.append("Task completed successfully by Senior Engineer Agent.\n");

        return result.toString();
    }

    private String buildDecisionSystemPrompt(DecisionTypeProto decisionType) {
        return String.format("""
                You are a Senior Software Engineer evaluating %s.

                Consider:
                - Technical feasibility
                - Code maintainability
                - Performance implications
                - Best practices
                - Team impact

                Provide clear reasoning and select the best option.
                """, decisionType.name());
    }

    private String buildDecisionUserPrompt(Map<String, String> context, List<OptionProto> options) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Context:\n");
        context.forEach((k, v) -> prompt.append("- ").append(k).append(": ").append(v).append("\n"));
        prompt.append("\n");

        prompt.append("Options:\n");
        for (int i = 0; i < options.size(); i++) {
            OptionProto option = options.get(i);
            prompt.append(i + 1).append(". ").append(option.getDescription()).append("\n");
            if (!option.getProsList().isEmpty()) {
                prompt.append("   Pros: ").append(String.join(", ", option.getProsList())).append("\n");
            }
            if (!option.getConsList().isEmpty()) {
                prompt.append("   Cons: ").append(String.join(", ", option.getConsList())).append("\n");
            }
        }

        prompt.append("\nWhich option should we choose and why?");
        return prompt.toString();
    }
}
