package com.ghatana.audio.video.vision.grpc;

import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class VisionGrpcServer {
    
    private static final Logger LOG = LoggerFactory.getLogger(VisionGrpcServer.class);
    
    private final int port;
    private final Server server;
    
    public VisionGrpcServer(int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
            .intercept(TenantGrpcInterceptor.lenient())
            .addService(new VisionGrpcService())
            .build();
    }
    
    public void start() throws IOException {
        server.start();
        LOG.info("Vision gRPC server started on port {}", port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Vision gRPC server");
            try {
                VisionGrpcServer.this.stop();
            } catch (InterruptedException e) {
                LOG.error("Error during shutdown", e);
            }
        }));
    }
    
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            LOG.info("Vision gRPC server stopped");
        }
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("VISION_GRPC_PORT", "50054"));
        
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
