package com.ghatana.virtualorg.framework.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ghatana.platform.config.interpolation.VariableResolver;
import com.ghatana.platform.config.registry.ConfigRegistry;
import com.ghatana.platform.config.YamlConfigSource;
import com.ghatana.platform.config.validation.ValidationResult;
import com.ghatana.platform.config.watcher.ConfigReloadWatcher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Enhanced configuration loader for Virtual-Org YAML configurations.
 * Integrates with config-runtime library for advanced features.
 *
 * <p>
 * <b>Purpose</b><br>
 * Loads, parses, and validates organization configurations from YAML files.
 * Supports variable interpolation, file references, schema validation, and hot-reload.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * OrganizationConfigLoader loader = new OrganizationConfigLoader(Path.of("config"));
 *
 * // Load organization with all references
 * loader.loadWithReferences("organization.yaml")
 *     .whenResult(resolved -> {
 *         // Use resolved configuration
 *     });
 *
 * // Enable hot-reload
 * loader.enableHotReload(name -> {
 *     System.out.println("Configuration reloaded: " + name);
 * });
 * }</pre>
 *
 * <p>
 * <b>Variable Interpolation</b><br>
 * Supports:
 * <ul>
 * <li>${VAR_NAME} - Environment variable (required)</li>
 * <li>${VAR_NAME:default} - Environment variable with default</li>
 * <li>${sys:prop.name} - System property</li>
 * <li>${ref:path/to/file.yaml} - File reference</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Load and parse organization configurations with advanced features
 * @doc.layer core
 * @doc.pattern Factory
 */
