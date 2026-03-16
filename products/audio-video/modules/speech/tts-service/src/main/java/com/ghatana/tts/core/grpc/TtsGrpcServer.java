package com.ghatana.tts.core.grpc;

import com.ghatana.audio.video.common.GrpcInterceptorChain;
import com.ghatana.audio.video.common.health.HealthMetricsServer;
import com.ghatana.audio.video.common.platform.AiRegistryClient;
import com.ghatana.tts.core.api.TtsEngine;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server for TTS service.
 * 
 * @doc.type class
 * @doc.purpose TTS gRPC server bootstrap
 * @doc.layer product
 * @doc.pattern Service
 */
public class TtsGrpcServer implements AutoCloseable {

    private static final Logger LOG = LogManager.getLogger(TtsGrpcServer.class);
    private static final int DEFAULT_PORT = 50052;

    private final Server server;
    private final TtsEngine engine;
    private final HealthMetricsServer healthServer;

    public TtsGrpcServer(TtsEngine engine, int port) {
        this.engine = engine;
        ServerBuilder<?> builder = ServerBuilder.forPort(port)
            .intercept(TenantGrpcInterceptor.lenient());
        GrpcInterceptorChain.build().forEach(builder::intercept);
        this.server = builder
            .addService(new TtsGrpcService(engine))
            .build();
        this.healthServer = new HealthMetricsServer("tts-service", () -> !server.isShutdown());
    }

    public TtsGrpcServer(TtsEngine engine) {
        this(engine, getPortFromEnv());
    }

    private static int getPortFromEnv() {
        String portStr = System.getenv("TTS_GRPC_PORT");
        if (portStr != null && !portStr.isEmpty()) {
            try {
                return Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                LOG.warn("Invalid TTS_GRPC_PORT value: {}, using default: {}", portStr, DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }

    public void start() throws IOException {
        server.start();
        healthServer.start();
        LOG.info("TTS gRPC server started on port {}", server.getPort());

        // Resolve active model from platform AI Registry at startup
        String modelTenant = System.getenv().getOrDefault("AI_MODEL_TENANT", "default");
        AiRegistryClient.getInstance().findActiveModel(modelTenant, "piper-en")
                .ifPresentOrElse(
                        model -> LOG.info("[TTS] Active model resolved from registry: {}", model),
                        () -> LOG.info("[TTS] AI Registry not configured or no active model — using default engine")
                );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down TTS gRPC server...");
            try {
                close();
            } catch (Exception e) {
                LOG.error("Error during shutdown", e);
            }
        }));
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public int getPort() {
        return server.getPort();
    }

    @Override
    public void close() throws Exception {
        if (server != null) {
            server.shutdown();
            try {
                if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        healthServer.close();
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                LOG.error("Error closing engine", e);
            }
        }
        LOG.info("TTS gRPC server stopped");
    }
}
