package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.agent.AIAgent;
import com.ghatana.products.yappc.domain.agent.AIAgentContext;
import com.ghatana.products.yappc.domain.task.TaskDefinition;
import com.ghatana.products.yappc.domain.task.TaskExecutionContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        AIAgentContext agentContext = createAgentContext(context, task);

        // Execute agent with proper type handling
        @SuppressWarnings("unchecked")
        AIAgent<TInput, ?> typedAgent = (AIAgent<TInput, ?>) agent;

        return typedAgent.execute(input, agentContext)
                .map(agentResult -> {
                    if (agentResult.success()) {
                        @SuppressWarnings("unchecked")
                        TOutput output = (TOutput) agentResult.data();
                        return output;
                    } else {
                        throw new TaskExecutionException(
                            "Agent execution failed: " + (agentResult.error() != null ? agentResult.error().message() : "unknown"),
                            task.id(),
                            agentName
                        );
                    }
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
    private AIAgentContext createAgentContext(
            @NotNull TaskExecutionContext taskContext,
            @NotNull TaskDefinition task
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskId", task.id());
        metadata.put("taskDomain", task.domain());
        metadata.put("taskPhase", task.phase());
        metadata.putAll(taskContext.metadata());

        if (taskContext.projectId() != null) {
            metadata.put("projectId", taskContext.projectId());
        }

        String organizationId = taskContext.projectId() != null ? taskContext.projectId() : "default-org";

        return AIAgentContext.builder()
                .userId(taskContext.userId())
                .workspaceId(organizationId)
                .requestId(taskContext.traceId() != null ? taskContext.traceId() : "req-" + task.id())
                .tenantId(taskContext.tenantId())
                .organizationId(organizationId)
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
