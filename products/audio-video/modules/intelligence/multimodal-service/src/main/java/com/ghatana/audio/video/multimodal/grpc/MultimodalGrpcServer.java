package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.audio.video.common.GrpcInterceptorChain;
import com.ghatana.audio.video.common.health.HealthMetricsServer;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Standalone gRPC server for the Multimodal analysis service.
 *
 * @doc.type class
 * @doc.purpose Multimodal gRPC server bootstrap
 * @doc.layer product
 * @doc.pattern Service
 */
public class MultimodalGrpcServer implements AutoCloseable {
    
    private static final Logger LOG = LoggerFactory.getLogger(MultimodalGrpcServer.class);
    private static final int DEFAULT_PORT = 50055;
    
    private final int port;
    private final Server server;
    private final HealthMetricsServer healthServer;
    
    public MultimodalGrpcServer(int port) {
        this.port = port;
        ServerBuilder<?> builder = ServerBuilder.forPort(port)
            .intercept(TenantGrpcInterceptor.lenient());
        GrpcInterceptorChain.build().forEach(builder::intercept);
        this.server = builder
            .addService(new MultimodalGrpcService())
            .build();
        this.healthServer = new HealthMetricsServer("multimodal-service", () -> !server.isShutdown());
    }
    
    public void start() throws IOException {
        server.start();
        healthServer.start();
        LOG.info("Multimodal gRPC server started on port {}", port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Multimodal gRPC server");
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
        LOG.info("Multimodal gRPC server stopped");
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("MULTIMODAL_GRPC_PORT",
            String.valueOf(DEFAULT_PORT)));
        
        MultimodalGrpcServer server = new MultimodalGrpcServer(port);
        try {
            server.start();
            server.blockUntilShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("Multimodal server failed", e);
            System.exit(1);
        }
    }
}
