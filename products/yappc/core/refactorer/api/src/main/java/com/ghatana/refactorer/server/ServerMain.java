package com.ghatana.refactorer.server;

import com.ghatana.refactorer.server.config.ConfigLoader;
import com.ghatana.refactorer.server.config.ServerConfig;
import com.ghatana.refactorer.server.grpc.PolyfixGrpcService;
import com.ghatana.refactorer.server.observability.OTelInitializer;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Main entry point for the Polyfix service server. Starts both ActiveJ HTTP server and gRPC server

 * in the same process.

 *

 * @doc.type class

 * @doc.purpose Bootstrap the ServiceRuntime, start HTTP transports, and install shutdown hooks.

 * @doc.layer product

 * @doc.pattern Bootstrap

 */

public final class ServerMain {
    private static final Logger logger = LogManager.getLogger(ServerMain.class);

    public static void main(String[] args) {
        try {
            logger.info("Starting Polyfix service server...");

            // Load configuration
            ServerConfig config = ConfigLoader.load();
            logger.info(
                    "Configuration loaded: HTTP port {}, gRPC port {}",
                    config.httpPort(),
                    config.grpcPort());

            ServiceRuntime runtime = ServiceRuntime.create(config);

            // Initialize observability
            OTelInitializer.initialize(config);

            // Start ActiveJ HTTP server
            ActivejServerFactory.ActivejServerHandle httpServerHandle =
                    ActivejServerFactory.create(
                            config, runtime.jobService(), runtime.accessPolicy());
            httpServerHandle.start();

            // Start gRPC server (Netty)
            Server grpcServer =
                    NettyServerBuilder.forPort(config.grpcPort())
                            .addService(new PolyfixGrpcService(runtime.jobService()))
                            .build()
                            .start();
            logger.info("gRPC server started on port {}", config.grpcPort());

            // Setup shutdown hooks
            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        logger.info("Shutting down servers...");
                                        grpcServer.shutdown();
                                        try {
                                            if (!grpcServer.awaitTermination(5, TimeUnit.SECONDS)) {
                                                grpcServer.shutdownNow();
                                            }
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            logger.warn(
                                                    "Interrupted while waiting for gRPC shutdown",
                                                    e);
                                        }

                                        try {
                                            httpServerHandle.close();
                                        } catch (Exception e) {
                                            logger.warn("Error while shutting down HTTP server", e);
                                        }

                                        logger.info("Servers shut down successfully");
                                    }));

            logger.info("Polyfix service server started successfully");

            // Wait for gRPC server termination
            grpcServer.awaitTermination();

        } catch (Exception e) {
            logger.error("Failed to start Polyfix service server", e);
            System.exit(1);
        }
    }
}
