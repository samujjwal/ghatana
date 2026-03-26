package com.ghatana.stt.grpc;

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
 * Standalone gRPC server for the STT (Speech-to-Text) service.
 *
 * <p>Binds {@link SttGrpcService} on the port configured by {@code STT_GRPC_PORT}
 * (default 50051) and starts a sidecar {@link HealthMetricsServer} for liveness
 * and Prometheus scraping.
 *
 * @doc.type class
 * @doc.purpose STT gRPC server bootstrap and lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */
public class SttGrpcServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(SttGrpcServer.class);
    private static final int DEFAULT_PORT = 50051;

    private final int port;
    private final Server server;
    private final HealthMetricsServer healthServer;

    /**
     * Constructs and configures the gRPC server.
     *
     * @param port TCP port to bind
     */
    public SttGrpcServer(int port) {
        this.port = port;

        ServerBuilder<?> builder = ServerBuilder.forPort(port)
                .intercept(TenantGrpcInterceptor.lenient());
        GrpcInterceptorChain.build().forEach(builder::intercept);

        this.server = builder
                .addService(new SttGrpcService(new SimpleMeterRegistry()))
                .build();

        this.healthServer = new HealthMetricsServer("stt-service", () -> !server.isShutdown());
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
        LOG.info("STT gRPC server started on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down STT gRPC server");
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
        LOG.info("STT gRPC server stopped");
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
                System.getenv().getOrDefault("STT_GRPC_PORT", String.valueOf(DEFAULT_PORT)));

        SttGrpcServer server = new SttGrpcServer(port);
        try {
            server.start();
            server.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("STT server failed", e);
            System.exit(1);
        }
    }
}
