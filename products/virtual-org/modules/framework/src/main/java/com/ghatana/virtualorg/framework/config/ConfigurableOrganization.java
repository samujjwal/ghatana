package com.ghatana.virtualorg.framework.config;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.DepartmentType;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.agent.AgentRegistry;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Configuration-driven organization implementation.
 *
 * <p>
 * <b>Purpose</b><br>
 * Creates an organization entirely from YAML configuration files. Supports
 * dynamic loading of departments, agents, workflows, and interactions without
 * requiring Java code for each organizational element.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Option 1: Load from config file path
 * ConfigurableOrganization org = ConfigurableOrganization.fromConfig(
 *     TenantId.of("tenant-123"),
 *     Path.of("config/organization.yaml"),
 *     new AgentRegistry()
 * );
 *
 * // Option 2: Use pre-loaded configuration
 * OrganizationConfigLoader loader = new OrganizationConfigLoader();
 * ResolvedOrganizationConfig resolved = loader.resolveReferencesSync(
 *     configPath.getParent(),
 *     loader.loadSync(configPath)
 * );
 * ConfigurableOrganization org = new ConfigurableOrganization(
 *     TenantId.of("tenant-123"),
 *     resolved,
 *     new AgentRegistry()
 * );
 * }</pre>
 *
 * <p>
 * <b>Features</b><br>
 * <ul>
 * <li>Load organization structure from YAML</li>
 * <li>Dynamic department creation</li>
 * <li>Dynamic agent configuration with AI settings</li>
 * <li>Workflow registration from config</li>
 * <li>Hot-reload support (planned)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Configuration-driven organization implementation
 * @doc.layer core
 * @doc.pattern Factory
 */
