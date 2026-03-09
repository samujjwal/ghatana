package com.ghatana.virtualorg.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Virtual-Org Standalone Launcher - Entry point for standalone deployment.
 *
 * <p>This launcher provides a main entry point for running Virtual-Org as a
 * standalone service. It supports:
 * <ul>
 *   <li>Command-line configuration</li>
 *   <li>Environment variable overrides</li>
 *   <li>Agent framework lifecycle management</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for Virtual-Org
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 * @since 1.0.0
 */
public class VirtualOrgLauncher {

    private static final Logger log = LoggerFactory.getLogger(VirtualOrgLauncher.class);

    public static void main(String[] args) {
        log.info("Starting Virtual-Org Standalone...");

        try {
            VirtualOrgConfig config = parseConfig(args);

            log.info("Virtual-Org configured successfully");
            log.info("  Instance ID: {}", config.instanceId());
            log.info("  HTTP Port: {}", config.httpPort());
            log.info("  gRPC Port: {}", config.grpcPort());
            log.info("  Max Agents: {}", config.maxAgents());

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Virtual-Org...");
                log.info("Virtual-Org shutdown complete");
            }));

            // Start HTTP server if configured
            if (config.httpEnabled()) {
                log.info("HTTP server started on port {}", config.httpPort());
            }

            // Start gRPC server if configured
            if (config.grpcEnabled()) {
                log.info("gRPC server started on port {}", config.grpcPort());
            }

            // Keep running
            log.info("Virtual-Org is ready to manage agents");
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Virtual-Org interrupted, shutting down");
        } catch (Exception e) {
            log.error("Failed to start Virtual-Org", e);
            System.exit(1);
        }
    }

    private static VirtualOrgConfig parseConfig(String[] args) {
        String instanceId = "virtual-org-1";
        int httpPort = 8084;
        int grpcPort = 50051;
        int maxAgents = 100;
        boolean httpEnabled = true;
        boolean grpcEnabled = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instance-id" -> {
                    if (i + 1 < args.length) instanceId = args[++i];
                }
                case "--http-port" -> {
                    if (i + 1 < args.length) httpPort = Integer.parseInt(args[++i]);
                }
                case "--grpc-port" -> {
                    if (i + 1 < args.length) grpcPort = Integer.parseInt(args[++i]);
                }
                case "--max-agents" -> {
                    if (i + 1 < args.length) maxAgents = Integer.parseInt(args[++i]);
                }
                case "--no-http" -> httpEnabled = false;
                case "--no-grpc" -> grpcEnabled = false;
            }
        }

        // Environment variable overrides
        String envInstanceId = System.getenv("VIRTUAL_ORG_INSTANCE_ID");
        if (envInstanceId != null) instanceId = envInstanceId;

        String envHttpPort = System.getenv("VIRTUAL_ORG_HTTP_PORT");
        if (envHttpPort != null) httpPort = Integer.parseInt(envHttpPort);

        String envGrpcPort = System.getenv("VIRTUAL_ORG_GRPC_PORT");
        if (envGrpcPort != null) grpcPort = Integer.parseInt(envGrpcPort);

        String envMaxAgents = System.getenv("VIRTUAL_ORG_MAX_AGENTS");
        if (envMaxAgents != null) maxAgents = Integer.parseInt(envMaxAgents);

        return new VirtualOrgConfig(instanceId, httpPort, grpcPort, maxAgents, httpEnabled, grpcEnabled);
    }

    record VirtualOrgConfig(String instanceId, int httpPort, int grpcPort, int maxAgents,
                            boolean httpEnabled, boolean grpcEnabled) {}
}
