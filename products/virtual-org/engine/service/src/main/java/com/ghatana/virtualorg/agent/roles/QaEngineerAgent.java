package com.ghatana.virtualorg.agent.roles;

import com.ghatana.virtualorg.agent.AbstractVirtualOrgAgent;
import com.ghatana.virtualorg.llm.LLMClient;
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

import java.util.List;
import java.util.Map;

/**
 * QA Engineer Agent for test planning, quality assurance, and defect detection.
 *
 * <p><b>Purpose</b><br>
 * Specialized quality assurance agent responsible for designing test strategies,
 * creating test plans, executing test suites, reporting defects, and enforcing
 * quality gates. Ensures software meets acceptance criteria before promotion.
 *
 * <p><b>Architecture Role</b><br>
 * QA specialist role in agent hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with QA-specific capabilities</li>
 *   <li>Role: QA_ENGINEER in organizational hierarchy</li>
 *   <li>Reports to: QALeadAgent for quality standards and strategy</li>
 *   <li>Collaborates with: EngineerAgent (defect reporting), DevOpsEngineerAgent (pipeline gates)</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * Quality assurance activities:
 * <ul>
 *   <li>Test Strategy: Design comprehensive test plans for features and releases</li>
 *   <li>Test Execution: Run unit, integration, regression, and end-to-end test suites</li>
 *   <li>Defect Detection: Identify and document defects with clear reproduction steps</li>
 *   <li>Coverage Analysis: Track and improve test coverage metrics</li>
 *   <li>Quality Gates: Enforce PASS/WARN/BLOCK gates before release promotion</li>
 *   <li>Test Automation: Create and maintain automated test suites</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Allowed to make decisions autonomously:
 * <ul>
 *   <li>TEST_PLANNING: Define test scope, test cases, coverage targets</li>
 *   <li>DEFECT_REPORTING: Report bugs with severity and priority classification</li>
 *   <li>QUALITY_GATE: Approve or block releases based on quality metrics</li>
 *   <li>TEST_AUTOMATION: Create automated test scripts</li>
 * </ul>
 *
 * <p>Must escalate to QALeadAgent:
 * <ul>
 *   <li>RELEASE_DECISION: Final go/no-go for production releases</li>
 *   <li>QUALITY_POLICY: Changes to acceptance criteria or coverage thresholds</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Available tools for quality operations:
 * <ul>
 *   <li>TestRunnerTool: Execute test suites, collect results</li>
 *   <li>CoverageAnalysisTool: Measure and report code coverage</li>
 *   <li>StaticAnalysisTool: Lint and complexity checks</li>
 *   <li>DefectTrackerTool: Create and update defect tickets</li>
 *   <li>FileOperationsTool: Read source code to identify test gaps</li>
 * </ul>
 *
 * @see AbstractVirtualOrgAgent
 * @see QALeadAgent
 * @see EngineerAgent
 * @doc.type class
 * @doc.purpose QA engineer agent for test planning, execution, and quality gate enforcement
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class QaEngineerAgent extends AbstractVirtualOrgAgent {

    private static final Logger log = LoggerFactory.getLogger(QaEngineerAgent.class);

    public QaEngineerAgent(
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
                AgentRoleProto.AGENT_ROLE_QA_ENGINEER,
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
        log.info("QA Engineer Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("QA Engineer Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("QA Engineer processing task: taskId={}, type={}, title={}",
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

        log.info("QA Engineer making decision: type={}", decisionType);

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
                .setConfidence(0.85f)
                .setCreatedAt(currentTimestamp())
                .build();
    }

    // =============================
    // Private helpers
    // =============================

    private String buildResult(TaskProto task, LLMResponse llmResponse, List<ToolCallProto> toolCalls) {
        StringBuilder result = new StringBuilder();
        result.append("# QA Task: ").append(task.getTitle()).append("\n\n");
        result.append("## Analysis\n");
        result.append(llmResponse.reasoning()).append("\n\n");

        if (!toolCalls.isEmpty()) {
            result.append("## Tools Executed\n");
            for (ToolCallProto toolCall : toolCalls) {
                result.append("- ").append(toolCall.getToolName());
                result.append(toolCall.getSuccess() ? " ✓\n" : " ✗ (" + toolCall.getError() + ")\n");
            }
            result.append("\n");
        }

        result.append("## Status\nQA task completed by QA Engineer Agent.\n");
        return result.toString();
    }

    private String buildDecisionSystemPrompt(DecisionTypeProto decisionType) {
        return String.format("""
                You are a QA Engineer evaluating %s.

                Your quality mandate:
                - Prioritize software correctness and reliability above all
                - Identify test gaps and coverage deficiencies
                - Apply risk-based testing: focus on high-risk, high-impact paths
                - Champion the user experience and prevent regressions
                - Enforce quality gates with data-driven decisions

                When evaluating options:
                - Choose the option that maximizes test coverage and quality signal
                - Prefer catching defects early over fast delivery
                - Consider edge cases, boundary conditions, and failure modes
                - Escalate release decisions with unresolved critical defects to QA Lead

                Be specific, objective, and evidence-based in your reasoning.
                """, decisionType.name());
    }

    private String buildDecisionUserPrompt(Map<String, String> context, List<OptionProto> options) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("QA Context:\n");
        context.forEach((k, v) -> prompt.append("  ").append(k).append(": ").append(v).append("\n"));
        prompt.append("\nOptions:\n");
        for (int i = 0; i < options.size(); i++) {
            prompt.append(i + 1).append(". ").append(options.get(i).getDescription()).append("\n");
        }
        prompt.append("\nAs a QA Engineer focused on quality, which option do you recommend and why?");
        return prompt.toString();
    }
}
