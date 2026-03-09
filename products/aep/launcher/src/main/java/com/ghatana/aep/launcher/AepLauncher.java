package com.ghatana.aep.launcher;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AEP Standalone Launcher - Entry point for standalone deployment.
 *
 * <p>This launcher provides a main entry point for running AEP as a
 * standalone service. It supports:
 * <ul>
 *   <li>Command-line configuration</li>
 *   <li>Environment variable overrides</li>
 *   <li>Optional HTTP server</li>
 *   <li>Graceful shutdown</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for AEP
 * @doc.layer launcher
 * @doc.pattern Main Entry Point
 * @since 1.0.0
 */
public class AepLauncher {

    private static final Logger log = LoggerFactory.getLogger(AepLauncher.class);

    public static void main(String[] args) {
        log.info("Starting AEP Standalone...");

        try {
            // Parse configuration from args or environment
            Aep.AepConfig config = parseConfig(args);

            // Create and start engine
            AepEngine engine = Aep.create(config);

            log.info("AEP Engine started successfully");
            log.info("  Instance ID: {}", config.instanceId());
            log.info("  Worker Threads: {}", config.workerThreads());
            log.info("  EventCloud: {}", engine.eventCloud().getClass().getSimpleName());

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down AEP...");
                engine.close();
                log.info("AEP shutdown complete");
            }));

            // Start HTTP server if configured
            if (shouldStartHttpServer(args)) {
                startHttpServer(engine, config);
            }

            // Keep running
            log.info("AEP is ready to process events");
            Thread.currentThread().join();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("AEP interrupted, shutting down");
        } catch (Exception e) {
            log.error("Failed to start AEP", e);
            System.exit(1);
        }
    }

    private static Aep.AepConfig parseConfig(String[] args) {
        Aep.AepConfig.Builder builder = Aep.AepConfig.builder();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instance-id" -> {
                    if (i + 1 < args.length) {
                        builder.instanceId(args[++i]);
                    }
                }
                case "--workers" -> {
                    if (i + 1 < args.length) {
                        builder.workerThreads(Integer.parseInt(args[++i]));
                    }
                }
                case "--enable-metrics" -> builder.enableMetrics(true);
                case "--enable-tracing" -> builder.enableTracing(true);
            }
        }

        // Environment variable overrides
        String instanceId = System.getenv("AEP_INSTANCE_ID");
        if (instanceId != null) {
            builder.instanceId(instanceId);
        }

        String workers = System.getenv("AEP_WORKERS");
        if (workers != null) {
            builder.workerThreads(Integer.parseInt(workers));
        }

        return builder.build();
    }

    private static boolean shouldStartHttpServer(String[] args) {
        for (String arg : args) {
            if ("--http".equals(arg) || "--server".equals(arg)) {
                return true;
            }
        }
        return System.getenv("AEP_HTTP_ENABLED") != null;
    }

    private static void startHttpServer(AepEngine engine, Aep.AepConfig config) {
        int port = 8080;
        String portEnv = System.getenv("AEP_HTTP_PORT");
        if (portEnv != null) {
            port = Integer.parseInt(portEnv);
        }

        try {
            com.ghatana.aep.launcher.http.AepHttpServer httpServer =
                new com.ghatana.aep.launcher.http.AepHttpServer(engine, port);
            httpServer.start();
            log.info("HTTP server started on port {}", port);

            // Register shutdown hook for HTTP server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping HTTP server...");
                httpServer.stop();
            }));
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
        }
    }
}
