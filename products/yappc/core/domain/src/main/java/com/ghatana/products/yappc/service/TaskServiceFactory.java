package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.agent.AgentRegistry;
import com.ghatana.products.yappc.domain.task.TaskDefinitionProvider;
import com.ghatana.products.yappc.domain.task.TaskRegistry;
import com.ghatana.products.yappc.domain.task.TaskService;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Factory for creating TaskService instances with all dependencies.
 *
 * @doc.type class
 * @doc.purpose Factory for task service initialization
 * @doc.layer product
 * @doc.pattern Factory, Builder
 */
public class TaskServiceFactory {

    private final AgentRegistry agentRegistry;
    private final Executor executor;
    private final List<TaskDefinitionProvider> providers;

    private TaskServiceFactory(
            @NotNull AgentRegistry agentRegistry,
            @NotNull Executor executor,
            @NotNull List<TaskDefinitionProvider> providers
    ) {
        this.agentRegistry = agentRegistry;
        this.executor = executor;
        this.providers = providers;
    }

    /**
     * Creates a new TaskService instance.
     */
    @NotNull
    public TaskService createTaskService() {
        // Create task registry
        TaskRegistry taskRegistry = new TaskRegistry(providers);

        // Load all tasks
        taskRegistry.loadAllTasks();

        // Create components
        CapabilityMatcher capabilityMatcher = new CapabilityMatcher();
        TaskOrchestrator orchestrator = new TaskOrchestrator(agentRegistry, capabilityMatcher);
        TaskValidator validator = new TaskValidator();

        // Create service
        return new TaskServiceImpl(taskRegistry, orchestrator, validator);
    }

    /**
     * Builder for TaskServiceFactory.
     */
    public static class Builder {
        private AgentRegistry agentRegistry;
        private Executor executor = ForkJoinPool.commonPool();
        private final List<TaskDefinitionProvider> providers = new ArrayList<>();

        public Builder agentRegistry(@NotNull AgentRegistry agentRegistry) {
            this.agentRegistry = agentRegistry;
            return this;
        }

        public Builder executor(@NotNull Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder addProvider(@NotNull TaskDefinitionProvider provider) {
            this.providers.add(provider);
            return this;
        }

        public Builder addYamlProvider(@NotNull Path configDirectory) {
            this.providers.add(new YamlTaskDefinitionProvider(configDirectory, executor));
            return this;
        }

        @NotNull
        public TaskServiceFactory build() {
            if (agentRegistry == null) {
                throw new IllegalStateException("AgentRegistry is required");
            }
            return new TaskServiceFactory(agentRegistry, executor, providers);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
