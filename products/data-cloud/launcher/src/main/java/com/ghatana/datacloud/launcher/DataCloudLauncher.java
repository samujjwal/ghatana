package com.ghatana.datacloud.launcher;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.grpc.DataCloudGrpcServer;
import com.ghatana.datacloud.launcher.http.DataCloudHttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data-Cloud Standalone Launcher - Entry point for standalone deployment.
 *
 * @since 1.0.0
 */
public class DataCloudLauncher {

    private static final Logger log = LoggerFactory.getLogger(DataCloudLauncher.class);

    public static void main(String[] args) {
        log.info("Starting Data-Cloud Standalone...");
        
        try {
            // Parse configuration from args or environment
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
        
        try {
            DataCloudHttpServer httpServer = new DataCloudHttpServer(client, port);
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
