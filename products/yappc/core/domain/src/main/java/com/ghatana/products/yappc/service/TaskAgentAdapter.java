package com.ghatana.products.yappc.service;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.products.yappc.domain.agent.AIAgent;
import com.ghatana.products.yappc.domain.task.TaskDefinition;
import com.ghatana.products.yappc.domain.task.TaskExecutionContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter that connects YAPPC agents to the task service orchestrator.
 * Translates between task execution context and agent execution context.
 *
 * @doc.type class
 * @doc.purpose Adapter between task service and YAPPC agents
 * @doc.layer product
 * @doc.pattern Adapter, Bridge
 */
public class TaskAgentAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(TaskAgentAdapter.class);

    private final AIAgent<?, ?> agent;

    public TaskAgentAdapter(@NotNull AIAgent<?, ?> agent) {
        this.agent = agent;
    }

    /**
     * Executes a task using the wrapped agent.
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
        String agentName = agent.getMetadata().name().getDisplayName();
        LOG.debug("Executing task {} using agent {}", task.id(), agentName);

        // Create agent context from task context
        AgentContext agentContext = createAgentContext(context, task);

        // Execute agent with proper type handling
        @SuppressWarnings("unchecked")
        AIAgent<TInput, ?> typedAgent = (AIAgent<TInput, ?>) agent;

        return typedAgent.process(input, agentContext)
                .map(agentResult -> {
                    // Extract the output from agent result
                    if (agentResult instanceof Map) {
                        @SuppressWarnings("unchecked")
                        TOutput output = (TOutput) agentResult;
                        return output;
                    }

                    // Handle AgentResult wrapper
                    if (agentResult instanceof com.ghatana.products.yappc.domain.agent.AgentResult) {
                        @SuppressWarnings("unchecked")
                        com.ghatana.products.yappc.domain.agent.AgentResult<?> result =
                            (com.ghatana.products.yappc.domain.agent.AgentResult<?>) agentResult;

                        if (result.success()) {
                            @SuppressWarnings("unchecked")
                            TOutput output = (TOutput) result.data();
                            return output;
                        } else {
                            throw new TaskExecutionException(
                                "Agent execution failed: " + (result.error() != null ? result.error().message() : "unknown"),
                                task.id(),
                                agentName
                            );
                        }
                    }

                    // Fallback: wrap in map
                    Map<String, Object> resultMap = new HashMap<>();
                    resultMap.put("taskId", task.id());
                    resultMap.put("agentName", agentName);
                    resultMap.put("status", "completed");
                    resultMap.put("result", agentResult);

                    @SuppressWarnings("unchecked")
                    TOutput output = (TOutput) resultMap;
                    return output;
                })
                .mapException(error -> new TaskExecutionException(
                    "Agent execution failed: " + error.getMessage(),
                    task.id(),
                    agentName,
                    error
                ));
    }

    /**
     * Creates an agent context from task execution context.
     */
    @NotNull
    private AgentContext createAgentContext(
            @NotNull TaskExecutionContext taskContext,
            @NotNull TaskDefinition task
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", taskContext.userId());
        metadata.put("tenantId", taskContext.tenantId());
        metadata.put("traceId", taskContext.traceId());
        metadata.put("taskId", task.id());
        metadata.put("taskDomain", task.domain());
        metadata.put("taskPhase", task.phase());
        metadata.putAll(taskContext.metadata());

        if (taskContext.projectId() != null) {
            metadata.put("projectId", taskContext.projectId());
        }

        String organizationId = taskContext.projectId() != null ? taskContext.projectId() : "default-org";

        Map<String, Object> config = new HashMap<>();
        config.put("taskId", task.id());
        config.put("organizationId", organizationId);

        return AgentContext.builder()
                .turnId(taskContext.traceId() != null ? taskContext.traceId() : "turn-" + task.id())
                .agentId(agent.getId())
                .tenantId(taskContext.tenantId())
                .userId(taskContext.userId())
                .startTime(Instant.now())
                .memoryStore(MemoryStore.noOp())
                .config(config)
                .metadata(metadata)
                .build();
    }

    /**
     * Returns the wrapped agent.
     */
    @NotNull
    public AIAgent<?, ?> getAgent() {
        return agent;
    }
}
