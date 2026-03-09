package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Central configuration registry for Virtual-Org.
 *
 * <p>
 * <b>Purpose</b><br>
 * Manages all configuration types (Organizations, Departments, Agents, Actions,
 * Tasks, Personas, Interactions, Workflows, Results, Lifecycle) with support
 * for:
 * <ul>
 * <li>Hot-reload of configuration changes</li>
 * <li>Validation and schema enforcement</li>
 * <li>Variable interpolation</li>
 * <li>Configuration change notifications</li>
 * <li>Multi-tenant isolation</li>
 * </ul>
 *
 * <p>
 * <b>Configuration Types</b><br>
 * <pre>
 * Kind              | Class              | Description
 * ------------------|--------------------|---------------------------------
 * Organization      | OrganizationConfig | Root organization definition
 * Department        | DepartmentConfig   | Department with KPIs, agents
 * Agent             | VirtualOrgAgentConfig        | Agent with AI, tools, behavior
 * Action            | ActionConfig       | Executable action definition
 * TaskDefinition    | TaskConfig         | Task type with SLA, lifecycle
 * Persona           | PersonaConfig      | Agent personality and behavior
 * Interaction       | InteractionConfig  | Inter-entity communication
 * Workflow          | WorkflowConfig     | Multi-step process definition
 * ResultProcessor   | ResultConfig       | Result handling pipeline
 * AgentLifecycle    | AgentLifecycleConfig | Agent lifecycle management
 * </pre>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConfigRegistry registry = ConfigRegistry.builder()
 *     .configDirectory(Path.of("config"))
 *     .enableHotReload(true)
 *     .build();
 *
 * // Load all configurations
 * registry.loadAll().get();
 *
 * // Get specific configuration
 * Optional<VirtualOrgAgentConfig> agent = registry.getAgent("engineering", "senior-engineer");
 *
 * // Subscribe to changes
 * registry.onConfigChange(VirtualOrgAgentConfig.class, (oldConfig, newConfig) -> {
 *     // Handle agent configuration change
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Central configuration registry with hot-reload
 * @doc.layer core
 * @doc.pattern Registry
 */
public class ConfigRegistry implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigRegistry.class);

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^:}]+)(?::([^}]*))?\\}");
    private static final Pattern SYS_PROP_PATTERN = Pattern.compile("\\$\\{sys:([^:}]+)(?::([^}]*))?\\}");

    // Configuration storage by namespace and name
    private final ConcurrentMap<String, OrganizationConfig> organizations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, DepartmentConfig>> departments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, VirtualOrgAgentConfig>> agents = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, ActionConfig>> actions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, TaskConfig>> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, PersonaConfig>> personas = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, InteractionConfig>> interactions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, WorkflowConfig>> workflows = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, ResultConfig>> resultProcessors = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, AgentLifecycleConfig>> lifecycles = new ConcurrentHashMap<>();

    // Change listeners by config type
    private final Map<Class<?>, List<ConfigChangeListener<?>>> changeListeners = new ConcurrentHashMap<>();

    // File watching for hot-reload
    private final WatchService watchService;
    private final ScheduledExecutorService watchExecutor;
    private volatile boolean watching = false;

    private final Path configDirectory;
    private final ObjectMapper yamlMapper;
    private final Executor executor;
    private final boolean hotReloadEnabled;

    private ConfigRegistry(Builder builder) throws IOException {
        this.configDirectory = builder.configDirectory;
        this.executor = builder.executor != null ? builder.executor : Executors.newCachedThreadPool();
        this.hotReloadEnabled = builder.hotReloadEnabled;
        this.yamlMapper = createYamlMapper();

        if (hotReloadEnabled) {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watchExecutor = Executors.newSingleThreadScheduledExecutor();
        } else {
            this.watchService = null;
            this.watchExecutor = null;
        }
    }

    /**
     * Creates a builder for ConfigRegistry.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    private ObjectMapper createYamlMapper() {
        return new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    // ========================================================================
    // Loading Methods
    // ========================================================================
    /**
     * Loads all configurations from the config directory.
     *
     * @return Promise that completes when all configs are loaded
     */
    public Promise<Void> loadAll() {
        return Promise.ofBlocking(executor, () -> {
            LOG.info("Loading all configurations from: {}", configDirectory);

            // Load organizations
            loadConfigs("organizations", "Organization", OrganizationConfig.class,
                    config -> organizations.put(config.metadata().namespace(), config));

            // Load departments
            loadConfigs("departments", "Department", DepartmentConfig.class,
                    config -> departments
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load agents
            loadConfigs("agents", "Agent", VirtualOrgAgentConfig.class,
                    config -> agents
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load actions
            loadConfigs("actions", "Action", ActionConfig.class,
                    config -> actions
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load tasks
            loadConfigs("tasks", "TaskDefinition", TaskConfig.class,
                    config -> tasks
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load personas
            loadConfigs("personas", "Persona", PersonaConfig.class,
                    config -> personas
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load interactions
            loadConfigs("interactions", "Interaction", InteractionConfig.class,
                    config -> interactions
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load workflows
            loadConfigs("workflows", "Workflow", WorkflowConfig.class,
                    config -> workflows
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load result processors
            loadConfigs("results", "ResultProcessor", ResultConfig.class,
                    config -> resultProcessors
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            // Load lifecycle configs
            loadConfigs("lifecycle", "AgentLifecycle", AgentLifecycleConfig.class,
                    config -> lifecycles
                            .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                            .put(config.metadata().name(), config));

            LOG.info("Configuration loading complete. Organizations: {}, Departments: {}, Agents: {}, "
                    + "Actions: {}, Tasks: {}, Personas: {}, Interactions: {}, Workflows: {}, "
                    + "ResultProcessors: {}, Lifecycles: {}",
                    organizations.size(),
                    departments.values().stream().mapToInt(Map::size).sum(),
                    agents.values().stream().mapToInt(Map::size).sum(),
                    actions.values().stream().mapToInt(Map::size).sum(),
                    tasks.values().stream().mapToInt(Map::size).sum(),
                    personas.values().stream().mapToInt(Map::size).sum(),
                    interactions.values().stream().mapToInt(Map::size).sum(),
                    workflows.values().stream().mapToInt(Map::size).sum(),
                    resultProcessors.values().stream().mapToInt(Map::size).sum(),
                    lifecycles.values().stream().mapToInt(Map::size).sum());

            // Start file watching if enabled
            if (hotReloadEnabled) {
                startWatching();
            }

            return null;
        });
    }

    private <T> void loadConfigs(String subdirectory, String kind, Class<T> configClass, Consumer<T> registrar) {
        Path subDir = configDirectory.resolve(subdirectory);
        if (!Files.exists(subDir)) {
            LOG.debug("Config subdirectory does not exist: {}", subDir);
            return;
        }

        try (Stream<Path> paths = Files.walk(subDir)) {
            paths.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String resolved = resolveVariables(content);

                            // Check if this file matches the expected kind
                            if (content.contains("kind: " + kind)) {
                                T config = yamlMapper.readValue(resolved, configClass);
                                registrar.accept(config);
                                LOG.debug("Loaded {} from: {}", kind, path);
                            }
                        } catch (Exception e) {
                            LOG.warn("Failed to load config from {}: {}", path, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            LOG.warn("Failed to scan config directory {}: {}", subDir, e.getMessage());
        }
    }

    // ========================================================================
    // Getter Methods
    // ========================================================================
    /**
     * Gets an organization by namespace.
     */
    public Optional<OrganizationConfig> getOrganization(String namespace) {
        return Optional.ofNullable(organizations.get(namespace));
    }

    /**
     * Gets all organizations.
     */
    public Collection<OrganizationConfig> getAllOrganizations() {
        return Collections.unmodifiableCollection(organizations.values());
    }

    /**
     * Gets a department by namespace and name.
     */
    public Optional<DepartmentConfig> getDepartment(String namespace, String name) {
        return Optional.ofNullable(departments.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all departments in a namespace.
     */
    public Collection<DepartmentConfig> getDepartments(String namespace) {
        return Collections.unmodifiableCollection(
                departments.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets an agent by namespace and name.
     */
    public Optional<VirtualOrgAgentConfig> getAgent(String namespace, String name) {
        return Optional.ofNullable(agents.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all agents in a namespace.
     */
    public Collection<VirtualOrgAgentConfig> getAgents(String namespace) {
        return Collections.unmodifiableCollection(
                agents.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets an action by namespace and name.
     */
    public Optional<ActionConfig> getAction(String namespace, String name) {
        return Optional.ofNullable(actions.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all actions in a namespace.
     */
    public Collection<ActionConfig> getActions(String namespace) {
        return Collections.unmodifiableCollection(
                actions.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets a task definition by namespace and name.
     */
    public Optional<TaskConfig> getTask(String namespace, String name) {
        return Optional.ofNullable(tasks.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all task definitions in a namespace.
     */
    public Collection<TaskConfig> getTasks(String namespace) {
        return Collections.unmodifiableCollection(
                tasks.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets a persona by namespace and name.
     */
    public Optional<PersonaConfig> getPersona(String namespace, String name) {
        return Optional.ofNullable(personas.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all personas in a namespace.
     */
    public Collection<PersonaConfig> getPersonas(String namespace) {
        return Collections.unmodifiableCollection(
                personas.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets an interaction by namespace and name.
     */
    public Optional<InteractionConfig> getInteraction(String namespace, String name) {
        return Optional.ofNullable(interactions.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all interactions in a namespace.
     */
    public Collection<InteractionConfig> getInteractions(String namespace) {
        return Collections.unmodifiableCollection(
                interactions.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets a workflow by namespace and name.
     */
    public Optional<WorkflowConfig> getWorkflow(String namespace, String name) {
        return Optional.ofNullable(workflows.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all workflows in a namespace.
     */
    public Collection<WorkflowConfig> getWorkflows(String namespace) {
        return Collections.unmodifiableCollection(
                workflows.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets a result processor by namespace and name.
     */
    public Optional<ResultConfig> getResultProcessor(String namespace, String name) {
        return Optional.ofNullable(resultProcessors.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all result processors in a namespace.
     */
    public Collection<ResultConfig> getResultProcessors(String namespace) {
        return Collections.unmodifiableCollection(
                resultProcessors.getOrDefault(namespace, Map.of()).values());
    }

    /**
     * Gets an agent lifecycle config by namespace and name.
     */
    public Optional<AgentLifecycleConfig> getLifecycle(String namespace, String name) {
        return Optional.ofNullable(lifecycles.getOrDefault(namespace, Map.of()).get(name));
    }

    /**
     * Gets all lifecycle configs in a namespace.
     */
    public Collection<AgentLifecycleConfig> getLifecycles(String namespace) {
        return Collections.unmodifiableCollection(
                lifecycles.getOrDefault(namespace, Map.of()).values());
    }

    // ========================================================================
    // Registration Methods (for programmatic config)
    // ========================================================================
    /**
     * Registers an organization configuration programmatically.
     */
    public void registerOrganization(OrganizationConfig config) {
        OrganizationConfig old = organizations.put(config.metadata().namespace(), config);
        notifyChange(OrganizationConfig.class, old, config);
    }

    /**
     * Registers a department configuration programmatically.
     */
    public void registerDepartment(DepartmentConfig config) {
        DepartmentConfig old = departments
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(DepartmentConfig.class, old, config);
    }

    /**
     * Registers an agent configuration programmatically.
     */
    public void registerAgent(VirtualOrgAgentConfig config) {
        VirtualOrgAgentConfig old = agents
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(VirtualOrgAgentConfig.class, old, config);
    }

    /**
     * Registers an action configuration programmatically.
     */
    public void registerAction(ActionConfig config) {
        ActionConfig old = actions
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(ActionConfig.class, old, config);
    }

    /**
     * Registers a task configuration programmatically.
     */
    public void registerTask(TaskConfig config) {
        TaskConfig old = tasks
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(TaskConfig.class, old, config);
    }

    /**
     * Registers a persona configuration programmatically.
     */
    public void registerPersona(PersonaConfig config) {
        PersonaConfig old = personas
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(PersonaConfig.class, old, config);
    }

    /**
     * Registers an interaction configuration programmatically.
     */
    public void registerInteraction(InteractionConfig config) {
        InteractionConfig old = interactions
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(InteractionConfig.class, old, config);
    }

    /**
     * Registers a workflow configuration programmatically.
     */
    public void registerWorkflow(WorkflowConfig config) {
        WorkflowConfig old = workflows
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(WorkflowConfig.class, old, config);
    }

    /**
     * Registers a result processor configuration programmatically.
     */
    public void registerResultProcessor(ResultConfig config) {
        ResultConfig old = resultProcessors
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(ResultConfig.class, old, config);
    }

    /**
     * Registers a lifecycle configuration programmatically.
     */
    public void registerLifecycle(AgentLifecycleConfig config) {
        AgentLifecycleConfig old = lifecycles
                .computeIfAbsent(config.metadata().namespace(), k -> new ConcurrentHashMap<>())
                .put(config.metadata().name(), config);
        notifyChange(AgentLifecycleConfig.class, old, config);
    }

    // ========================================================================
    // Change Notification
    // ========================================================================
    /**
     * Registers a listener for configuration changes.
     *
     * @param configClass the configuration class to listen for
     * @param listener the change listener
     * @param <T> the configuration type
     */
    @SuppressWarnings("unchecked")
    public <T> void onConfigChange(Class<T> configClass, ConfigChangeListener<T> listener) {
        changeListeners
                .computeIfAbsent(configClass, k -> new CopyOnWriteArrayList<>())
                .add((ConfigChangeListener<?>) listener);
    }

    @SuppressWarnings("unchecked")
    private <T> void notifyChange(Class<T> configClass, T oldConfig, T newConfig) {
        List<ConfigChangeListener<?>> listeners = changeListeners.get(configClass);
        if (listeners != null) {
            for (ConfigChangeListener<?> listener : listeners) {
                try {
                    ((ConfigChangeListener<T>) listener).onConfigChange(oldConfig, newConfig);
                } catch (Exception e) {
                    LOG.error("Error in config change listener", e);
                }
            }
        }
    }

    // ========================================================================
    // Hot Reload
    // ========================================================================
    private void startWatching() throws IOException {
        if (watching) {
            return;
        }

        // Register all subdirectories
        try (Stream<Path> paths = Files.walk(configDirectory)) {
            paths.filter(Files::isDirectory).forEach(dir -> {
                try {
                    dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                } catch (IOException e) {
                    LOG.warn("Failed to register directory for watching: {}", dir);
                }
            });
        }

        watching = true;
        watchExecutor.scheduleWithFixedDelay(this::pollWatchEvents, 1, 1, TimeUnit.SECONDS);
        LOG.info("Hot-reload enabled, watching: {}", configDirectory);
    }

    private void pollWatchEvents() {
        try {
            WatchKey key = watchService.poll();
            if (key == null) {
                return;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path changed = ((Path) key.watchable()).resolve(pathEvent.context());

                if (changed.toString().endsWith(".yaml") || changed.toString().endsWith(".yml")) {
                    LOG.info("Configuration file changed: {}", changed);
                    reloadFile(changed);
                }
            }

            key.reset();
        } catch (Exception e) {
            LOG.error("Error polling watch events", e);
        }
    }

    private void reloadFile(Path path) {
        try {
            String content = Files.readString(path);
            String resolved = resolveVariables(content);

            // Determine kind and reload
            if (content.contains("kind: Organization")) {
                OrganizationConfig config = yamlMapper.readValue(resolved, OrganizationConfig.class);
                registerOrganization(config);
            } else if (content.contains("kind: Department")) {
                DepartmentConfig config = yamlMapper.readValue(resolved, DepartmentConfig.class);
                registerDepartment(config);
            } else if (content.contains("kind: Agent")) {
                VirtualOrgAgentConfig config = yamlMapper.readValue(resolved, VirtualOrgAgentConfig.class);
                registerAgent(config);
            } else if (content.contains("kind: Action")) {
                ActionConfig config = yamlMapper.readValue(resolved, ActionConfig.class);
                registerAction(config);
            } else if (content.contains("kind: TaskDefinition")) {
                TaskConfig config = yamlMapper.readValue(resolved, TaskConfig.class);
                registerTask(config);
            } else if (content.contains("kind: Persona")) {
                PersonaConfig config = yamlMapper.readValue(resolved, PersonaConfig.class);
                registerPersona(config);
            } else if (content.contains("kind: Interaction")) {
                InteractionConfig config = yamlMapper.readValue(resolved, InteractionConfig.class);
                registerInteraction(config);
            } else if (content.contains("kind: Workflow")) {
                WorkflowConfig config = yamlMapper.readValue(resolved, WorkflowConfig.class);
                registerWorkflow(config);
            } else if (content.contains("kind: ResultProcessor")) {
                ResultConfig config = yamlMapper.readValue(resolved, ResultConfig.class);
                registerResultProcessor(config);
            } else if (content.contains("kind: AgentLifecycle")) {
                AgentLifecycleConfig config = yamlMapper.readValue(resolved, AgentLifecycleConfig.class);
                registerLifecycle(config);
            }

            LOG.info("Reloaded configuration: {}", path);
        } catch (Exception e) {
            LOG.error("Failed to reload configuration: {}", path, e);
        }
    }

    // ========================================================================
    // Variable Resolution
    // ========================================================================
    private String resolveVariables(String content) {
        String result = content;

        // Resolve environment variables
        Matcher envMatcher = ENV_VAR_PATTERN.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (envMatcher.find()) {
            String varName = envMatcher.group(1);
            String defaultValue = envMatcher.group(2);

            // Skip if it's a system property pattern
            if (varName.startsWith("sys:")) {
                continue;
            }

            String value = System.getenv(varName);
            if (value == null) {
                value = defaultValue != null ? defaultValue : "";
            }
            envMatcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        envMatcher.appendTail(sb);
        result = sb.toString();

        // Resolve system properties
        Matcher sysMatcher = SYS_PROP_PATTERN.matcher(result);
        sb = new StringBuffer();
        while (sysMatcher.find()) {
            String propName = sysMatcher.group(1);
            String defaultValue = sysMatcher.group(2);
            String value = System.getProperty(propName, defaultValue != null ? defaultValue : "");
            sysMatcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        sysMatcher.appendTail(sb);
        result = sb.toString();

        return result;
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================
    @Override
    public void close() {
        watching = false;
        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                LOG.warn("Error closing watch service", e);
            }
        }
    }

    // ========================================================================
    // Builder
    // ========================================================================
    /**
     * Builder for ConfigRegistry.
     */
    public static class Builder {

        private Path configDirectory;
        private Executor executor;
        private boolean hotReloadEnabled = false;

        /**
         * Sets the configuration directory.
         */
        public Builder configDirectory(Path path) {
            this.configDirectory = path;
            return this;
        }

        /**
         * Sets a custom executor.
         */
        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        /**
         * Enables hot-reload of configurations.
         */
        public Builder enableHotReload(boolean enabled) {
            this.hotReloadEnabled = enabled;
            return this;
        }

        /**
         * Builds the ConfigRegistry.
         */
        public ConfigRegistry build() throws IOException {
            Objects.requireNonNull(configDirectory, "configDirectory is required");
            return new ConfigRegistry(this);
        }
    }

    /**
     * Listener interface for configuration changes.
     *
     * @param <T> the configuration type
     */
    @FunctionalInterface
    public interface ConfigChangeListener<T> {

        void onConfigChange(T oldConfig, T newConfig);
    }
}
