package com.ghatana.aep.server;

import com.ghatana.aep.Aep;
import com.ghatana.aep.AepEngine;
import com.ghatana.aep.catalog.AepOperatorCatalogLoader;
import com.ghatana.aep.di.AepCoreModule;
import com.ghatana.aep.di.AepProductionModule;
import com.ghatana.aep.di.AepRuntimeProfile;
import com.ghatana.aep.server.grpc.AepGrpcServer;
import com.ghatana.agent.learning.review.DataCloudHumanReviewQueue;
import com.ghatana.agent.learning.review.HumanReviewQueue;
import com.ghatana.agent.learning.review.InMemoryHumanReviewQueue;
import com.ghatana.agent.learning.review.ReviewItem;
import com.ghatana.agent.learning.review.ReviewNotificationSpi;
import com.ghatana.agent.spi.AgentRegistry;
import com.ghatana.aep.server.http.AepHttpServer;
import com.ghatana.datacloud.agent.registry.DataCloudAgentRegistry;
import com.ghatana.core.operator.catalog.UnifiedOperatorCatalog;
import com.ghatana.core.operator.spi.OperatorProviderRegistry;
import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.client.DataCloudClientFactory;
import com.ghatana.datacloud.deployment.ServerConfig;
import com.ghatana.platform.incident.GracefulDegradationManager;
import com.ghatana.platform.incident.KillSwitchService;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.security.analytics.EgressMonitor;
import com.ghatana.platform.security.analytics.PromptInjectionDetector;
import com.ghatana.platform.toolruntime.change.ChangeApprovalWorkflow;
import com.ghatana.platform.toolruntime.recertification.RecertificationPipeline;
import io.activej.inject.Injector;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPool;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Objects;

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
    private static final String DEFAULT_AGENT_REGISTRY_TENANT = "platform";

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

            DataCloudClient agentDataCloud = createAgentDataCloudClient(System.getenv());

            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down AEP...");
                engine.close();
                if (agentDataCloud != null) {
                    agentDataCloud.close();
                }
                log.info("AEP shutdown complete");
            }));

            // Start learning-loop components (HumanReviewQueue + ConsolidationScheduler).
            // These are always created so HITL/learning endpoints are available.
            java.util.concurrent.atomic.AtomicReference<AepHttpServer> httpServerRef =
                new java.util.concurrent.atomic.AtomicReference<>();
            HumanReviewQueue humanReviewQueue = createHumanReviewQueue(agentDataCloud, httpServerRef);

            // Start HTTP server if configured
            if (shouldStartHttpServer(args)) {
                startHttpServer(engine, config, agentDataCloud, httpServerRef, humanReviewQueue);
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
            UnifiedOperatorCatalog catalog = new UnifiedOperatorCatalog();
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
        startGrpcServer(System.getenv());
    }

    static void startGrpcServer(Map<String, String> environment) {
        try {
            GrpcRegistryRuntime registryRuntime = createGrpcRegistryRuntime(environment);
            AepGrpcServer grpcServer = new AepGrpcServer(registryRuntime.agentRegistry());
            grpcServer.start();
            log.info("gRPC server started on port {}", grpcServer.getPort());

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping gRPC server...");
                grpcServer.close();
                registryRuntime.close();
            }));
        } catch (Exception e) {
            log.error("Failed to start gRPC server", e);
            throw new IllegalStateException("Failed to start AEP gRPC server", e);
        }
    }

    static GrpcRegistryRuntime createGrpcRegistryRuntime(Map<String, String> environment) {
        com.ghatana.datacloud.client.DataCloudClient registryDataCloud = createRegistryDataCloudClient(environment);
        AgentRegistry agentRegistry = new DataCloudAgentRegistry(
            registryDataCloud,
            resolveAgentRegistryTenant(environment)
        );
        return new GrpcRegistryRuntime(agentRegistry, registryDataCloud);
    }

    static com.ghatana.datacloud.client.DataCloudClient createRegistryDataCloudClient(
            Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");

        String dataCloudUrl = trimToNull(environment.get("DATACLOUD_URL"));
        if (dataCloudUrl != null) {
            log.info("Creating standalone Data-Cloud client for AEP gRPC registry: {}", dataCloudUrl);
            return DataCloudClientFactory.standalone(dataCloudUrl);
        }

        if (hasDataCloudEnvironment(environment)) {
            log.info("Creating Data-Cloud client for AEP gRPC registry from Data-Cloud environment settings");
            return DataCloudClientFactory.fromEnvironment(environment);
        }

        if (AepRuntimeProfile.isProduction(environment)) {
            throw new IllegalStateException(
                "DATACLOUD_URL must be configured when AEP_PROFILE=production"
            );
        }

        log.info("Creating embedded Data-Cloud client for AEP gRPC registry in non-production profile '{}'",
            AepRuntimeProfile.resolve(environment));
        return DataCloudClientFactory.embedded(ServerConfig.defaultConfig());
    }

    private static boolean hasDataCloudEnvironment(Map<String, String> environment) {
        return trimToNull(environment.get("DC_DEPLOYMENT_MODE")) != null
            || trimToNull(environment.get("DC_SERVER_URL")) != null
            || trimToNull(environment.get("DC_CLUSTER_URLS")) != null;
    }

    private static String resolveAgentRegistryTenant(Map<String, String> environment) {
        String tenant = trimToNull(environment.get("AEP_AGENT_REGISTRY_TENANT"));
        return tenant != null ? tenant : DEFAULT_AGENT_REGISTRY_TENANT;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Creates an in-memory {@link HumanReviewQueue} wired with a {@link ReviewNotificationSpi}
     * that broadcasts {@code hitl_request_created} SSE events to all connected UI clients.
     *
     * <p>The {@code httpServerRef} is resolved lazily so the queue can be created before
     * the HTTP server (which gets the queue as a constructor argument).
     *
     * @param httpServerRef lazy reference to the HTTP server set after construction
     * @return in-memory human-review queue
     */
        private static HumanReviewQueue createHumanReviewQueue(
            @org.jetbrains.annotations.Nullable DataCloudClient agentDataCloud,
            java.util.concurrent.atomic.AtomicReference<AepHttpServer> httpServerRef) {
        ReviewNotificationSpi spi = new ReviewNotificationSpi() {
            @Override
            public void onItemEnqueued(ReviewItem item) {
                AepHttpServer srv = httpServerRef.get();
                if (srv != null) {
                    srv.broadcastSseEvent(
                        item.getTenantId(),
                        "hitl_request_created",
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
        if (agentDataCloud != null) {
            log.info("Creating DataCloud-backed HumanReviewQueue with SSE notification SPI");
            return new DataCloudHumanReviewQueue(agentDataCloud, spi);
        }
        log.info("Creating in-memory HumanReviewQueue with SSE notification SPI");
        return new InMemoryHumanReviewQueue(spi);
    }

    private static void startHttpServer(AepEngine engine, Aep.AepConfig config,
                                        @org.jetbrains.annotations.Nullable DataCloudClient agentDataCloud,
                                        @org.jetbrains.annotations.Nullable java.util.concurrent.atomic.AtomicReference<AepHttpServer> httpServerRef,
                                        @org.jetbrains.annotations.Nullable HumanReviewQueue humanReviewQueue) {
        int port = 8080;
        String portEnv = System.getenv("AEP_HTTP_PORT");
        if (portEnv != null) {
            port = Integer.parseInt(portEnv);
        }

        try {
            PrometheusMeterRegistry promRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            MetricsCollector metricsCollector = MetricsCollectorFactory.create(promRegistry);
            Injector injector = createGovernanceInjector();
            DataSource dataSource = injector.getInstance(DataSource.class);
            JedisPool jedisPool = injector.getInstance(JedisPool.class);
            AepHttpServer httpServer = new AepHttpServer(engine, port, agentDataCloud, humanReviewQueue,
                metricsCollector,
                injector.getInstance(KillSwitchService.class),
                injector.getInstance(GracefulDegradationManager.class),
                injector.getInstance(PolicyAsCodeEngine.class),
                injector.getInstance(EgressMonitor.class),
                injector.getInstance(PromptInjectionDetector.class),
                injector.getInstance(ChangeApprovalWorkflow.class),
                injector.getInstance(RecertificationPipeline.class),
                promRegistry,
                dataSource,
                jedisPool);
            if (httpServerRef != null) {
                httpServerRef.set(httpServer);
            }
            httpServer.start();
            log.info("HTTP server started on port {}", port);

            // Register shutdown hook for HTTP server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping HTTP server...");
                httpServer.stop();
            }));
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
            throw new IllegalStateException("Failed to start AEP HTTP server on port " + port, e);
        }
    }

    private static Injector createGovernanceInjector() {
        return createGovernanceInjector(System.getenv());
    }

    static Injector createGovernanceInjector(Map<String, String> environment) {
        if (AepRuntimeProfile.isProduction(environment)) {
            log.info("Initializing AEP governance services with production profile");
            return Injector.of(new AepProductionModule(environment));
        }

        log.info("Initializing AEP governance services with non-production profile '{}'",
            AepRuntimeProfile.resolve(environment));
        return Injector.of(new AepCoreModule());
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
    static DataCloudClient createAgentDataCloudClient(Map<String, String> environment) {
        try {
            String sovereignDataDir = trimToNull(environment.get("DATACLOUD_SOVEREIGN_DATA_DIR"));
            if (sovereignDataDir != null) {
                log.info("Creating sovereign DataCloudClient for AEP using {}", sovereignDataDir);
                return DataCloud.create(DataCloud.DataCloudConfig.builder()
                    .profile(DataCloud.DataCloudConfig.DataCloudProfile.SOVEREIGN)
                    .customConfig(Map.of("sovereign.dataDir", sovereignDataDir))
                    .build());
            }

            if (AepRuntimeProfile.isProduction(environment)) {
                log.info("Creating durable production DataCloudClient for AEP");
                return DataCloud.create(DataCloud.DataCloudConfig.builder()
                    .profile(DataCloud.DataCloudConfig.DataCloudProfile.PRODUCTION)
                    .build());
            }

            log.info("Creating embedded DataCloudClient for AEP in non-production profile '{}'",
                AepRuntimeProfile.resolve(environment));
            return DataCloud.embedded();
        } catch (Exception e) {
            if (AepRuntimeProfile.isProduction(environment)) {
                throw new IllegalStateException("Failed to create durable DataCloudClient for AEP", e);
            }
            log.warn("DataCloudClient creation failed — agent registry endpoints will be unavailable: {}", e.getMessage());
            return null;
        }
    }

    static final class GrpcRegistryRuntime implements AutoCloseable {
        private final AgentRegistry agentRegistry;
        private final com.ghatana.datacloud.client.DataCloudClient registryDataCloud;

        GrpcRegistryRuntime(AgentRegistry agentRegistry,
                            com.ghatana.datacloud.client.DataCloudClient registryDataCloud) {
            this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry");
            this.registryDataCloud = Objects.requireNonNull(registryDataCloud, "registryDataCloud");
        }

        AgentRegistry agentRegistry() {
            return agentRegistry;
        }

        com.ghatana.datacloud.client.DataCloudClient registryDataCloud() {
            return registryDataCloud;
        }

        @Override
        public void close() {
            if (agentRegistry instanceof AutoCloseable closeableRegistry) {
                try {
                    closeableRegistry.close();
                } catch (Exception e) {
                    log.warn("Failed to close gRPC agent registry cleanly: {}", e.getMessage(), e);
                }
            }

            try {
                registryDataCloud.close();
            } catch (Exception e) {
                log.warn("Failed to close gRPC registry Data-Cloud client cleanly: {}", e.getMessage(), e);
            }
        }
    }
}
