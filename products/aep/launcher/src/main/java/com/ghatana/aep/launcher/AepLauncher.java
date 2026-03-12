package com.ghatana.aep.launcher;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.catalog.AepOperatorCatalogLoader;
import com.ghatana.aep.launcher.grpc.AepGrpcServer;
import com.ghatana.agent.registry.InMemoryAgentFrameworkRegistry;
import com.ghatana.core.operator.catalog.DefaultOperatorCatalog;
import com.ghatana.core.operator.catalog.OperatorCatalog;
import com.ghatana.core.operator.spi.OperatorProviderRegistry;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
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

            // Load operator catalog before starting server — discovers YAML operator
            // definitions from classpath resources/operators/ and registers them
            loadOperatorCatalog();

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

            // Start gRPC server if configured
            if (shouldStartGrpcServer(args)) {
                startGrpcServer();
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

    private static void loadOperatorCatalog() {
        try {
            OperatorCatalog catalog = new DefaultOperatorCatalog();
            OperatorProviderRegistry registry = OperatorProviderRegistry.create();
            new AepOperatorCatalogLoader(catalog, registry).loadFromClasspath();
            log.info("Operator catalog loaded from classpath");
        } catch (Exception e) {
            log.warn("Operator catalog loading failed — continuing without catalog: {}", e.getMessage());
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

    private static boolean shouldStartGrpcServer(String[] args) {
        for (String arg : args) {
            if ("--grpc".equals(arg)) {
                return true;
            }
        }
        return System.getenv("AEP_GRPC_ENABLED") != null;
    }

    private static void startGrpcServer() {
        try {
            AepGrpcServer grpcServer = new AepGrpcServer(new InMemoryAgentFrameworkRegistry());
            grpcServer.start();
            log.info("gRPC server started on port {}", grpcServer.getPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping gRPC server...");
                grpcServer.close();
            }));
        } catch (Exception e) {
            log.error("Failed to start gRPC server", e);
        }
    }

    private static void startHttpServer(AepEngine engine, Aep.AepConfig config) {
        int port = 8080;
        String portEnv = System.getenv("AEP_HTTP_PORT");
        if (portEnv != null) {
            port = Integer.parseInt(portEnv);
        }

        try {
            DataCloudClient agentDataCloud = createAgentDataCloudClient();
            com.ghatana.aep.launcher.http.AepHttpServer httpServer =
                new com.ghatana.aep.launcher.http.AepHttpServer(engine, port, null, agentDataCloud);
            httpServer.start();
            log.info("HTTP server started on port {}", port);

            // Register shutdown hook for HTTP server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping HTTP server...");
                httpServer.stop();
                if (agentDataCloud != null) {
                    agentDataCloud.close();
                }
            }));
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
        }
    }

    /**
     * Creates a {@link DataCloudClient} for agent-registry queries.
     *
     * <p>Creates an embedded in-process Data-Cloud instance backed by in-memory storage.
     * For production deployments that connect to an external Data-Cloud service, inject
     * the client via the DI module instead of using this factory method.
     *
     * @return configured client, or {@code null} on failure (agent registry endpoints degrade gracefully)
     */
    private static DataCloudClient createAgentDataCloudClient() {
        try {
            log.info("Creating embedded DataCloudClient for agent registry");
            return DataCloud.embedded();
        } catch (Exception e) {
            log.warn("DataCloudClient creation failed — agent registry endpoints will be unavailable: {}", e.getMessage());
            return null;
        }
    }
}
