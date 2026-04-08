package com.ghatana.datacloud.launcher.bootstrap;

import com.ghatana.aiplatform.featurestore.FeatureStoreService;
import com.ghatana.aiplatform.observability.AiMetricsEmitter;
import com.ghatana.aiplatform.registry.ModelRegistryService;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.ai.AIModelManager;
import com.ghatana.datacloud.analytics.AnalyticsQueryEngine;
import com.ghatana.datacloud.analytics.report.ReportService;
import com.ghatana.datacloud.brain.DataCloudBrain;
import com.ghatana.datacloud.client.LearningSignalStore;
import com.ghatana.datacloud.di.DataCloudBrainModule;
import com.ghatana.datacloud.infrastructure.config.DataCloudDatabaseConfig;
import com.ghatana.datacloud.launcher.JdbcDatabaseHealthProbe;
import com.ghatana.datacloud.launcher.DataCloudLauncherSettings;
import com.ghatana.datacloud.launcher.DataCloudTransportStartupException;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import com.ghatana.datacloud.launcher.learning.DataCloudLearningBridge;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.slf4j.Logger;

/**
 * @doc.type class
 * @doc.purpose Starts and manages standalone Data-Cloud HTTP dependencies and transport lifecycle
 * @doc.layer product
 * @doc.pattern Bootstrap
 */
public final class DataCloudHttpLauncherBootstrap {

    private DataCloudHttpLauncherBootstrap() {}

    public static void start(DataCloudClient client, Logger log) {
        start(client, log, System.getenv());
    }

    static void start(DataCloudClient client, Logger log, Map<String, String> env) {
        start(client, log, env, DataCloudBrainModule::createStandalone, DataCloudLearningBridge::new);
    }

