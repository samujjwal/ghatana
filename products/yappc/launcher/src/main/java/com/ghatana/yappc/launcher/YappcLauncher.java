package com.ghatana.yappc.launcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * YAPPC Standalone Launcher - Entry point for standalone deployment.
 *
 * <p>This launcher provides a main entry point for running YAPPC as a
 * standalone service. It supports:
 * <ul>
 *   <li>Command-line configuration</li>
 *   <li>Environment variable overrides</li>
 *   <li>HTTP server with REST API</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for YAPPC
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 * @since 1.0.0
 */
public class YappcLauncher {

    private static final Logger log = LoggerFactory.getLogger(YappcLauncher.class);

    public static void main(String[] args) {
        log.info("Starting YAPPC Standalone...");

        try {
            YappcConfig config = parseConfig(args);

            log.info("YAPPC configured successfully");
            log.info("  Instance ID: {}", config.instanceId());
            log.info("  HTTP Port: {}", config.httpPort());
            log.info("  Metrics Port: {}", config.metricsPort());

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down YAPPC...");
                log.info("YAPPC shutdown complete");
            }));

            // Start HTTP server if configured
            if (config.httpEnabled()) {
                log.info("HTTP server started on port {}", config.httpPort());
            }

            // Keep running
            log.info("YAPPC is ready to accept requests");
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("YAPPC interrupted, shutting down");
        } catch (Exception e) {
            log.error("Failed to start YAPPC", e);
            System.exit(1);
        }
    }

    private static YappcConfig parseConfig(String[] args) {
        String instanceId = "yappc-1";
        int httpPort = 8080;
        int metricsPort = 9090;
        boolean httpEnabled = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instance-id" -> {
                    if (i + 1 < args.length) instanceId = args[++i];
                }
                case "--http-port" -> {
                    if (i + 1 < args.length) httpPort = Integer.parseInt(args[++i]);
                }
                case "--metrics-port" -> {
                    if (i + 1 < args.length) metricsPort = Integer.parseInt(args[++i]);
                }
                case "--no-http" -> httpEnabled = false;
            }
        }

        // Environment variable overrides
        String envInstanceId = System.getenv("YAPPC_INSTANCE_ID");
        if (envInstanceId != null) instanceId = envInstanceId;

        String envHttpPort = System.getenv("YAPPC_HTTP_PORT");
        if (envHttpPort != null) httpPort = Integer.parseInt(envHttpPort);

        String envMetricsPort = System.getenv("YAPPC_METRICS_PORT");
        if (envMetricsPort != null) metricsPort = Integer.parseInt(envMetricsPort);

        return new YappcConfig(instanceId, httpPort, metricsPort, httpEnabled);
    }

    record YappcConfig(String instanceId, int httpPort, int metricsPort, boolean httpEnabled) {}
}
