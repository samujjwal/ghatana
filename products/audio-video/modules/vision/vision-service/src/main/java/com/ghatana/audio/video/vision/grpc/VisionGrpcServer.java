package com.ghatana.audio.video.vision.grpc;

import com.ghatana.audio.video.common.GrpcInterceptorChain;
import com.ghatana.audio.video.common.health.HealthMetricsServer;
import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Standalone gRPC server for the Vision (object detection) service.
 *
 * @doc.type class
 * @doc.purpose Vision gRPC server bootstrap
 * @doc.layer product
 * @doc.pattern Service
 */
public class VisionGrpcServer implements AutoCloseable {
    
    private static final Logger LOG = LoggerFactory.getLogger(VisionGrpcServer.class);
    private static final int DEFAULT_PORT = 50054;
    
    private final int port;
    private final Server server;
    private final HealthMetricsServer healthServer;
    
    public VisionGrpcServer(int port) {
        this.port = port;
        ServerBuilder<?> builder = ServerBuilder.forPort(port)
            .intercept(TenantGrpcInterceptor.lenient());
        GrpcInterceptorChain.build().forEach(builder::intercept);
        this.server = builder
            .addService(new VisionGrpcService(MediaProcessingMetrics.create()))
            .build();
        this.healthServer = new HealthMetricsServer("vision-service", () -> !server.isShutdown());
    }
    
    public void start() throws IOException {
        server.start();
        healthServer.start();
        LOG.info("Vision gRPC server started on port {}", port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Vision gRPC server");
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
        LOG.info("Vision gRPC server stopped");
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("VISION_GRPC_PORT",
            String.valueOf(DEFAULT_PORT)));
        
        VisionGrpcServer server = new VisionGrpcServer(port);
        try {
            server.start();
            server.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("Vision server failed", e);
            System.exit(1);
        }
    }
}
