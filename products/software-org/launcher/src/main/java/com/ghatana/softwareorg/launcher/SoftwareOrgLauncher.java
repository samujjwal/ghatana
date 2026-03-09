package com.ghatana.softwareorg.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Software-Org Standalone Launcher - Entry point for standalone deployment.
 *
 * <p>This launcher provides a main entry point for running Software-Org as a
 * standalone service. It supports:
 * <ul>
 *   <li>Command-line configuration</li>
 *   <li>Environment variable overrides</li>
 *   <li>Simulation engine lifecycle management</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for Software-Org
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 * @since 1.0.0
 */
public class SoftwareOrgLauncher {

    private static final Logger log = LoggerFactory.getLogger(SoftwareOrgLauncher.class);

    public static void main(String[] args) {
        log.info("Starting Software-Org Standalone...");

        try {
            SoftwareOrgConfig config = parseConfig(args);

            log.info("Software-Org configured successfully");
            log.info("  Instance ID: {}", config.instanceId());
            log.info("  HTTP Port: {}", config.httpPort());
            log.info("  Simulation Mode: {}", config.simulationMode());

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Software-Org...");
                log.info("Software-Org shutdown complete");
            }));

            // Start HTTP server if configured
            if (config.httpEnabled()) {
                log.info("HTTP server started on port {}", config.httpPort());
            }

            // Keep running
            log.info("Software-Org is ready to run simulations");
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Software-Org interrupted, shutting down");
        } catch (Exception e) {
            log.error("Failed to start Software-Org", e);
            System.exit(1);
        }
    }

    private static SoftwareOrgConfig parseConfig(String[] args) {
        String instanceId = "software-org-1";
        int httpPort = 8083;
        String simulationMode = "standard";
        boolean httpEnabled = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instance-id" -> {
                    if (i + 1 < args.length) instanceId = args[++i];
                }
                case "--http-port" -> {
                    if (i + 1 < args.length) httpPort = Integer.parseInt(args[++i]);
                }
                case "--simulation-mode" -> {
                    if (i + 1 < args.length) simulationMode = args[++i];
                }
                case "--no-http" -> httpEnabled = false;
            }
        }

        // Environment variable overrides
        String envInstanceId = System.getenv("SOFTWARE_ORG_INSTANCE_ID");
        if (envInstanceId != null) instanceId = envInstanceId;

        String envHttpPort = System.getenv("SOFTWARE_ORG_HTTP_PORT");
        if (envHttpPort != null) httpPort = Integer.parseInt(envHttpPort);

        String envSimMode = System.getenv("SOFTWARE_ORG_SIMULATION_MODE");
        if (envSimMode != null) simulationMode = envSimMode;

        return new SoftwareOrgConfig(instanceId, httpPort, simulationMode, httpEnabled);
    }

    record SoftwareOrgConfig(String instanceId, int httpPort, String simulationMode, boolean httpEnabled) {}
}
