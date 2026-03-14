package com.ghatana.datacloud.launcher;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.di.DataCloudBrainModule;
import com.ghatana.datacloud.launcher.grpc.DataCloudGrpcServer;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

/**
 * Data-Cloud Standalone Launcher - Entry point for standalone deployment.
 *
 * <p>Starts HTTP and/or gRPC server based on command-line flags or environment
 * variables. When {@code DATACLOUD_BRAIN_ENABLED=true}, also wires the
 * cognitive brain and learning bridge so that {@code /api/v1/brain/**} and
 * {@code /api/v1/learning/**} endpoints are active.
 *
 * @doc.type class
 * @doc.purpose Standalone launcher for Data-Cloud services
 * @doc.layer product
 * @doc.pattern Launcher
 * @since 1.0.0
 */
public class DataCloudLauncher {

    private static final Logger log = LoggerFactory.getLogger(DataCloudLauncher.class);

    public static void main(String[] args) {
        log.info("Starting Data-Cloud Standalone...");
        
        try {
            // Parse configuration from args or environment
            // Validate configuration before creating any resources (fail-fast)
            DataCloudConfigValidator.fromEnvironment().validate();

            DataCloud.DataCloudConfig config = parseConfig(args);
            
            // Create and start client
            DataCloudClient client = DataCloud.create(config);
            
            log.info("Data-Cloud Client started successfully");
            log.info("  Instance ID: {}", config.instanceId());
            log.info("  Max Connections: {}", config.maxConnectionsPerTenant());
            log.info("  Caching: {}", config.enableCaching() ? "enabled" : "disabled");
            log.info("  Metrics: {}", config.enableMetrics() ? "enabled" : "disabled");
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Shutting down Data-Cloud...");
                client.close();
                log.info("Data-Cloud shutdown complete");
            }));
            
            // Start HTTP server if configured
            if (shouldStartHttpServer(args)) {
                startHttpServer(client, config);
            }

            // Start gRPC server if configured
            if (shouldStartGrpcServer(args)) {
                startGrpcServer(client, args);
            }

            // Keep running
            log.info("Data-Cloud is ready to serve requests");
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Data-Cloud interrupted, shutting down");
        } catch (Exception e) {
            log.error("Failed to start Data-Cloud", e);
            System.exit(1);
        }
    }

    private static DataCloud.DataCloudConfig parseConfig(String[] args) {
        DataCloud.DataCloudConfig.Builder builder = DataCloud.DataCloudConfig.builder();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--instance-id" -> {
                    if (i + 1 < args.length) {
                        builder.instanceId(args[++i]);
                    }
                }
                case "--max-connections" -> {
                    if (i + 1 < args.length) {
                        builder.maxConnectionsPerTenant(Integer.parseInt(args[++i]));
                    }
                }
                case "--enable-caching" -> builder.enableCaching(true);
                case "--disable-caching" -> builder.enableCaching(false);
                case "--enable-metrics" -> builder.enableMetrics(true);
            }
        }
        
        // Environment variable overrides
        String instanceId = System.getenv("DATACLOUD_INSTANCE_ID");
        if (instanceId != null) {
            builder.instanceId(instanceId);
        }
        
        String maxConnections = System.getenv("DATACLOUD_MAX_CONNECTIONS");
        if (maxConnections != null) {
            builder.maxConnectionsPerTenant(Integer.parseInt(maxConnections));
        }
        
        return builder.build();
    }

    private static boolean shouldStartGrpcServer(String[] args) {
        for (String arg : args) {
            if ("--grpc".equals(arg)) {
                return true;
            }
        }
        return System.getenv("DATACLOUD_GRPC_ENABLED") != null
                || System.getenv("DATACLOUD_GRPC_PORT") != null;
    }

    private static void startGrpcServer(DataCloudClient client, String[] args) {
        try {
            DataCloudGrpcServer grpcServer = new DataCloudGrpcServer(client.eventLogStore());
            grpcServer.start();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("Stopping gRPC server...");
                grpcServer.close();
            }));
        } catch (Exception e) {
            log.error("Failed to start gRPC server", e);
        }
    }

    private static boolean shouldStartHttpServer(String[] args) {
        for (String arg : args) {
            if ("--http".equals(arg) || "--server".equals(arg)) {
                return true;
            }
        }
        return System.getenv("DATACLOUD_HTTP_ENABLED") != null;
    }

    private static void startHttpServer(DataCloudClient client, DataCloud.DataCloudConfig config) {
        int port = 8090;
        String portEnv = System.getenv("DATACLOUD_HTTP_PORT");
        if (portEnv != null) {
            port = Integer.parseInt(portEnv);
        }

        // Wire optional brain + learning bridge when DATACLOUD_BRAIN_ENABLED=true
        DataCloudBrain brain = null;
        DataCloudLearningBridge learningBridge = null;

        String brainEnabled = System.getenv("DATACLOUD_BRAIN_ENABLED");
        if ("true".equalsIgnoreCase(brainEnabled)) {
            try {
                brain = DataCloudBrainModule.createStandalone(null);
                log.info("Brain initialised (standalone mode)");

                learningBridge = new DataCloudLearningBridge(brain);
                learningBridge.start();
                log.info("Learning bridge started (interval=5min)");

                final DataCloudLearningBridge bridgeRef = learningBridge;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("Closing learning bridge...");
                    bridgeRef.close();
                }));
            } catch (Exception e) {
                log.warn("Failed to start brain/learning bridge, continuing without: {}", e.getMessage(), e);
                brain = null;
                learningBridge = null;
            }
        }

        // Wire analytics engine when DATACLOUD_ANALYTICS_ENABLED=true (DC-9)
        AnalyticsQueryEngine analyticsEngine = null;
        if ("true".equalsIgnoreCase(System.getenv("DATACLOUD_ANALYTICS_ENABLED"))) {
            try {
                analyticsEngine = new AnalyticsQueryEngine();
                log.info("AnalyticsQueryEngine initialised (standalone mode)");
            } catch (Exception e) {
                log.warn("Failed to start analytics engine, continuing without: {}", e.getMessage(), e);
            }
        }

        // Wire AI services (model registry + feature store) when DATACLOUD_AI_ENABLED=true (DC-11)
        // Requires DATACLOUD_DB_URL/USER/PASSWORD to be set.
        AIModelManager aiModelManager = null;
        FeatureStoreService featureStoreService = null;
        if ("true".equalsIgnoreCase(System.getenv("DATACLOUD_AI_ENABLED"))) {
            try {
                DataSource aiDataSource = buildAiDataSource();
                NoopMetricsCollector metrics = new NoopMetricsCollector();
                AiMetricsEmitter aiMetrics = new AiMetricsEmitter(metrics);
                ModelRegistryService modelRegistry = new ModelRegistryService(aiDataSource, metrics);
                aiModelManager = new AIModelManager(modelRegistry, aiMetrics);
                featureStoreService = new FeatureStoreService(aiDataSource, metrics);
                log.info("AI services initialised (model registry + feature store)");
            } catch (Exception e) {
                log.warn("Failed to start AI services, continuing without: {}", e.getMessage(), e);
                aiModelManager = null;
                featureStoreService = null;
            }
        }

        // Wire report service when analytics engine is available (DC-10)
        ReportService reportService = null;
        if (analyticsEngine != null) {
            try {
                reportService = new ReportService(analyticsEngine);
                log.info("ReportService initialised (analytics-only mode; use EntityExportService for full export)");
            } catch (Exception e) {
                log.warn("Failed to start report service, continuing without: {}", e.getMessage(), e);
            }
        }

        try {
            DataCloudHttpServer httpServer = new DataCloudHttpServer(client, port, brain, learningBridge, analyticsEngine)
                    .withReportService(reportService)
                    .withAiModelManager(aiModelManager)
                    .withFeatureStoreService(featureStoreService);
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

    /**
     * Creates a HikariCP {@link DataSource} for AI services from environment variables.
     *
     * <p>Reads {@code DATACLOUD_DB_URL}, {@code DATACLOUD_DB_USER}, and
     * {@code DATACLOUD_DB_PASSWORD}. Falls back to sensible defaults for the pool
     * so that lightweight deployments work without explicit tuning.
     *
     * @return configured {@link HikariDataSource}
     * @throws IllegalStateException if required env vars are missing
     *
     * @doc.type method
     * @doc.purpose Create AI-service DataSource from environment variables
     * @doc.layer product
     * @doc.pattern Factory
     */
    private static DataSource buildAiDataSource() {
        String url = System.getenv("DATACLOUD_DB_URL");
        String user = System.getenv("DATACLOUD_DB_USER");
        String password = System.getenv("DATACLOUD_DB_PASSWORD");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                    "DATACLOUD_DB_URL is required when DATACLOUD_AI_ENABLED=true");
        }
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTimeout(30_000L);
        cfg.setIdleTimeout(600_000L);
        cfg.setMaxLifetime(1_800_000L);
        cfg.setPoolName("dc-ai-services");
        cfg.addDataSourceProperty("ApplicationName", "data-cloud-ai");
        return new HikariDataSource(cfg);
    }
}
