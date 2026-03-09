package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class MultimodalGrpcServer {
    
    private static final Logger LOG = LoggerFactory.getLogger(MultimodalGrpcServer.class);
    
    private final int port;
    private final Server server;
    
    public MultimodalGrpcServer(int port) {
        this.port = port;
        this.server = ServerBuilder.forPort(port)
            .intercept(TenantGrpcInterceptor.lenient())
            .addService(new MultimodalGrpcService())
            .build();
    }
    
    public void start() throws IOException {
        server.start();
        LOG.info("Multimodal gRPC server started on port {}", port);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down Multimodal gRPC server");
            try {
                MultimodalGrpcServer.this.stop();
            } catch (InterruptedException e) {
                LOG.error("Error during shutdown", e);
            }
        }));
    }
    
    public void stop() throws InterruptedException {
        if (server != null) {
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            LOG.info("Multimodal gRPC server stopped");
        }
    }
    
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }
    
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("MULTIMODAL_GRPC_PORT", "50055"));
        
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