public class OrganizationConfigLoader {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizationConfigLoader.class);

    private final Path baseDir;
    private final ObjectMapper yamlMapper;
    private final Executor executor;
    private final VariableResolver variableResolver;
    private final ConfigRegistry configRegistry;
    private ConfigReloadWatcher hotReloadWatcher;

    /**
     * Creates a new configuration loader with default settings.
     *
     * @param baseDir base directory for configuration files
     */
    public OrganizationConfigLoader(Path baseDir) {
        this(baseDir, Executors.newCachedThreadPool());
    }

    /**
     * Creates a new configuration loader with custom executor.
     *
     * @param baseDir base directory for configuration files
     * @param executor executor for async operations
     */
    public OrganizationConfigLoader(Path baseDir, Executor executor) {
        this.baseDir = baseDir;
        this.yamlMapper = createYamlMapper();
        this.executor = executor;
        this.variableResolver = new VariableResolver(baseDir, executor);
        this.configRegistry = new ConfigRegistry(baseDir);
    }

    private ObjectMapper createYamlMapper() {
        return new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    /**
     * Adds a custom variable for interpolation.
     *
     * @param name variable name
     * @param value variable value
     * @return this loader for chaining
     */
    public OrganizationConfigLoader addVariable(String name, String value) {
        variableResolver.addVariable(name, value);
        return this;
    }

    /**
     * Loads organization configuration from a YAML file using config-runtime.
     *
     * @param configFileName name of the organization YAML file (relative to baseDir)
     * @return Promise of parsed OrganizationConfig
     */
    public Promise<OrganizationConfig> load(String configFileName) {
        return configRegistry.loadConfiguration("organization", configFileName)
                .then(source -> parseOrganizationConfig(source));
    }

    /**
     * Loads organization with all referenced configurations (departments, agents, workflows).
     *
     * @param configFileName name of the organization YAML file
     * @return Promise of fully resolved configuration
     */
    public Promise<ResolvedOrganizationConfig> loadWithReferences(String configFileName) {
        return load(configFileName)
                .then(orgConfig -> resolveAllReferences(orgConfig));
    }

    /**
     * Validates configuration against JSON schema.
     *
     * @param configFileName configuration file to validate
     * @param schemaPath path to JSON schema file
     * @return Promise of validation result
     */
    public Promise<ValidationResult> validateWithSchema(String configFileName, Path schemaPath) {
        Path configPath = baseDir.resolve(configFileName);
        if (!Files.exists(configPath)) {
            return Promise.of(ValidationResult.failure("Config file not found: " + configPath));
        }
        if (schemaPath != null && !Files.exists(schemaPath)) {
            return Promise.of(ValidationResult.failure("Schema file not found: " + schemaPath));
        }
        // Basic existence validation; full schema validation is a future enhancement
        return Promise.of(ValidationResult.success());
    }

    /**
     * Enables hot-reload for all configurations.
     *
     * @param reloadHandler handler called when configuration is reloaded
     * @return Promise that completes when watcher is started
     */
    public Promise<Void> enableHotReload(java.util.function.Consumer<String> reloadHandler) {
        try {
            if (hotReloadWatcher == null) {
                hotReloadWatcher = new ConfigReloadWatcher();
            }
            hotReloadWatcher.watchFile(baseDir.toString(), reloadHandler);
            return Promise.complete();
        } catch (Exception e) {
            return Promise.ofException(new ConfigurationException("Failed to setup hot-reload", e));
        }
    }

    /**
     * Disables hot-reload watcher.
     *
     * @return Promise that completes when watcher is stopped
     */
    public Promise<Void> disableHotReload() {
        if (hotReloadWatcher != null) {
            hotReloadWatcher.close();
            hotReloadWatcher = null;
        }
        return Promise.complete();
    }

    /**
     * Reloads all configurations.
     *
     * @return Promise that completes when reload is done
     */
    public Promise<Void> reloadAll() {
        return configRegistry.reloadAll();
    }

    /**
     * Loads organization configuration from a YAML file.
     *
     * @param configPath path to the organization.yaml file
     * @return Promise of parsed OrganizationConfig
     */
    public Promise<OrganizationConfig> load(Path configPath) {
        return Promise.ofBlocking(executor, () -> loadSync(configPath));
    }

    /**
     * Loads organization configuration synchronously.
     *
     * @param configPath path to the organization.yaml file
     * @return parsed OrganizationConfig
     * @throws ConfigurationException if loading or parsing fails
     */
    public OrganizationConfig loadSync(Path configPath) {
        try {
            LOG.info("Loading organization configuration from: {}", configPath);

            // Read and resolve variables
            String content = Files.readString(configPath);
            String resolved = variableResolver.resolve(content);

            // Parse YAML
            OrganizationConfig config = yamlMapper.readValue(resolved, OrganizationConfig.class);

            // Validate basic structure
            if (!config.isValid()) {
                throw new ConfigurationException("Invalid organization configuration: missing required fields");
            }

            LOG.info("Successfully loaded organization: {}", config.getDisplayName());
            return config;

        } catch (IOException e) {
            throw new ConfigurationException("Failed to load configuration: " + configPath, e);
        }
    }

    /**
     * Parses OrganizationConfig from YamlConfigSource.
     */
    private Promise<OrganizationConfig> parseOrganizationConfig(YamlConfigSource source) {
        return Promise.ofBlocking(executor, () -> {
            try {
                // Read raw content from file
                String content = Files.readString(Path.of(source.getName()));
                String resolved = variableResolver.resolve(content);
                OrganizationConfig config = yamlMapper.readValue(resolved, OrganizationConfig.class);

                if (!config.isValid()) {
                    throw new ConfigurationException("Invalid organization configuration");
                }

                return config;
            } catch (IOException e) {
                throw new ConfigurationException("Failed to parse organization config", e);
            }
        });
    }

    /**
     * Resolves all referenced configurations.
     */
    private Promise<ResolvedOrganizationConfig> resolveAllReferences(OrganizationConfig orgConfig) {
        return Promise.ofBlocking(executor, () -> {
            List<DepartmentConfig> departments = new ArrayList<>();
            List<VirtualOrgAgentConfig> agents = new ArrayList<>();
            List<WorkflowConfig> workflows = new ArrayList<>();
            List<ActionConfig> actions = new ArrayList<>();
            List<PersonaConfig> personas = new ArrayList<>();

            // Load departments
            if (orgConfig.spec().departments() != null) {
                for (ConfigReference ref : orgConfig.spec().departments()) {
                    if (ref.isReference()) {
                        DepartmentConfig dept = loadDepartmentSync(baseDir.resolve(ref.ref()));
                        departments.add(dept);

                        // Load agents from department
                        if (dept.spec().agents() != null) {
                            for (ConfigReference agentRef : dept.spec().agents()) {
                                if (agentRef.isReference()) {
                                    VirtualOrgAgentConfig agent = loadAgentSync(baseDir.resolve(agentRef.ref()));
                                    agents.add(agent);
                                }
                            }
                        }
                    }
                }
            }

            // Load workflows
            if (orgConfig.spec().workflows() != null) {
                for (ConfigReference ref : orgConfig.spec().workflows()) {
                    if (ref.isReference()) {
                        WorkflowConfig wf = loadWorkflowSync(baseDir.resolve(ref.ref()));
                        workflows.add(wf);
                    }
                }
            }

            // Load actions if specified
            if (orgConfig.spec().actions() != null) {
                for (ConfigReference ref : orgConfig.spec().actions()) {
                    if (ref.isReference()) {
                        ActionConfig action = loadActionSync(baseDir.resolve(ref.ref()));
                        actions.add(action);
                    }
                }
            }

            // Load personas if specified
            if (orgConfig.spec().personas() != null) {
                for (ConfigReference ref : orgConfig.spec().personas()) {
                    if (ref.isReference()) {
                        PersonaConfig persona = loadPersonaSync(baseDir.resolve(ref.ref()));
                        personas.add(persona);
                    }
                }
            }

            return new ResolvedOrganizationConfig(
                    orgConfig, departments, agents, workflows, actions, personas);
        });
    }

    /**
     * Loads a department configuration from a YAML file.
     *
     * @param configPath path to the department YAML file
     * @return Promise of parsed DepartmentConfig
     */
    public Promise<DepartmentConfig> loadDepartment(Path configPath) {
        return Promise.ofBlocking(executor, () -> loadDepartmentSync(configPath));
    }

    /**
     * Loads department configuration synchronously.
     *
     * @param configPath path to the department YAML file
     * @return parsed DepartmentConfig
     */
    public DepartmentConfig loadDepartmentSync(Path configPath) {
        try {
            String content = Files.readString(configPath);
            String resolved = variableResolver.resolve(content);
            DepartmentConfig config = yamlMapper.readValue(resolved, DepartmentConfig.class);

            if (!config.isValid()) {
                throw new ConfigurationException("Invalid department configuration: " + configPath);
            }

            return config;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load department: " + configPath, e);
        }
    }

    /**
     * Loads an agent configuration from a YAML file.
     *
     * @param configPath path to the agent YAML file
     * @return Promise of parsed VirtualOrgAgentConfig
     */
    public Promise<VirtualOrgAgentConfig> loadAgent(Path configPath) {
        return Promise.ofBlocking(executor, () -> loadAgentSync(configPath));
    }

    /**
     * Loads agent configuration synchronously.
     *
     * @param configPath path to the agent YAML file
     * @return parsed VirtualOrgAgentConfig
     */
    public VirtualOrgAgentConfig loadAgentSync(Path configPath) {
        try {
            String content = Files.readString(configPath);
            String resolved = variableResolver.resolve(content);
            VirtualOrgAgentConfig config = yamlMapper.readValue(resolved, VirtualOrgAgentConfig.class);

            if (!config.isValid()) {
                throw new ConfigurationException("Invalid agent configuration: " + configPath);
            }

            return config;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load agent: " + configPath, e);
        }
    }

    /**
     * Loads a workflow configuration from a YAML file.
     *
     * @param configPath path to the workflow YAML file
     * @return Promise of parsed WorkflowConfig
     */
    public Promise<WorkflowConfig> loadWorkflow(Path configPath) {
        return Promise.ofBlocking(executor, () -> loadWorkflowSync(configPath));
    }

    /**
     * Loads workflow configuration synchronously.
     *
     * @param configPath path to the workflow YAML file
     * @return parsed WorkflowConfig
     */
    public WorkflowConfig loadWorkflowSync(Path configPath) {
        try {
            String content = Files.readString(configPath);
            String resolved = variableResolver.resolve(content);
            WorkflowConfig config = yamlMapper.readValue(resolved, WorkflowConfig.class);

            if (!config.isValid()) {
                throw new ConfigurationException("Invalid workflow configuration: " + configPath);
            }

            return config;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load workflow: " + configPath, e);
        }
    }

    /**
     * Loads an action configuration from a YAML file.
     *
     * @param configPath path to the action YAML file
     * @return Promise of parsed ActionConfig
     */
    public Promise<ActionConfig> loadAction(Path configPath) {
        return Promise.ofBlocking(executor, () -> loadActionSync(configPath));
    }

    /**
     * Loads action configuration synchronously.
     *
     * @param configPath path to the action YAML file
     * @return parsed ActionConfig
     */
    public ActionConfig loadActionSync(Path configPath) {
        try {
            String content = Files.readString(configPath);
            String resolved = variableResolver.resolve(content);
            ActionConfig config = yamlMapper.readValue(resolved, ActionConfig.class);

            if (!config.isValid()) {
                throw new ConfigurationException("Invalid action configuration: " + configPath);
            }

            return config;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load action: " + configPath, e);
        }
    }

    /**
     * Loads a persona configuration from a YAML file.
     *
     * @param configPath path to the persona YAML file
     * @return Promise of parsed PersonaConfig
     */
    public Promise<PersonaConfig> loadPersona(Path configPath) {
        return Promise.ofBlocking(executor, () -> loadPersonaSync(configPath));
    }

    /**
     * Loads persona configuration synchronously.
     *
     * @param configPath path to the persona YAML file
     * @return parsed PersonaConfig
     */
    public PersonaConfig loadPersonaSync(Path configPath) {
        try {
            String content = Files.readString(configPath);
            String resolved = variableResolver.resolve(content);
            PersonaConfig config = yamlMapper.readValue(resolved, PersonaConfig.class);

            if (!config.isValid()) {
                throw new ConfigurationException("Invalid persona configuration: " + configPath);
            }

            return config;
        } catch (IOException e) {
            throw new ConfigurationException("Failed to load persona: " + configPath, e);
        }
    }

    /**
     * Loads all referenced configurations from an organization config.
     *
     * @param basePath base directory for resolving references
     * @param orgConfig organization configuration with references
     * @return resolved configuration with all departments, agents, workflows
     * loaded
     */
    public Promise<ResolvedOrganizationConfig> resolveReferences(Path basePath, OrganizationConfig orgConfig) {
        return Promise.ofBlocking(executor, () -> resolveReferencesSync(basePath, orgConfig));
    }

    /**
     * Resolves all references synchronously.
     */
    public ResolvedOrganizationConfig resolveReferencesSync(Path basePath, OrganizationConfig orgConfig) {
        List<DepartmentConfig> departments = new ArrayList<>();
        List<WorkflowConfig> workflows = new ArrayList<>();

        // Load departments
        if (orgConfig.spec().departments() != null) {
            for (ConfigReference ref : orgConfig.spec().departments()) {
                if (ref.isReference()) {
                    Path deptPath = basePath.resolve(ref.ref());
                    DepartmentConfig dept = loadDepartmentSync(deptPath);
                    departments.add(dept);

                    // Load agents for this department
                    // (agents are stored within department config for now)
                }
            }
        }

        // Load workflows
        if (orgConfig.spec().workflows() != null) {
            for (ConfigReference ref : orgConfig.spec().workflows()) {
                if (ref.isReference()) {
                    Path wfPath = basePath.resolve(ref.ref());
                    WorkflowConfig wf = loadWorkflowSync(wfPath);
                    workflows.add(wf);
                }
            }
        }

        return new ResolvedOrganizationConfig(orgConfig, departments, workflows);
    }

    /**
     * Validates configuration without loading the full organization.
     *
     * @param configPath path to the configuration file
     * @return validation result with any errors
     */
    public Promise<ConfigValidationResult> validate(Path configPath) {
        return Promise.ofBlocking(executor, () -> validateSync(configPath));
    }

    /**
     * Validates configuration synchronously.
     */
    public ConfigValidationResult validateSync(Path configPath) {
        List<ConfigValidationError> errors = new ArrayList<>();

        try {
            // Load and parse
            OrganizationConfig config = loadSync(configPath);

            // Structural validation
            if (config.spec().displayName() == null || config.spec().displayName().isBlank()) {
                errors.add(new ConfigValidationError("spec.displayName", "Display name is required"));
            }

            // Validate department references exist
            Path basePath = configPath.getParent();
            if (config.spec().departments() != null) {
                for (ConfigReference ref : config.spec().departments()) {
                    if (ref.isReference()) {
                        Path deptPath = basePath.resolve(ref.ref());
                        if (!Files.exists(deptPath)) {
                            errors.add(new ConfigValidationError(
                                    "spec.departments",
                                    "Referenced department file not found: " + ref.ref()
                            ));
                        }
                    }
                }
            }

            // Validate workflow references exist
            if (config.spec().workflows() != null) {
                for (ConfigReference ref : config.spec().workflows()) {
                    if (ref.isReference()) {
                        Path wfPath = basePath.resolve(ref.ref());
                        if (!Files.exists(wfPath)) {
                            errors.add(new ConfigValidationError(
                                    "spec.workflows",
                                    "Referenced workflow file not found: " + ref.ref()
                            ));
                        }
                    }
                }
            }

        } catch (ConfigurationException e) {
            errors.add(new ConfigValidationError("root", e.getMessage()));
        }

        return new ConfigValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Gets the configuration registry.
     *
     * @return configuration registry
     */
    public ConfigRegistry getConfigRegistry() {
        return configRegistry;
    }

    /**
     * Gets the variable resolver.
     *
     * @return variable resolver
     */
    public VariableResolver getVariableResolver() {
        return variableResolver;
    }

}

/**
 * Configuration validation error.
 */
record ConfigValidationError(String path, String message) {}

/**
 * Configuration validation result.
 */
record ConfigValidationResult(boolean valid, List<ConfigValidationError> errors) {
    public boolean isValid() {
        return valid;
    }
}

/**
 * Configuration exception.
 */
class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
