package com.ghatana.products.yappc.domain.task;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all development tasks with multi-source loading.
 *
 * @doc.type class
 * @doc.purpose Task definition registry
 * @doc.layer product
 * @doc.pattern Registry
 */
public class TaskRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TaskRegistry.class);

    private final Map<String, TaskDefinition> tasks = new ConcurrentHashMap<>();
    private final List<TaskDefinitionProvider> providers;

    public TaskRegistry(@NotNull List<TaskDefinitionProvider> providers) {
        this.providers = new ArrayList<>(providers);
        // Sort by priority (higher first)
        this.providers.sort(Comparator.comparingInt(TaskDefinitionProvider::getPriority).reversed());
    }

    /**
     * Loads all tasks from all providers.
     */
    public void loadAllTasks() {
        LOG.info("Loading task definitions from {} providers", providers.size());

        for (TaskDefinitionProvider provider : providers) {
            try {
                provider.loadTasks().whenComplete((taskList, error) -> {
                    if (error != null) {
                        LOG.error("Failed to load tasks from provider: {}", provider.getName(), error);
                        return;
                    }

                    for (TaskDefinition task : taskList) {
                        register(task);
                    }

                    LOG.info("Loaded {} tasks from provider: {}", taskList.size(), provider.getName());
                });
            } catch (Exception e) {
                LOG.error("Error loading from provider: {}", provider.getName(), e);
            }
        }

        LOG.info("Task registry loaded with {} tasks", tasks.size());
    }

    /**
     * Registers a task definition.
     *
     * @param task Task definition to register
     */
    public synchronized void register(@NotNull TaskDefinition task) {
        TaskDefinition existing = tasks.put(task.id(), task);

        if (existing != null) {
            LOG.warn("Overriding existing task: {} (old version from domain: {}, new from: {})",
                    task.id(), existing.domain(), task.domain());
        }

        LOG.debug("Registered task: {} with capabilities: {}", task.id(), task.requiredCapabilities());
    }

    /**
     * Gets task definition by ID.
     *
     * @param taskId Task ID
     * @return Optional task definition
     */
    @NotNull
    public Optional<TaskDefinition> getTask(@NotNull String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * Gets all registered tasks.
     *
     * @return List of all task definitions
     */
    @NotNull
    public List<TaskDefinition> getAllTasks() {
        return new ArrayList<>(tasks.values());
    }

    /**
     * Gets tasks by domain.
     *
     * @param domain Domain to filter by
     * @return List of matching tasks
     */
    @NotNull
    public List<TaskDefinition> getTasksByDomain(@NotNull String domain) {
        return tasks.values().stream()
                .filter(task -> domain.equals(task.domain()))
                .toList();
    }

    /**
     * Gets tasks by phase.
     *
     * @param phase SDLC phase to filter by
     * @return List of matching tasks
     */
    @NotNull
    public List<TaskDefinition> getTasksByPhase(@NotNull SDLCPhase phase) {
        return tasks.values().stream()
                .filter(task -> phase.equals(task.phase()))
                .toList();
    }

    /**
     * Searches tasks by query string.
     *
     * @param query Search query
     * @return List of matching tasks
     */
    @NotNull
    public List<TaskDefinition> searchTasks(@NotNull String query) {
        String lowerQuery = query.toLowerCase();
        return tasks.values().stream()
                .filter(task ->
                        task.id().toLowerCase().contains(lowerQuery) ||
                        task.name().toLowerCase().contains(lowerQuery) ||
                        task.description().toLowerCase().contains(lowerQuery)
                )
                .toList();
    }

    /**
     * Unregisters a task.
     *
     * @param taskId Task ID to remove
     * @return true if removed
     */
    public synchronized boolean unregister(@NotNull String taskId) {
        TaskDefinition removed = tasks.remove(taskId);

        if (removed != null) {
            LOG.info("Unregistered task: {}", taskId);
            return true;
        }

        return false;
    }

    /**
     * Reloads all tasks from providers.
     */
    public void reload() {
        LOG.info("Reloading task registry");
        tasks.clear();
        loadAllTasks();
    }

    /**
     * Gets registry statistics.
     *
     * @return Task count
     */
    public int getTaskCount() {
        return tasks.size();
    }

    /**
     * Checks if a task is registered.
     *
     * @param taskId Task ID
     * @return true if registered
     */
    public boolean hasTask(@NotNull String taskId) {
        return tasks.containsKey(taskId);
    }
}
