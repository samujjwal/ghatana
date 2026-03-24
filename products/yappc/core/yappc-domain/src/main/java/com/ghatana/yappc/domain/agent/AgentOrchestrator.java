package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Agent Orchestrator for coordinating multi-agent workflows.
 * <p>
 * Manages the execution of complex workflows involving multiple AI agents
 * with dependency resolution, parallel execution, and error handling.
 *
 * @doc.type class
 * @doc.purpose Multi-agent workflow orchestration
 * @doc.layer product
 * @doc.pattern Orchestrator
 */
public class AgentOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final Map<AgentName, AIAgent<?, ?>> agents;
    private final MetricsCollector metricsCollector;

    /**
     * Creates a new AgentOrchestrator.
     *
     * @param metricsCollector The metrics collector
     */
    public AgentOrchestrator(@NotNull MetricsCollector metricsCollector) {
        this.agents = new ConcurrentHashMap<>();
        this.metricsCollector = metricsCollector;
    }

    /**
     * Registers an agent with the orchestrator.
     *
     * @param agent The agent to register
     */
    public void registerAgent(@NotNull AIAgent<?, ?> agent) {
        AgentName name = agent.getMetadata().name();
        agents.put(name, agent);
        LOG.info("Registered agent: {}", name.getDisplayName());
    }

    /**
     * Unregisters an agent from the orchestrator.
     *
     * @param name The agent name to unregister
     */
    public void unregisterAgent(@NotNull AgentName name) {
        agents.remove(name);
        LOG.info("Unregistered agent: {}", name.getDisplayName());
    }

    /**
     * Gets an agent by name.
     *
     * @param name The agent name
     * @return The agent, or null if not found
     */
    @Nullable
    public AIAgent<?, ?> getAgent(@NotNull AgentName name) {
        return agents.get(name);
    }

    /**
     * Executes a workflow with multiple agent steps.
     *
     * @param workflow The workflow to execute
     * @param context  The execution context
     * @return Promise resolving to the workflow result
     */
    public Promise<WorkflowResult> executeWorkflow(
            @NotNull AgentWorkflow workflow,
            @NotNull AIAgentContext context
    ) {
        LOG.info("Starting workflow: {}", workflow.name());
        long startTime = System.currentTimeMillis();

        metricsCollector.incrementCounter("orchestrator.workflows.started", "workflow", workflow.name());

        Map<String, AgentResult<?>> results = new ConcurrentHashMap<>();

        // Build execution plan based on dependencies
        List<List<WorkflowStep>> executionPlan = buildExecutionPlan(workflow);

        return executeStages(executionPlan, results, context, workflow.stopOnError())
                .map(v -> {
                    long duration = System.currentTimeMillis() - startTime;

                    boolean allSuccess = results.values().stream().allMatch(AgentResult::success);

                    WorkflowResult result = new WorkflowResult(
                            allSuccess,
                            new ArrayList<>(results.values()),
                            aggregateMetrics(results),
                            duration
                    );

                    if (allSuccess) {
                        metricsCollector.incrementCounter("orchestrator.workflows.success", "workflow", workflow.name());
                    } else {
                        metricsCollector.incrementCounter("orchestrator.workflows.failed", "workflow", workflow.name());
                    }

                    metricsCollector.recordTimer("orchestrator.workflows.duration", duration, "workflow", workflow.name());

                    return result;
                })
                .mapException(error -> {
                    LOG.error("Workflow {} failed: {}", workflow.name(), error.getMessage());
                    metricsCollector.incrementCounter("orchestrator.workflows.error", "workflow", workflow.name());
                    return error;
                });
    }

    private List<List<WorkflowStep>> buildExecutionPlan(AgentWorkflow workflow) {
        // Topological sort based on dependencies
        Map<String, WorkflowStep> stepMap = new HashMap<>();
        Map<String, Set<String>> inDegree = new HashMap<>();

        for (WorkflowStep step : workflow.steps()) {
            stepMap.put(step.id(), step);
            inDegree.put(step.id(), new HashSet<>(step.dependsOn()));
        }

        List<List<WorkflowStep>> stages = new ArrayList<>();

        while (!stepMap.isEmpty()) {
            // Find all steps with no remaining dependencies
            List<WorkflowStep> currentStage = new ArrayList<>();

            for (Map.Entry<String, Set<String>> entry : new HashMap<>(inDegree).entrySet()) {
                if (entry.getValue().isEmpty()) {
                    currentStage.add(stepMap.get(entry.getKey()));
                }
            }

            if (currentStage.isEmpty() && !stepMap.isEmpty()) {
                throw new IllegalArgumentException("Circular dependency detected in workflow");
            }

            // Remove completed steps and update dependencies
            for (WorkflowStep step : currentStage) {
                stepMap.remove(step.id());
                inDegree.remove(step.id());

                // Update remaining steps that depended on this one
                for (Set<String> deps : inDegree.values()) {
                    deps.remove(step.id());
                }
            }

            if (!currentStage.isEmpty()) {
                stages.add(currentStage);
            }
        }

        return stages;
    }

    private Promise<Void> executeStages(
            List<List<WorkflowStep>> stages,
            Map<String, AgentResult<?>> results,
            AIAgentContext context,
            boolean stopOnError
    ) {
        Promise<Void> chain = Promise.complete();

        for (List<WorkflowStep> stage : stages) {
            chain = chain.then(v -> executeStage(stage, results, context, stopOnError));
        }

        return chain;
    }

    private Promise<Void> executeStage(
            List<WorkflowStep> stage,
            Map<String, AgentResult<?>> results,
            AIAgentContext context,
            boolean stopOnError
    ) {
        // Execute all steps in the stage in parallel
        List<Promise<Void>> stepPromises = new ArrayList<>();

        for (WorkflowStep step : stage) {
            Promise<Void> stepPromise = executeStep(step, results, context)
                    .then(result -> {
                        results.put(step.id(), result);

                        if (!result.success() && stopOnError) {
                            return Promise.ofException(new WorkflowStepException(step.id(), result.error()));
                        }

                        return Promise.complete();
                    });

            stepPromises.add(stepPromise);
        }

        return Promises.toList(stepPromises).toVoid();
    }

    @SuppressWarnings("unchecked")
    private <TInput, TOutput> Promise<AgentResult<?>> executeStep(
            WorkflowStep step,
            Map<String, AgentResult<?>> previousResults,
            AIAgentContext context
    ) {
        AIAgent<TInput, TOutput> agent = (AIAgent<TInput, TOutput>) agents.get(step.agentName());

        if (agent == null) {
            return Promise.ofException(new IllegalArgumentException("Agent not found: " + step.agentName()));
        }

        // Build input from dependencies
        TInput input = (TInput) buildInputFromDependencies(step, previousResults);

        return agent.execute(input, context)
                .map(result -> (AgentResult<?>) result);
    }

    private Object buildInputFromDependencies(
            WorkflowStep step,
            Map<String, AgentResult<?>> previousResults
    ) {
        Map<String, Object> resolvedInput = new HashMap<>(step.input());

        // Replace references to previous results
        for (Map.Entry<String, Object> entry : step.input().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String strValue && strValue.startsWith("@")) {
                String refId = strValue.substring(1);
                AgentResult<?> refResult = previousResults.get(refId);
                if (refResult != null && refResult.success()) {
                    resolvedInput.put(entry.getKey(), refResult.data());
                }
            }
        }

        return resolvedInput;
    }

    private WorkflowMetrics aggregateMetrics(Map<String, AgentResult<?>> results) {
        long totalLatency = 0;
        int totalTokens = 0;
        double totalCost = 0;

        for (AgentResult<?> result : results.values()) {
            totalLatency += result.metrics().latencyMs();
            if (result.metrics().tokensUsed() != null) {
                totalTokens += result.metrics().tokensUsed();
            }
            if (result.metrics().costUSD() != null) {
                totalCost += result.metrics().costUSD();
            }
        }

        return new WorkflowMetrics(
                totalLatency,
                totalTokens,
                totalCost,
                results.size(),
                (int) results.values().stream().filter(AgentResult::success).count()
        );
    }

    /**
     * Executes a phase change workflow involving multiple agents.
     */
    public Promise<WorkflowResult> executePhaseChangeWorkflow(
            String itemId,
            String newPhase,
            AIAgentContext context
    ) {
        AgentWorkflow workflow = new AgentWorkflow(
                "phase-change",
                "Phase Change Workflow",
                List.of(
                        new WorkflowStep(
                                "predict",
                                AgentName.PREDICTION_AGENT,
                                Map.of("itemId", itemId, "currentPhase", newPhase),
                                List.of()
                        ),
                        new WorkflowStep(
                                "analyze-risks",
                                AgentName.ANOMALY_DETECTOR_AGENT,
                                Map.of("itemId", itemId, "metricType", "VELOCITY"),
                                List.of()
                        ),
                        new WorkflowStep(
                                "recommend",
                                AgentName.RECOMMENDATION_AGENT,
                                Map.of(
                                        "itemId", itemId,
                                        "predictions", "@predict",
                                        "risks", "@analyze-risks"
                                ),
                                List.of("predict", "analyze-risks")
                        )
                ),
                false
        );

        return executeWorkflow(workflow, context);
    }

    /**
     * Executes a comprehensive item analysis workflow.
     */
    public Promise<WorkflowResult> executeItemAnalysisWorkflow(
            String itemId,
            AIAgentContext context
    ) {
        AgentWorkflow workflow = new AgentWorkflow(
                "item-analysis",
                "Comprehensive Item Analysis",
                List.of(
                        new WorkflowStep(
                                "predict",
                                AgentName.PREDICTION_AGENT,
                                Map.of("itemId", itemId, "horizonDays", 30),
                                List.of()
                        ),
                        new WorkflowStep(
                                "anomalies",
                                AgentName.ANOMALY_DETECTOR_AGENT,
                                Map.of("itemId", itemId, "metricType", "QUALITY_METRICS"),
                                List.of()
                        ),
                        new WorkflowStep(
                                "copilot-summary",
                                AgentName.COPILOT_AGENT,
                                Map.of(
                                        "query", "Summarize the analysis for item " + itemId,
                                        "predictions", "@predict",
                                        "anomalies", "@anomalies"
                                ),
                                List.of("predict", "anomalies")
                        )
                ),
                false
        );

        return executeWorkflow(workflow, context);
    }

    /**
     * Performs health checks on all registered agents.
     */
    public Promise<Map<AgentName, AgentHealth>> healthCheckAll() {
        Map<AgentName, Promise<AgentHealth>> healthPromises = new HashMap<>();

        for (Map.Entry<AgentName, AIAgent<?, ?>> entry : agents.entrySet()) {
            healthPromises.put(entry.getKey(), entry.getValue().healthCheck());
        }

        return Promises.toList(
                healthPromises.entrySet().stream()
                        .map(e -> e.getValue().map(h -> Map.entry(e.getKey(), h)))
                        .toList()
        ).map(entries -> {
            Map<AgentName, AgentHealth> result = new HashMap<>();
            for (Map.Entry<AgentName, AgentHealth> entry : entries) {
                result.put(entry.getKey(), entry.getValue());
            }
            return result;
        });
    }

    /**
     * Gets the status of all registered agents.
     */
    public List<AgentStatus> getAgentStatuses() {
        List<AgentStatus> statuses = new ArrayList<>();

        for (Map.Entry<AgentName, AIAgent<?, ?>> entry : agents.entrySet()) {
            AgentMetadata metadata = entry.getValue().getMetadata();
            statuses.add(new AgentStatus(
                    entry.getKey(),
                    metadata.version(),
                    true, // registered
                    metadata.latencySLA()
            ));
        }

        return statuses;
    }

    // Data structures

    /**
     * A workflow definition.
     */
    public record AgentWorkflow(
            @NotNull String name,
            @NotNull String description,
            @NotNull List<WorkflowStep> steps,
            boolean stopOnError
    ) {}

    /**
     * A single step in a workflow.
     */
    public record WorkflowStep(
            @NotNull String id,
            @NotNull AgentName agentName,
            @NotNull Map<String, Object> input,
            @NotNull List<String> dependsOn
    ) {}

    /**
     * Result of a workflow execution.
     */
    public record WorkflowResult(
            boolean success,
            @NotNull List<AgentResult<?>> results,
            @NotNull WorkflowMetrics metrics,
            long durationMs
    ) {}

    /**
     * Aggregated workflow metrics.
     */
    public record WorkflowMetrics(
            long totalLatencyMs,
            int totalTokensUsed,
            double totalCostUSD,
            int stepsExecuted,
            int stepsSucceeded
    ) {}

    /**
     * Agent status information.
     */
    public record AgentStatus(
            @NotNull AgentName name,
            @NotNull String version,
            boolean registered,
            long latencySLA
    ) {}

    /**
     * Exception for workflow step failures.
     */
    public static class WorkflowStepException extends RuntimeException {
        private final String stepId;
        private final AgentResult.AgentError error;

        public WorkflowStepException(String stepId, AgentResult.AgentError error) {
            super("Workflow step " + stepId + " failed: " + (error != null ? error.message() : "Unknown error"));
            this.stepId = stepId;
            this.error = error;
        }

        public String getStepId() {
            return stepId;
        }

        public AgentResult.AgentError getError() {
            return error;
        }
    }
}
