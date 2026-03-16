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
 * DevOps Engineer Agent for deployment automation, infrastructure management, and reliability.
 *
 * <p><b>Purpose</b><br>
 * Specialized DevOps agent responsible for CI/CD pipeline management, infrastructure
 * provisioning, deployment orchestration, monitoring, and incident response.
 * Bridges development and operations to ensure reliable, scalable software delivery.
 *
 * <p><b>Architecture Role</b><br>
 * DevOps specialist role in agent hierarchy:
 * <ul>
 *   <li>Extends AbstractVirtualOrgAgent with DevOps-specific capabilities</li>
 *   <li>Role: DEVOPS_ENGINEER in organizational hierarchy</li>
 *   <li>Reports to: DevOpsLeadAgent for infrastructure strategy and standards</li>
 *   <li>Collaborates with: EngineerAgent (build integration), QaEngineerAgent (pipeline gates)</li>
 * </ul>
 *
 * <p><b>Responsibilities</b><br>
 * DevOps and platform engineering:
 * <ul>
 *   <li>CI/CD Pipelines: Build, test, and deployment pipeline configuration</li>
 *   <li>Infrastructure as Code: Kubernetes manifests, Terraform, Helm charts</li>
 *   <li>Deployment Strategies: Blue-green, canary, rolling deployments</li>
 *   <li>Monitoring: Prometheus, Grafana alerts, SLO/SLA tracking</li>
 *   <li>Incident Response: On-call runbooks, root cause analysis, mitigation</li>
 *   <li>Security: Secret management, network policies, RBAC for infrastructure</li>
 * </ul>
 *
 * <p><b>Decision Authority</b><br>
 * Allowed to make decisions autonomously:
 * <ul>
 *   <li>DEPLOYMENT: Deploy to staging and non-production environments</li>
 *   <li>INFRASTRUCTURE: Minor infrastructure changes (scaling, config updates)</li>
 *   <li>MONITORING: Alert rule configuration, dashboard updates</li>
 *   <li>INCIDENT_RESPONSE: Immediate mitigation actions during incidents</li>
 * </ul>
 *
 * <p>Must escalate to DevOpsLeadAgent:
 * <ul>
 *   <li>PRODUCTION_DEPLOYMENT: Deployments to production environment</li>
 *   <li>INFRASTRUCTURE_CHANGE: Major infrastructure topology changes</li>
 *   <li>COST_OPTIMIZATION: Changes impacting cloud spend</li>
 * </ul>
 *
 * <p><b>Tool Access</b><br>
 * Available tools for DevOps operations:
 * <ul>
 *   <li>KubernetesTool: Pod management, deployment operations, log streaming</li>
 *   <li>TerraformTool: Infrastructure provisioning and state management</li>
 *   <li>PipelineTool: CI/CD pipeline triggers, status checks</li>
 *   <li>MonitoringTool: Alert management, metrics queries, dashboard operations</li>
 *   <li>SecretsTool: Vault/Kubernetes secret management</li>
 * </ul>
 *
 * @see AbstractVirtualOrgAgent
 * @see DevOpsLeadAgent
 * @see EngineerAgent
 * @doc.type class
 * @doc.purpose DevOps engineer agent for deployments, infrastructure, and reliability
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class DevOpsEngineerAgent extends AbstractVirtualOrgAgent {

    private static final Logger log = LoggerFactory.getLogger(DevOpsEngineerAgent.class);

    public DevOpsEngineerAgent(
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
                AgentRoleProto.AGENT_ROLE_DEVOPS_ENGINEER,
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
        log.info("DevOps Engineer Agent starting: {}", getAgentId());
    }

    @Override
    protected void onStop() throws Exception {
        log.info("DevOps Engineer Agent stopping: {}", getAgentId());
    }

    @Override
    @NotNull
    protected Promise<TaskResponseProto> doProcessTask(@NotNull TaskRequestProto request) {
        TaskProto task = request.getTask();
        log.info("DevOps Engineer processing task: taskId={}, type={}, title={}",
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

        log.info("DevOps Engineer making decision: type={}", decisionType);

        String systemPrompt = buildDecisionSystemPrompt(decisionType);
        String userPrompt = buildDecisionUserPrompt(context, options);

        String reasoning = llmClient.generate(systemPrompt, userPrompt, 0.4f, 2048)
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
                .setConfidence(0.80f)
                .setCreatedAt(currentTimestamp())
                .build();
    }

    // =============================
    // Private helpers
    // =============================

    private String buildResult(TaskProto task, LLMResponse llmResponse, List<ToolCallProto> toolCalls) {
        StringBuilder result = new StringBuilder();
        result.append("# DevOps Task: ").append(task.getTitle()).append("\n\n");
        result.append("## Execution Plan\n");
        result.append(llmResponse.reasoning()).append("\n\n");

        if (!toolCalls.isEmpty()) {
            result.append("## Operations Executed\n");
            for (ToolCallProto toolCall : toolCalls) {
                result.append("- ").append(toolCall.getToolName());
                result.append(toolCall.getSuccess() ? " ✓\n" : " ✗ (" + toolCall.getError() + ")\n");
            }
            result.append("\n");
        }

        result.append("## Status\nDevOps task completed by DevOps Engineer Agent.\n");
        return result.toString();
    }

    private String buildDecisionSystemPrompt(DecisionTypeProto decisionType) {
        return String.format("""
                You are a DevOps Engineer evaluating %s.

                Your operational mandate:
                - Ensure system reliability, availability, and observability
                - Apply infrastructure-as-code principles for all changes
                - Prioritize reversible, low-risk deployment strategies
                - Enforce security and compliance in all infrastructure decisions
                - Use data from monitoring to drive decisions

                When evaluating options:
                - Prefer zero-downtime deployment strategies (blue-green, canary)
                - Choose options with clear rollback paths
                - Consider blast radius and potential cascading failures
                - Escalate production deployments to DevOps Lead for approval

                Be precise, risk-aware, and operationally conservative.
                """, decisionType.name());
    }

    private String buildDecisionUserPrompt(Map<String, String> context, List<OptionProto> options) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Infrastructure Context:\n");
        context.forEach((k, v) -> prompt.append("  ").append(k).append(": ").append(v).append("\n"));
        prompt.append("\nOptions:\n");
        for (int i = 0; i < options.size(); i++) {
            prompt.append(i + 1).append(". ").append(options.get(i).getDescription()).append("\n");
        }
        prompt.append("\nAs a DevOps Engineer, which option do you recommend and why?");
        return prompt.toString();
    }
}
