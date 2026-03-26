package com.ghatana.tts.grpc;

import com.ghatana.audio.video.common.GrpcInterceptorChain;
import com.ghatana.audio.video.common.health.HealthMetricsServer;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Standalone gRPC server for the TTS (Text-to-Speech) service.
 *
 * <p>Binds {@link TtsGrpcService} on the port configured by {@code TTS_GRPC_PORT}
 * (default 50052) and starts a sidecar {@link HealthMetricsServer} for liveness
 * and Prometheus scraping.
 *
 * @doc.type class
 * @doc.purpose TTS gRPC server bootstrap and lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */
public class TtsGrpcServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(TtsGrpcServer.class);
    private static final int DEFAULT_PORT = 50052;

    private final int port;
    private final Server server;
    private final HealthMetricsServer healthServer;

    /**
     * Constructs and configures the gRPC server.
     *
     * @param port TCP port to bind
     */
    public TtsGrpcServer(int port) {
        this.port = port;

        ServerBuilder<?> builder = ServerBuilder.forPort(port)
                .intercept(TenantGrpcInterceptor.lenient());
        GrpcInterceptorChain.build().forEach(builder::intercept);

        this.server = builder
                .addService(new TtsGrpcService(new SimpleMeterRegistry()))
                .build();

        this.healthServer = new HealthMetricsServer("tts-service", () -> !server.isShutdown());
    }

    /**
     * Starts the gRPC server and health endpoint, then registers a JVM
     * shutdown hook for graceful termination.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        server.start();
        healthServer.start();
        LOG.info("TTS gRPC server started on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down TTS gRPC server");
            try {
                close();
            } catch (Exception e) {
                LOG.error("Error during shutdown", e);
            }
        }));
    }

    @Override
    public void close() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        healthServer.close();
        LOG.info("TTS gRPC server stopped");
    }

    /**
     * Blocks the calling thread until the server shuts down.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("TTS_GRPC_PORT", String.valueOf(DEFAULT_PORT)));

        TtsGrpcServer server = new TtsGrpcServer(port);
        try {
            server.start();
            server.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("TTS server failed", e);
            System.exit(1);
        }
    }
}
