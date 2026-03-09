package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.agent.AgentMetadata;
import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import com.ghatana.products.yappc.domain.agent.AIAgent;
import com.ghatana.products.yappc.domain.task.TaskDefinition;
import com.ghatana.products.yappc.domain.task.TaskExecutionContext;
import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates task execution including agent selection and workflow coordination.
 *
 * @doc.type class
 * @doc.purpose Task orchestration and agent coordination
 * @doc.layer product
 * @doc.pattern Orchestrator
 */
public class TaskOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(TaskOrchestrator.class);

    private final AgentRegistry agentRegistry;
    private final CapabilityMatcher capabilityMatcher;

    public TaskOrchestrator(
            @NotNull AgentRegistry agentRegistry,
            @NotNull CapabilityMatcher capabilityMatcher
    ) {
        this.agentRegistry = agentRegistry;
        this.capabilityMatcher = capabilityMatcher;
    }

    /**
     * Executes a task by orchestrating required agents.
     *
     * @param task    Task definition
     * @param input   Task input
     * @param context Execution context
     * @param <TInput>  Input type
     * @param <TOutput> Output type
     * @return Promise of task output
     */
    @NotNull
    public <TInput, TOutput> Promise<TOutput> execute(
            @NotNull TaskDefinition task,
            @NotNull TInput input,
            @NotNull TaskExecutionContext context
    ) {
        LOG.debug("Orchestrating task: {} with capabilities: {}", task.id(), task.requiredCapabilities());

        // 1. Find capable agents
        List<AgentMetadata> capableAgents = capabilityMatcher.findCapableAgents(
                task.requiredCapabilities(),
                agentRegistry.getAllMetadata()
        );

        if (capableAgents.isEmpty()) {
            return Promise.ofException(new NoCapableAgentException(
                    "No agents found with capabilities: " + task.requiredCapabilities()
            ));
        }

        LOG.debug("Found {} capable agents for task {}", capableAgents.size(), task.id());

        // 2. Create execution plan
        ExecutionPlan plan = createExecutionPlan(task, capableAgents, input, context);

        // 3. Execute based on orchestration pattern
        return executePlan(plan, capableAgents);
    }

    /**
     * Creates an execution plan for the task.
     */
    @NotNull
    private <TInput> ExecutionPlan createExecutionPlan(
            @NotNull TaskDefinition task,
            @NotNull List<AgentMetadata> capableAgents,
            @NotNull TInput input,
            @NotNull TaskExecutionContext context
    ) {
        // Select the best agent based on capability scores
        AgentMetadata selectedAgent = capableAgents.get(0);

        OrchestrationPattern pattern = resolvePattern(context);

        LOG.debug("Selected agent: {} for task: {}", selectedAgent.name(), task.id());

        return new ExecutionPlan(
                task,
                selectedAgent,
                input,
                context,
                pattern
        );
    }

    private OrchestrationPattern resolvePattern(@NotNull TaskExecutionContext context) {
        Object raw = context.metadata().get("orchestrationPattern");
        if (raw instanceof String s) {
            try {
                return OrchestrationPattern.valueOf(s.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return OrchestrationPattern.SEQUENTIAL;
            }
        }
        return OrchestrationPattern.SEQUENTIAL;
    }

    /**
     * Executes the plan based on orchestration pattern.
     */
    @NotNull
    private <TOutput> Promise<TOutput> executePlan(
            @NotNull ExecutionPlan plan,
            @NotNull List<AgentMetadata> capableAgents
    ) {
        return switch (plan.pattern()) {
            case SEQUENTIAL -> executeSequential(plan);
            case PARALLEL -> executeParallel(plan, capableAgents);
            case PIPELINE -> executePipeline(plan, capableAgents);
            case CONDITIONAL -> executeConditional(plan);
        };
    }

    /**
     * Executes task sequentially.
     */
    @NotNull
    private <TOutput> Promise<TOutput> executeSequential(@NotNull ExecutionPlan plan) {
        LOG.debug("Executing task sequentially: {}", plan.task().id());

        AIAgent<?, ?> agent = agentRegistry.get(plan.agent().name());
        if (agent == null) {
            return Promise.ofException(new NoAgentFoundException(
                    "Agent not found in registry: " + plan.agent().name()));
        }

        // Create adapter and execute
        TaskAgentAdapter adapter = new TaskAgentAdapter(agent);
        return adapter.execute(plan.task(), plan.input(), plan.context());
    }

    /**
     * Executes task in parallel.
     */
    @NotNull
    private <TOutput> Promise<TOutput> executeParallel(
            @NotNull ExecutionPlan plan,
            @NotNull List<AgentMetadata> capableAgents
    ) {
        LOG.debug("Executing task in parallel: {}", plan.task().id());

        // Execute with all capable agents in parallel
        List<Promise<TOutput>> parallelExecutions = capableAgents.stream()
                .map(agentMeta -> {
                    AIAgent<?, ?> agent = agentRegistry.get(agentMeta.name());
                    if (agent == null) {
                        return Promise.<TOutput>ofException(
                                new NoAgentFoundException("Agent not found: " + agentMeta.name()));
                    }

                    TaskAgentAdapter adapter = new TaskAgentAdapter(agent);
                    @SuppressWarnings("unchecked")
                    Promise<TOutput> execution = (Promise<TOutput>) adapter.execute(plan.task(), plan.input(), plan.context());
                    return execution;
                })
                .toList();

        // Return the first successful result
        return firstSuccessful(parallelExecutions);
    }

    /**
     * Executes task as pipeline.
     */
    @NotNull
    private <TOutput> Promise<TOutput> executePipeline(
            @NotNull ExecutionPlan plan,
            @NotNull List<AgentMetadata> capableAgents
    ) {
        LOG.debug("Executing task as pipeline: {}", plan.task().id());

        if (capableAgents.isEmpty()) {
            return Promise.ofException(new NoAgentFoundException(
                    "No capable agents found for pipeline execution of task: " + plan.task().id()));
        }

        // Execute all candidates and return the first successful result.
        // (A sequential fallback chain would require a recovery primitive; this keeps it simple
        // and non-blocking using ActiveJ's Promises combinators.)
        List<Promise<TOutput>> candidates = capableAgents.stream()
                .map(agentMeta -> {
                    AIAgent<?, ?> agent = agentRegistry.get(agentMeta.name());
                    if (agent == null) {
                        return Promise.<TOutput>ofException(
                                new NoAgentFoundException("Agent not found: " + agentMeta.name()));
                    }
                    TaskAgentAdapter adapter = new TaskAgentAdapter(agent);
                    @SuppressWarnings("unchecked")
                    Promise<TOutput> execution = (Promise<TOutput>) adapter.execute(plan.task(), plan.input(), plan.context());
                    return execution;
                })
                .toList();

        return firstSuccessful(candidates);
    }

    @NotNull
    private static <T> Promise<T> firstSuccessful(@NotNull List<Promise<T>> promises) {
        if (promises.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("No promises provided"));
        }

        SettablePromise<T> result = new SettablePromise<>();
        AtomicInteger remaining = new AtomicInteger(promises.size());
        List<Exception> errors = new ArrayList<>(promises.size());

        for (Promise<T> promise : promises) {
            promise.whenComplete((value, error) -> {
                if (result.isComplete()) {
                    return;
                }

                if (error == null) {
                    result.set(value);
                    return;
                }

                if (error instanceof Exception exception) {
                    errors.add(exception);
                } else {
                    errors.add(new RuntimeException(error));
                }
                if (remaining.decrementAndGet() == 0 && !result.isComplete()) {
                    Exception last = errors.isEmpty() ? new RuntimeException("All executions failed") : errors.get(errors.size() - 1);
                    result.setException(last);
                }
            });
        }

        return result;
    }

    /**
     * Executes task conditionally.
     */
    @NotNull
    private <TOutput> Promise<TOutput> executeConditional(@NotNull ExecutionPlan plan) {
        LOG.debug("Executing task conditionally: {}", plan.task().id());

        Object rawInput = plan.input();
        if (rawInput instanceof Map<?, ?> rawMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> input = (Map<String, Object>) rawMap;

            Object condition = input.get("conditionCapability");
            if (!(condition instanceof String)) {
                condition = input.get("condition");
            }

            if (condition instanceof String conditionStr && !conditionStr.isBlank()) {
                List<String> required = List.of(conditionStr.trim());
                List<AgentMetadata> matchingAgents = capabilityMatcher.findCapableAgents(
                        required,
                        agentRegistry.getAllMetadata()
                );

                if (!matchingAgents.isEmpty()) {
                    AgentMetadata selectedAgent = matchingAgents.get(0);
                    AIAgent<?, ?> agent = agentRegistry.get(selectedAgent.name());
                    if (agent != null) {
                        TaskAgentAdapter adapter = new TaskAgentAdapter(agent);
                        return adapter.execute(plan.task(), input, plan.context());
                    }
                }
            }
        }

        // Fallback to sequential if no condition or agent found
        return executeSequential(plan);
    }

    /**
     * Execution plan for a task.
     */
    private record ExecutionPlan(
            TaskDefinition task,
            AgentMetadata agent,
            Object input,
            TaskExecutionContext context,
            OrchestrationPattern pattern
    ) {}

    /**
     * Orchestration patterns.
     */
    private enum OrchestrationPattern {
        SEQUENTIAL,
        PARALLEL,
        PIPELINE,
        CONDITIONAL
    }

    /**
     * Exception thrown when no capable agent is found.
     */
    public static class NoCapableAgentException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NoCapableAgentException(String message) {
            super(message);
        }
    }
}
