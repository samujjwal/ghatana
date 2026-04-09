package com.ghatana.audio.video.common;

import com.ghatana.audio.video.common.health.HealthMetricsServer;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Reusable base class for all audio-video gRPC servers.
 *
 * <p>Encapsulates the common lifecycle (port binding, interceptor chain wiring,
 * {@link HealthMetricsServer} sidecar, and graceful shutdown) so that concrete
 * server classes only need to supply the {@link BindableService} implementation
 * and a human-readable service name.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * public final class SttGrpcServer extends AudioVideoGrpcServerBase {
 *
 *     public SttGrpcServer(int port) {
 *         super("stt-service", port, new SttGrpcService(new SimpleMeterRegistry()));
 *     }
 *
 *     public static void main(String[] args) throws Exception {
 *         int port = Integer.parseInt(
 *             System.getenv().getOrDefault("STT_GRPC_PORT", "50051"));
 *         new SttGrpcServer(port).startAndAwaitShutdown();
 *     }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Shared gRPC server base for audio-video services — handles port binding,
 *              interceptor chain, health endpoint, and shutdown lifecycle
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class AudioVideoGrpcServerBase implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AudioVideoGrpcServerBase.class);

    /** Default graceful-shutdown timeout in seconds. */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;

    private final String serviceName;
    private final int port;
    private final Server server;
    private final HealthMetricsServer healthServer;

    /**
     * Constructs and wires the gRPC server.
     *
     * @param serviceName  human-readable service name used in log and health endpoint
     * @param port         TCP port to bind the gRPC server to
     * @param serviceImpl  the gRPC service implementation to expose
     */
    protected AudioVideoGrpcServerBase(String serviceName, int port, BindableService serviceImpl) {
        this(serviceName, port, serviceImpl, GrpcInterceptorChain.build());
    }

    /**
     * Constructs and wires the gRPC server with a custom interceptor list.
     *
     * <p>Use this overload in tests to provide an empty or controlled interceptor list,
     * avoiding dependencies on environment variables (e.g. {@code AV_JWT_SECRET}).
     *
     * @param serviceName  human-readable service name used in log and health endpoint
     * @param port         TCP port to bind the gRPC server to
     * @param serviceImpl  the gRPC service implementation to expose
     * @param interceptors ordered interceptor list to apply (innermost first for gRPC)
     */
    protected AudioVideoGrpcServerBase(
            String serviceName, int port, BindableService serviceImpl,
            java.util.List<io.grpc.ServerInterceptor> interceptors) {
        this.serviceName = serviceName;
        this.port = port;

        ServerBuilder<?> builder = ServerBuilder.forPort(port)
                .intercept(TenantGrpcInterceptor.lenient());
        interceptors.forEach(builder::intercept);

        this.server = builder.addService(serviceImpl).build();
        this.healthServer = new HealthMetricsServer(serviceName, () -> !server.isShutdown());
    }

    /**
     * Starts the gRPC server and the health/metrics sidecar.
     *
     * <p>Also registers a JVM shutdown hook so that a SIGTERM or SIGINT signal
     * triggers {@link #close()} automatically.
     *
     * @throws IOException if the port cannot be bound
     */
    public final void start() throws IOException {
        server.start();
        healthServer.start();
        LOG.info("{} started on port {}", serviceName, port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown hook triggered for {}", serviceName);
            try {
                close();
            } catch (Exception e) {
                LOG.error("Error during {} shutdown", serviceName, e);
            }
        }, serviceName + "-shutdown-hook"));
    }

    /**
     * Starts the server and blocks the calling thread until shutdown.
     * Intended for use in {@code main} methods.
     *
     * @throws IOException          if the port cannot be bound
     * @throws InterruptedException if the thread is interrupted while waiting
     */
    public final void startAndAwaitShutdown() throws IOException, InterruptedException {
        start();
        blockUntilShutdown();
    }

    /**
     * Blocks the calling thread until the server shuts down.
     *
     * @throws InterruptedException if interrupted while waiting
     */
    public final void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Gracefully stops the server, waiting up to {@value #SHUTDOWN_TIMEOUT_SECONDS} seconds.
     */
    @Override
    public final void close() {
        if (server != null && !server.isShutdown()) {
            server.shutdown();
            try {
                if (!server.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    LOG.warn("{} did not shut down cleanly within {}s; forcing", serviceName, SHUTDOWN_TIMEOUT_SECONDS);
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        healthServer.close();
        LOG.info("{} stopped", serviceName);
    }

    /**
     * Returns the TCP port this server is bound to.
     *
     * @return server port
     */
    public final int getPort() {
        return port;
    }

    /**
     * Returns {@code true} if the underlying gRPC server has been shut down.
     *
     * @return shutdown state
     */
    public final boolean isShutdown() {
        return server == null || server.isShutdown();
    }

    /**
     * Returns the human-readable service name used in logging.
     *
     * @return service name
     */
    public final String getServiceName() {
        return serviceName;
    }
}
