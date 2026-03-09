package com.ghatana.softwareorg.launcher;

import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.virtualorg.framework.VirtualOrgContext;
import com.ghatana.virtualorg.framework.config.ConfigurableOrganization;
import com.ghatana.virtualorg.framework.config.OrganizationConfigLoader;
import com.ghatana.virtualorg.framework.config.ResolvedOrganizationConfig;
import io.activej.eventloop.Eventloop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Virtual-app framework bootstrap.
 *
 * <p><b>Purpose</b><br>
 * Initializes and manages the virtual-app framework for the software organization.
 * Integrates with the virtual-org framework to create departments, agents, and workflows.
 *
 * <p><b>Responsibilities</b><br>
 * - Initialize virtual-org framework with VirtualOrgContext
 * - Create departments from configuration
 * - Bootstrap agents and workflows using SPI-discovered factories
 * - Manage application lifecycle
 *
 * @doc.type class
 * @doc.purpose Virtual-app framework initialization and lifecycle management
 * @doc.layer product
 * @doc.pattern Facade
 */
public class VirtualAppBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(VirtualAppBootstrap.class);

    private final OrgConfiguration orgConfig;
    private final LauncherConfig launcherConfig;
    private final Eventloop eventloop;
    private VirtualOrgContext virtualOrgContext;
    private ConfigurableOrganization organization;
    private OrganizationConfigLoader configLoader;
    private ResolvedOrganizationConfig resolvedConfig;
    private boolean initialized = false;
    private boolean started = false;

    /**
     * Creates a new virtual-app bootstrap.
     *
     * @param eventloop Eventloop
     * @param orgConfig Organization configuration
     * @param launcherConfig Launcher configuration
     */
    public VirtualAppBootstrap(Eventloop eventloop, OrgConfiguration orgConfig, LauncherConfig launcherConfig) {
        this.eventloop = eventloop;
        this.orgConfig = orgConfig;
        this.launcherConfig = launcherConfig;
    }

    /**
     * Initializes the virtual-app framework synchronously.
     *
     * @throws RuntimeException if initialization fails
     */
    public void initialize() {
        if (initialized) {
            logger.warn("Virtual-app framework already initialized");
            return;
        }

        logger.info("Initializing virtual-app framework...");

        try {
            // Step 1: Create VirtualOrgContext with auto-discovery
            virtualOrgContext = VirtualOrgContext.builder(eventloop)
                    .withAutoDiscovery(true)
                    .build();

            // Step 2: Initialize context (loads extensions via SPI)
            virtualOrgContext.initialize().getResult();
            logger.info("VirtualOrgContext initialized with {} agent factories",
                    virtualOrgContext.getAgentRegistry().getFactoryCount());

            // Step 3: Create organization config loader
            configLoader = new OrganizationConfigLoader(launcherConfig.getConfigPath());
            logger.debug("Created OrganizationConfigLoader");

            // Step 4: Load organization configuration from YAML
            Path orgConfigPath = launcherConfig.getConfigPath()
                .resolve("organization.yaml");
            
            logger.info("Loading organization config from: {}", orgConfigPath);
            var rawConfig = configLoader.loadSync(orgConfigPath);
            
            // Step 5: Resolve all references (departments, agents, workflows)
            logger.info("Resolving configuration references...");
            resolvedConfig = configLoader.resolveReferencesSync(
                launcherConfig.getConfigPath(),
                rawConfig
            );
            logger.info("Configuration resolved: {} departments, {} agents",
                resolvedConfig.departments().size(),
                resolvedConfig.agents().size());

            // Step 6: Create configurable organization with context's agent registry
            TenantId tenantId = TenantId.of("software-org-" + System.currentTimeMillis());
            organization = new ConfigurableOrganization(
                eventloop,
                tenantId,
                resolvedConfig,
                launcherConfig.getConfigPath(),
                virtualOrgContext.getAgentRegistry()
            );
            logger.info("Created organization: {}", organization.getName());

            initialized = true;
            logger.info("Virtual-app framework initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize virtual-app framework", e);
            throw new RuntimeException("Virtual-app initialization failed", e);
        }
    }

    /**
     * Starts the application synchronously.
     *
     * @throws RuntimeException if startup fails
     */
    public void start() {
        if (!initialized) {
            throw new IllegalStateException("Framework not initialized. Call initialize() first.");
        }

        if (started) {
            logger.warn("Application already started");
            return;
        }

        logger.info("Starting application...");

        try {
            // Start the organization (activates all departments and agents)
            logger.info("Starting organization: {}", organization.getName());
            organization.start();
            
            logger.info("Organization started with {} departments",
                organization.getDepartments().size());
            logger.info("Total agents: {}", 
                organization.getDepartments().stream()
                    .mapToInt(dept -> dept.getAgents().size())
                    .sum());

            started = true;
            logger.info("Application started successfully");
            
        } catch (Exception e) {
            logger.error("Failed to start application", e);
            throw new RuntimeException("Application startup failed", e);
        }
    }

    /**
     * Stops the application.
     */
    public void stop() {
        if (!started) {
            logger.warn("Application not started");
            return;
        }

        logger.info("Stopping application...");

        try {
            // Stop the organization (deactivates all departments and agents)
            if (organization != null) {
                logger.info("Stopping organization: {}", organization.getName());
                organization.stop();
                logger.info("Organization stopped");
            }

            // Shutdown VirtualOrgContext
            if (virtualOrgContext != null) {
                virtualOrgContext.shutdown().getResult();
                logger.info("VirtualOrgContext shut down");
            }

            started = false;
            initialized = false;
            logger.info("Application stopped successfully");
        } catch (Exception e) {
            logger.error("Error stopping application", e);
        }
    }
    
    /**
     * Gets the organization configuration.
     *
     * @return organization configuration
     */
    public OrgConfiguration getOrgConfig() {
        return orgConfig;
    }

    /**
     * Gets the VirtualOrgContext.
     *
     * @return the context, or null if not initialized
     */
    public VirtualOrgContext getVirtualOrgContext() {
        return virtualOrgContext;
    }
    
    /**
     * Gets the configurable organization instance.
     *
     * @return organization instance, or null if not initialized
     */
    public ConfigurableOrganization getOrganization() {
        return organization;
    }
    
    /**
     * Gets the resolved configuration.
     *
     * @return resolved configuration, or null if not initialized
     */
    public ResolvedOrganizationConfig getResolvedConfig() {
        return resolvedConfig;
    }
    
    /**
     * Checks if the framework is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if the framework is started.
     *
     * @return true if started
     */
    public boolean isStarted() {
        return started;
    }
}
