package com.ghatana.datacloud.launcher;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.bootstrap.DataCloudGrpcLauncherBootstrap;
import com.ghatana.datacloud.launcher.bootstrap.DataCloudHttpLauncherBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data-Cloud Standalone Launcher - Entry point for standalone deployment.
 *
 * <p>Starts HTTP and/or gRPC server based on command-line flags or environment
 * variables. When {@code DATACLOUD_BRAIN_ENABLED=true}, also wires the
 * cognitive brain and learning bridge so that {@code /api/v1/brain/**} and
 * {@code /api/v1/learning/**} endpoints are active.
 *
 * <h2>Local Development Mode (B2)</h2>
 * <p>Set {@code DATACLOUD_PROFILE=local} (or pass {@code --profile=local}) to
 * launch a fully self-contained single-process server with no external
 * infrastructure. In this mode the server uses an H2 in-process store for
 * entities and an in-memory event log. No PostgreSQL, Redis, ClickHouse, or
 * Iceberg credentials are required.
 *
 * <pre>
 *   # Minimal local-dev invocation
 *   DATACLOUD_PROFILE=local \
 *   DATACLOUD_HTTP_ENABLED=true \
 *   ./gradlew :products:data-cloud:delivery:launcher:runLauncher
 * </pre>
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
            DataCloud.DataCloudConfig.DataCloudProfile profile = DataCloudLauncherSettings.resolveProfile(args, System.getenv());
            boolean embeddedProfile = DataCloudLauncherSettings.isEmbeddedProfile(profile);

            if (!embeddedProfile) {
                // Validate configuration before creating any resources (fail-fast)
                DataCloudConfigValidator.fromEnvironment().validate();
            } else {
                log.info("Embedded profile {} active — skipping external infra connectivity checks", profile.name().toLowerCase());
            }

            DataCloud.DataCloudConfig config = DataCloudLauncherSettings.parseClientConfig(args);
            boolean startHttpServer = DataCloudLauncherSettings.shouldStartHttpServer(args);
            boolean startGrpcServer = DataCloudLauncherSettings.shouldStartGrpcServer(args);
            if (!DataCloudLauncherSettings.hasEnabledTransport(args, System.getenv())) {
                throw new IllegalStateException(
                        "No transport enabled. Configure DATACLOUD_HTTP_ENABLED, DATACLOUD_GRPC_ENABLED, DATACLOUD_GRPC_PORT, or pass --http/--grpc.");
            }

            DataCloudClient client = profile == DataCloud.DataCloudConfig.DataCloudProfile.LOCAL
                ? DataCloud.embedded()
                : DataCloud.create(config);

            log.info("Data-Cloud Client started successfully");
            log.info("  Mode: {}", switch (profile) {
                case LOCAL -> "local-dev (in-memory embedded, no external infra)";
                case SOVEREIGN -> "sovereign (file-backed H2, no external infra)";
                case STAGING -> "staging";
                case PRODUCTION -> "production";
            });
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
            if (startHttpServer) {
                startHttpServer(client);
            }

            // Start gRPC server if configured
            if (startGrpcServer) {
                startGrpcServer(client);
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

    private static void startGrpcServer(DataCloudClient client) {
        DataCloudGrpcLauncherBootstrap.start(client, log);
    }

    private static void startHttpServer(DataCloudClient client) {
        DataCloudHttpLauncherBootstrap.start(client, log);
    }
}
