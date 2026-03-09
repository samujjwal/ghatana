package com.ghatana.softwareorg.launcher;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Software Organization Launcher - Main application entry point.
 *
 * <p><b>Purpose</b><br>
 * Single entry point for the entire Software-Org application. Initializes all
 * components including configuration loading, virtual-app framework, HTTP API server,
 * and department bootstrapping.
 *
 * <p><b>Startup Sequence</b><br>
 * 1. Load configuration from YAML files
 * 2. Initialize virtual-app framework
 * 3. Bootstrap all departments and agents
 * 4. Start HTTP API server
 * 5. Register shutdown hooks
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * # Start with default configuration
 * java -jar launcher-all.jar
 *
 * # Start with custom config path
 * java -jar launcher-all.jar --config-path=/path/to/configs
 *
 * # Start with custom port
 * java -jar launcher-all.jar --port=9090
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Main application launcher and entry point
 * @doc.layer product
 * @doc.pattern Facade
 */
public class SoftwareOrgLauncher {

    private static final Logger logger = LoggerFactory.getLogger(SoftwareOrgLauncher.class);

    private final LauncherConfig config;
    private ConfigurationLoader configLoader;
    private ApiServer apiServer;
    private VirtualAppBootstrap virtualAppBootstrap;
    private Eventloop eventloop;

    /**
     * Creates a new launcher with the given configuration.
     *
     * @param config Launcher configuration
     */
    public SoftwareOrgLauncher(LauncherConfig config) {
        this.config = config;
    }

    /**
     * Main entry point for the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            LauncherConfig config = LauncherConfig.fromArgs(args);

            // Create and start launcher
            SoftwareOrgLauncher launcher = new SoftwareOrgLauncher(config);
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, stopping application...");
                launcher.stop();
            }));

            launcher.start();

        } catch (Exception e) {
            logger.error("Failed to start Software-Org application", e);
            System.exit(1);
        }
    }

    /**
     * Starts the application.
     *
     * @throws Exception if startup fails
     */
    public void start() throws Exception {
        logger.info("========================================");
        logger.info("Starting Software-Org Application");
        logger.info("========================================");
        logger.info("Config Path: {}", config.getConfigPath());
        logger.info("API Port: {}", config.getApiPort());
        logger.info("Environment: {}", config.getEnvironment());
        logger.info("========================================");
        logger.info("Environment: {}", config.getEnvironment());
        logger.info("========================================");

        // Create Eventloop
        eventloop = Eventloop.builder()
                .withThreadName("main-eventloop")
                .withFatalErrorHandler((error, context) -> {
                    logger.error("Fatal error in eventloop: {}", context, error);
                })
                .build();

        // Step 1: Load configuration
        logger.info("Step 1/4: Loading configuration...");
        configLoader = new ConfigurationLoader(config.getConfigPath());
        OrgConfiguration orgConfig = configLoader.loadAll();
        logger.info("✓ Configuration loaded successfully");
        logger.info("  - Organization: {}", orgConfig.getName());
        logger.info("  - Version: {}", orgConfig.getVersion());
        logger.info("  - Departments: {}", orgConfig.getDepartments().size());
        logger.info("  - Personas: {}", orgConfig.getPersonas().size());
        logger.info("  - Agents: {}", orgConfig.getAgents().size());
        logger.info("  - Workflows: {}", orgConfig.getWorkflows().size());

        // Step 2: Initialize virtual-app framework
        logger.info("Step 2/4: Initializing virtual-app framework...");
        virtualAppBootstrap = new VirtualAppBootstrap(eventloop, orgConfig, config);
        virtualAppBootstrap.initialize(); // Returns Promise - executes via Promise.ofBlocking()
        logger.info("✓ Virtual-app framework initialized");

        // Step 3: Start HTTP API server
        logger.info("Step 3/4: Starting HTTP API server...");
        apiServer = new ApiServer(eventloop, config.getApiPort(), configLoader, virtualAppBootstrap);
        apiServer.start(); // Returns Promise<Void>
        logger.info("✓ HTTP API server started on port {}", config.getApiPort());
        logger.info("  - API Base URL: http://localhost:{}/api", config.getApiPort());
        logger.info("  - Health Check: http://localhost:{}/health", config.getApiPort());
        logger.info("  - Metrics: http://localhost:{}/metrics", config.getApiPort());

        // Step 4: Start application
        logger.info("Step 4/4: Starting application...");
        virtualAppBootstrap.start();
        logger.info("✓ Application started successfully");

        logger.info("========================================");
        logger.info("Software-Org Application is READY");
        logger.info("========================================");

        // Run eventloop
        eventloop.run();
    }

    /**
     * Stops the application gracefully.
     */
    public void stop() {
        logger.info("Stopping Software-Org application...");

        try {
            // Stop API server
            if (apiServer != null) {
                logger.info("Stopping API server...");
                apiServer.stop();
                logger.info("✓ API server stopped");
            }

            // Stop virtual-app framework
            if (virtualAppBootstrap != null) {
                logger.info("Stopping virtual-app framework...");
                virtualAppBootstrap.stop();
                logger.info("✓ Virtual-app framework stopped");
            }

            // Break eventloop
            if (eventloop != null) {
                eventloop.breakEventloop();
            }

            logger.info("✓ Software-Org application stopped successfully");

        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    /**
     * Gets the configuration loader.
     *
     * @return Configuration loader
     */
    public ConfigurationLoader getConfigLoader() {
        return configLoader;
    }

    /**
     * Gets the API server.
     *
     * @return API server
     */
    public ApiServer getApiServer() {
        return apiServer;
    }

    /**
     * Gets the virtual-app bootstrap.
     *
     * @return Virtual-app bootstrap
     */
    public VirtualAppBootstrap getVirtualAppBootstrap() {
        return virtualAppBootstrap;
    }
}