    static void start(
            DataCloudClient client,
            Logger log,
            Map<String, String> env,
            Function<LearningSignalStore, DataCloudBrain> brainFactory,
            Function<DataCloudBrain, DataCloudLearningBridge> learningBridgeFactory) {
        int port = DataCloudLauncherSettings.resolveHttpPort(env);

        DataCloudBrain brain = null;
        DataCloudLearningBridge learningBridge = null;

        if (DataCloudLauncherSettings.isBrainEnabled(env)) {
            BrainServices brainServices = startBrainServices(
                    log,
                    () -> brainFactory.apply(null),
                    learningBridgeFactory,
                    Runtime.getRuntime()::addShutdownHook);
            brain = brainServices.brain();
            learningBridge = brainServices.learningBridge();
        }

        AnalyticsServices analyticsServices = DataCloudLauncherSettings.isAnalyticsEnabled(env)
                ? startAnalyticsServices(log, AnalyticsQueryEngine::new, ReportService::new)
                : AnalyticsServices.disabled();
        AnalyticsQueryEngine analyticsEngine = analyticsServices.analyticsEngine();

        AIModelManager aiModelManager = null;
        FeatureStoreService featureStoreService = null;
        ReportService reportService = analyticsServices.reportService();

        boolean databaseEnabled = DataCloudLauncherSettings.isDatabaseEnabled(env);
        boolean aiEnabled = DataCloudLauncherSettings.isAiEnabled(env);
        DataSource databaseDataSource = null;
        if (databaseEnabled || aiEnabled) {
            databaseDataSource = startRequiredDatabaseDataSource(log, DataCloudHttpLauncherBootstrap::buildDatabaseDataSource);
        }

        if (aiEnabled && databaseDataSource != null) {
            DataSource aiDataSource = databaseDataSource;
            AiServices aiServices = startRequiredAiServices(log, () -> buildAiServices(aiDataSource));
            aiModelManager = aiServices.aiModelManager();
            featureStoreService = aiServices.featureStoreService();
        }

        try {
            DataCloudHttpServer httpServer = new DataCloudHttpServer(client, port, brain, learningBridge, analyticsEngine)
                    .withReportService(reportService)
                    .withAiModelManager(aiModelManager)
                    .withFeatureStoreService(featureStoreService)
                    .withMetricsCollector(MetricsCollectorFactory.create(
                            new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                                    io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT)));
            startTransport(
                    httpServer,
                    port,
                    databaseEnabled,
                    databaseDataSource,
                    log,
                    Runtime.getRuntime()::addShutdownHook);
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
            closeDataSource(databaseDataSource);
            throw new DataCloudTransportStartupException(
                    "Failed to start HTTP server on port " + port,
                    e);
        }
    }

    private static DataSource buildDatabaseDataSource() {
        return DataCloudDatabaseConfig.fromEnvironment("DATACLOUD_DB")
                .createDataSource();
    }

    static DataSource startRequiredDatabaseDataSource(
            Logger log,
            Supplier<DataSource> dataSourceSupplier) {
        try {
            DataSource dataSource = dataSourceSupplier.get();
            log.info("Standalone database DataSource initialised");
            return dataSource;
        } catch (Exception e) {
            log.error("Failed to create standalone database DataSource for enabled DB-backed features", e);
            throw new DataCloudTransportStartupException(
                    "Failed to create standalone database DataSource for enabled DB-backed features",
                    e);
        }
    }

    static AiServices startRequiredAiServices(
            Logger log,
            Supplier<AiServices> aiServicesSupplier) {
        try {
            AiServices aiServices = aiServicesSupplier.get();
            log.info("AI services initialised (model registry + feature store)");
            return aiServices;
        } catch (Exception e) {
            log.error("Failed to start AI services while DATACLOUD_AI_ENABLED=true", e);
            throw new DataCloudTransportStartupException(
                    "Failed to start AI services while DATACLOUD_AI_ENABLED=true",
                    e);
        }
    }

    private static AiServices buildAiServices(DataSource databaseDataSource) {
        io.micrometer.prometheusmetrics.PrometheusMeterRegistry promRegistry =
                new io.micrometer.prometheusmetrics.PrometheusMeterRegistry(
                        io.micrometer.prometheusmetrics.PrometheusConfig.DEFAULT);
        MetricsCollector metrics = MetricsCollectorFactory.create(promRegistry);
        AiMetricsEmitter aiMetrics = new AiMetricsEmitter(metrics);
        ModelRegistryService modelRegistry = new ModelRegistryService(databaseDataSource, metrics);
        return new AiServices(
                new AIModelManager(modelRegistry, aiMetrics),
                new FeatureStoreService(databaseDataSource, metrics));
    }

    static void startTransport(
            DataCloudHttpServer httpServer,
            int port,
            boolean databaseEnabled,
            DataSource databaseDataSource,
            Logger log,
            Consumer<Thread> shutdownHookRegistrar) {
        try {
            if (databaseEnabled && databaseDataSource != null) {
                httpServer.withHealthSubsystem("database", new JdbcDatabaseHealthProbe(databaseDataSource, 5));
            }
            httpServer.start();
            log.info("HTTP server started on port {}", port);

            shutdownHookRegistrar.accept(new Thread(() -> {
                log.info("Stopping HTTP server...");
                httpServer.stop();
                closeDataSource(databaseDataSource);
            }));
        } catch (Exception e) {
            log.error("Failed to start HTTP server on port {}", port, e);
            closeDataSource(databaseDataSource);
            throw new DataCloudTransportStartupException(
                    "Failed to start HTTP server on port " + port,
                    e);
        }
    }

    static BrainServices startBrainServices(
            Logger log,
            Supplier<DataCloudBrain> brainSupplier,
            Function<DataCloudBrain, DataCloudLearningBridge> learningBridgeFactory,
            Consumer<Thread> shutdownHookRegistrar) {
        try {
            DataCloudBrain brain = brainSupplier.get();
            log.info("Brain initialised (standalone mode)");

            DataCloudLearningBridge learningBridge = learningBridgeFactory.apply(brain);
            learningBridge.start();
            log.info("Learning bridge started (interval=5min)");

            shutdownHookRegistrar.accept(new Thread(() -> {
                log.info("Closing learning bridge...");
                learningBridge.close();
            }));

            return new BrainServices(brain, learningBridge);
        } catch (Exception e) {
            log.warn("Failed to start brain/learning bridge, continuing without: {}", e.getMessage(), e);
            return BrainServices.disabled();
        }
    }

    static AnalyticsServices startAnalyticsServices(
            Logger log,
            Supplier<AnalyticsQueryEngine> analyticsEngineSupplier,
            Function<AnalyticsQueryEngine, ReportService> reportServiceFactory) {
        try {
            AnalyticsQueryEngine analyticsEngine = analyticsEngineSupplier.get();
            log.info("AnalyticsQueryEngine initialised (standalone mode)");

            try {
                ReportService reportService = reportServiceFactory.apply(analyticsEngine);
                log.info("ReportService initialised (analytics-only mode; use EntityExportService for full export)");
                return new AnalyticsServices(analyticsEngine, reportService);
            } catch (Exception e) {
                log.warn("Failed to start report service, continuing without: {}", e.getMessage(), e);
                return new AnalyticsServices(analyticsEngine, null);
            }
        } catch (Exception e) {
            log.warn("Failed to start analytics engine, continuing without: {}", e.getMessage(), e);
            return AnalyticsServices.disabled();
        }
    }

    private static void closeDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource && !hikariDataSource.isClosed()) {
            hikariDataSource.close();
        }
    }

    record BrainServices(DataCloudBrain brain, DataCloudLearningBridge learningBridge) {
        static BrainServices disabled() {
            return new BrainServices(null, null);
        }
    }

    record AnalyticsServices(AnalyticsQueryEngine analyticsEngine, ReportService reportService) {
        static AnalyticsServices disabled() {
            return new AnalyticsServices(null, null);
        }
    }

    record AiServices(AIModelManager aiModelManager, FeatureStoreService featureStoreService) {}
}