public class ConfigurableOrganization extends AbstractOrganization {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurableOrganization.class);

    private final ResolvedOrganizationConfig resolvedConfig;
    private final Path configBasePath;
    private final List<ConfigurableDepartment> configurableDepartments;
    private final AgentRegistry agentRegistry;

    /**
     * Creates a configurable organization from resolved configuration.
     *
     * @param eventloop eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param resolvedConfig fully resolved configuration with all references
     * loaded
     * @param agentRegistry registry for creating domain-specific agents
     */
    public ConfigurableOrganization(Eventloop eventloop, TenantId tenantId, ResolvedOrganizationConfig resolvedConfig, AgentRegistry agentRegistry) {
        super(
                eventloop,
                tenantId,
                resolvedConfig.organization().getDisplayName(),
                resolvedConfig.organization().getDescription()
        );
        this.resolvedConfig = resolvedConfig;
        this.configBasePath = null;
        this.configurableDepartments = new CopyOnWriteArrayList<>();
        this.agentRegistry = agentRegistry != null ? agentRegistry : new AgentRegistry();

        initializeFromConfig();
    }

    /**
     * Creates a configurable organization from resolved configuration with
     * config path.
     *
     * @param eventloop eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param resolvedConfig fully resolved configuration
     * @param configBasePath base path for configuration files (for hot-reload)
     * @param agentRegistry registry for creating domain-specific agents
     */
    public ConfigurableOrganization(
            Eventloop eventloop,
            TenantId tenantId,
            ResolvedOrganizationConfig resolvedConfig,
            Path configBasePath,
            AgentRegistry agentRegistry) {
        super(
                eventloop,
                tenantId,
                resolvedConfig.organization().getDisplayName(),
                resolvedConfig.organization().getDescription()
        );
        this.resolvedConfig = resolvedConfig;
        this.configBasePath = configBasePath;
        this.configurableDepartments = new CopyOnWriteArrayList<>();
        this.agentRegistry = agentRegistry != null ? agentRegistry : new AgentRegistry();

        initializeFromConfig();
    }

    /**
     * Factory method to create organization from config file path.
     *
     * @param eventloop eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param configPath path to organization.yaml
     * @param agentRegistry registry for creating domain-specific agents
     * @return Promise of configured organization
     */
    public static Promise<ConfigurableOrganization> fromConfig(Eventloop eventloop, TenantId tenantId, Path configPath, AgentRegistry agentRegistry) {
        Path baseDir = configPath.getParent();
        OrganizationConfigLoader loader = new OrganizationConfigLoader(baseDir);

        return loader.load(configPath)
                .then(orgConfig -> loader.resolveReferences(baseDir, orgConfig))
                .map(resolved -> new ConfigurableOrganization(eventloop, tenantId, resolved, baseDir, agentRegistry));
    }

    /**
     * Synchronous factory method to create organization from config file.
     *
     * @param eventloop eventloop for this organization
     * @param tenantId tenant owning this organization
     * @param configPath path to organization.yaml
     * @param agentRegistry registry for creating domain-specific agents
     * @return configured organization
     */
    public static ConfigurableOrganization fromConfigSync(Eventloop eventloop, TenantId tenantId, Path configPath, AgentRegistry agentRegistry) {
        Path baseDir = configPath.getParent();
        OrganizationConfigLoader loader = new OrganizationConfigLoader(baseDir);
        OrganizationConfig orgConfig = loader.loadSync(configPath);
        ResolvedOrganizationConfig resolved = loader.resolveReferencesSync(
                baseDir,
                orgConfig
        );
        return new ConfigurableOrganization(eventloop, tenantId, resolved, baseDir, agentRegistry);
    }

    /**
     * Initializes organization from configuration.
     */
    private void initializeFromConfig() {
        LOG.info("Initializing organization from configuration: {}", getName());

        // Create and register departments
        for (DepartmentConfig deptConfig : resolvedConfig.departments()) {
            ConfigurableDepartment dept = createDepartment(deptConfig);
            registerDepartment(dept);
            configurableDepartments.add(dept);
            LOG.debug("Registered department: {}", dept.getName());
        }

        // Register workflows
        for (WorkflowConfig wfConfig : resolvedConfig.workflows()) {
            // WorkflowDefinition registration would go here
            LOG.debug("Registered workflow: {}", wfConfig.getName());
        }

        LOG.info("Organization initialized with {} departments and {} workflows",
                configurableDepartments.size(), resolvedConfig.workflows().size());
    }

    /**
     * Creates a department from configuration.
     */
    private ConfigurableDepartment createDepartment(DepartmentConfig config) {
        DepartmentType type = parseDepartmentType(config.spec().type());

        ConfigurableDepartment dept = new ConfigurableDepartment(
                this,
                config.spec().displayName(),
                type,
                config
        );

        // Create and register agents
        if (config.spec().agents() != null) {
            for (ConfigReference agentRef : config.spec().agents()) {
                if (agentRef.isReference() && configBasePath != null) {
                    // Load from file reference
                    Path agentPath = configBasePath.resolve(agentRef.ref());
                    try {
                        OrganizationConfigLoader agentLoader = new OrganizationConfigLoader(configBasePath);
                        VirtualOrgAgentConfig agentConfig = agentLoader.loadAgentSync(agentPath);
                        Agent agent = createAgent(agentConfig, dept.getName());
                        dept.registerAgent(agent);
                    } catch (Exception e) {
                        LOG.error("Failed to load agent from {}: {}", agentPath, e.getMessage());
                    }
                } else if (agentRef.isInline()) {
                    // Create from inline config (simplified)
                    Map<String, Object> inline = agentRef.inline();
                    String name = (String) inline.getOrDefault("displayName", "Unnamed Agent");
                    Agent agent = Agent.builder()
                            .id(name.toLowerCase().replace(" ", "-"))
                            .name(name)
                            .department(dept.getName())
                            .build();
                    dept.registerAgent(agent);
                }
            }
        }

        return dept;
    }

    /**
     * Creates an agent from configuration.
     */
    private Agent createAgent(VirtualOrgAgentConfig config, String departmentName) {
        String template = config.getTemplate();
        if (template != null && !template.isBlank()) {
            try {
                return agentRegistry.create(template, config);
            } catch (IllegalArgumentException e) {
                LOG.warn("No factory found for template '{}', falling back to generic agent. Error: {}", template, e.getMessage());
            }
        }

        Agent.Builder builder = Agent.builder()
                .id(config.getName())
                .name(config.spec().displayName())
                .department(departmentName);

        // Add capabilities
        if (config.spec().capabilities() != null && config.spec().capabilities().primary() != null) {
            builder.capabilities(
                    config.spec().capabilities().primary().toArray(String[]::new)
            );
        }

        return builder.build();
    }

    /**
     * Parses department type from string.
     */
    private DepartmentType parseDepartmentType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return DepartmentType.ENGINEERING; // Default
        }

        try {
            return DepartmentType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Custom type - default to ENGINEERING for now
            LOG.warn("Unknown department type: {}, using ENGINEERING", typeStr);
            return DepartmentType.ENGINEERING;
        }
    }

    /**
     * Gets the resolved configuration.
     */
    public ResolvedOrganizationConfig getResolvedConfig() {
        return resolvedConfig;
    }

    /**
     * Gets the original organization configuration.
     */
    public OrganizationConfig getOrganizationConfig() {
        return resolvedConfig.organization();
    }

    /**
     * Gets the configuration base path.
     */
    public Optional<Path> getConfigBasePath() {
        return Optional.ofNullable(configBasePath);
    }

    /**
     * Gets all configurable departments.
     */
    public List<ConfigurableDepartment> getConfigurableDepartments() {
        return Collections.unmodifiableList(configurableDepartments);
    }

    /**
     * Reloads configuration from files (hot-reload support).
     *
     * @return Promise indicating reload success
     */
    public Promise<Void> reloadConfig() {
        if (configBasePath == null) {
            return Promise.ofException(new IllegalStateException("No config path available for reload"));
        }

        // TODO: Implement hot-reload logic
        // - Load new config
        // - Compare with existing
        // - Apply safe changes
        // - Queue disruptive changes
        LOG.info("Configuration reload requested for organization: {}", getName());
        return Promise.complete();
    }
}
