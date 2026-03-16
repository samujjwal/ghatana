package com.ghatana.stt.core.grpc;

import com.ghatana.audio.video.common.GrpcInterceptorChain;
import com.ghatana.audio.video.common.health.HealthMetricsServer;
import com.ghatana.audio.video.common.platform.AiRegistryClient;
import com.ghatana.stt.core.api.AdaptiveSTTEngine;
import com.ghatana.stt.core.api.AdaptiveSTTEngineFactory;
import com.ghatana.stt.core.config.EngineConfig;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;

import java.io.IOException;
import java.util.Optional;

/**
 * Standalone gRPC server exposing the AdaptiveSTTEngine via {@link SttGrpcService}.
 *
 * <p>This is primarily intended for the desktop client and other services
 * that communicate over gRPC.</p>
 *
 * @doc.type class
 * @doc.purpose STT gRPC server bootstrap
 * @doc.layer transport
 */
public final class SttGrpcServer implements AutoCloseable {

    private final int port;
    private final Server server;
    private final AdaptiveSTTEngine engine;
    private final HealthMetricsServer healthServer;

    public SttGrpcServer(int port, EngineConfig config) {
        this.port = port;
        this.engine = AdaptiveSTTEngineFactory.create(config);
        ServerBuilder<?> builder = ServerBuilder.forPort(port)
            .intercept(TenantGrpcInterceptor.lenient());
        GrpcInterceptorChain.build().forEach(builder::intercept);
        this.server = builder
            .addService(new SttGrpcService(engine))
            .build();
        this.healthServer = new HealthMetricsServer("stt-service", () -> !server.isShutdown());
    }

    public void start() throws IOException {
        server.start();
        healthServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        System.out.printf("STT gRPC server started on port %d%n", port);
    }

    private void shutdown() {
        try {
            close();
        } catch (Exception ignored) {
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        server.awaitTermination();
    }

    @Override
    public void close() {
        server.shutdown();
        healthServer.close();
        try {
            engine.close();
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("STT_GRPC_PORT", "50051"));

        // Resolve the active model version from the platform AI Registry at startup.
        // Override the tenant via AI_MODEL_TENANT env var (defaults to "default").
        String modelTenant = System.getenv().getOrDefault("AI_MODEL_TENANT", "default");
        Optional<String> activeModel = AiRegistryClient.getInstance().findActiveModel(modelTenant, "whisper-base");
        activeModel.ifPresentOrElse(
                model -> System.out.printf("[STT] Active model resolved from registry: %s%n", model),
                () -> System.out.println("[STT] AI Registry not configured or no active model found — using default engine")
        );

        EngineConfig config = EngineConfig.builder()
            .build();

        SttGrpcServer server = new SttGrpcServer(port, config);
        server.start();
        server.blockUntilShutdown();
    }
}
