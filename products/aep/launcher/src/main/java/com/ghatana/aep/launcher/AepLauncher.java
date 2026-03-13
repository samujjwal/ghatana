package com.ghatana.aep.launcher;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.catalog.AepOperatorCatalogLoader;
import com.ghatana.aep.launcher.grpc.AepGrpcServer;
import com.ghatana.agent.learning.consolidation.ConsolidationScheduler;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import com.ghatana.aep.launcher.http.AepHttpServer;
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

            // Start learning-loop components (HumanReviewQueue + ConsolidationScheduler)
            // These are always created so HITL/learning endpoints are available.
            java.util.concurrent.atomic.AtomicReference<AepHttpServer> httpServerRef =
                new java.util.concurrent.atomic.AtomicReference<>();
            HumanReviewQueue humanReviewQueue = createHumanReviewQueue(httpServerRef);
            startConsolidationScheduler(humanReviewQueue);

            // Start HTTP server if configured
            if (shouldStartHttpServer(args)) {
                startHttpServer(engine, config, humanReviewQueue, httpServerRef);
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

    /**
     * Creates an in-memory {@link HumanReviewQueue} wired with a {@link ReviewNotificationSpi}
     * that broadcasts {@code hitl.new} SSE events to all connected UI clients.
     *
     * <p>The {@code httpServerRef} is resolved lazily so the queue can be created before
     * the HTTP server (which gets the queue as a constructor argument).
     *
     * @param httpServerRef lazy reference to the HTTP server set after construction
     * @return in-memory human-review queue
     */
    private static HumanReviewQueue createHumanReviewQueue(
            java.util.concurrent.atomic.AtomicReference<AepHttpServer> httpServerRef) {
        ReviewNotificationSpi spi = new ReviewNotificationSpi() {
            @Override
            public void onItemEnqueued(ReviewItem item) {
                AepHttpServer srv = httpServerRef.get();
                if (srv != null) {
                    srv.broadcastSseEvent(
                        item.getTenantId(),
                        "hitl.new",
                        java.util.Map.of(
                            "itemId",    item.getReviewId(),
                            "skillId",   item.getSkillId(),
                            "itemType",  item.getItemType().name(),
                            "confidence", item.getConfidenceScore()
                        )
                    );
                }
            }
            @Override public void onItemApproved(ReviewItem item) {}
            @Override public void onItemRejected(ReviewItem item) {}
        };
        log.info("Creating in-memory HumanReviewQueue with SSE notification SPI");
        return new InMemoryHumanReviewQueue(spi);
    }

    /**
     * Starts the {@link ConsolidationScheduler} (learning loop).
     *
     * <p>The scheduler periodically consolidates episodic memory into semantic
     * facts and procedural policies. The interval is controlled by the
     * {@code AEP_CONSOLIDATION_INTERVAL_HOURS} environment variable (default: 6 h).
     *
     * @param humanReviewQueue queue to which low-confidence policies are submitted
     */
    private static void startConsolidationScheduler(HumanReviewQueue humanReviewQueue) {
        try {
            // Use the AepLearningModule injector to wire all consolidation components
            com.ghatana.aep.di.AepLearningModule learningModule = new com.ghatana.aep.di.AepLearningModule();
            io.activej.inject.Injector injector = io.activej.inject.Injector.of(
                    new com.ghatana.aep.di.AepCoreModule(), learningModule);
            ConsolidationScheduler scheduler = injector.getInstance(ConsolidationScheduler.class);
            scheduler.start();
            log.info("ConsolidationScheduler started (learning loop active)");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping ConsolidationScheduler...");
                scheduler.stop();
            }));
        } catch (Exception e) {
            log.warn("ConsolidationScheduler could not start — learning loop disabled: {}", e.getMessage());
        }
    }

    private static void startHttpServer(AepEngine engine, Aep.AepConfig config,
                                        HumanReviewQueue humanReviewQueue,
                                        java.util.concurrent.atomic.AtomicReference<AepHttpServer> httpServerRef) {
        int port = 8080;
        String portEnv = System.getenv("AEP_HTTP_PORT");
        if (portEnv != null) {
            port = Integer.parseInt(portEnv);
        }

        try {
            DataCloudClient agentDataCloud = createAgentDataCloudClient();
            AepHttpServer httpServer =
                new AepHttpServer(engine, port, humanReviewQueue, agentDataCloud);
            // Set the reference BEFORE start() so the ReviewNotificationSpi can use it
            // immediately (ConsolidationScheduler may fire on startup replay).
            httpServerRef.set(httpServer);
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
