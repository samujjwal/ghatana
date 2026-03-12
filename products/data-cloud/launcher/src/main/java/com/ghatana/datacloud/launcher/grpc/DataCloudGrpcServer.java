package com.ghatana.datacloud.launcher.grpc;

import com.ghatana.datacloud.grpc.EventLogGrpcService;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.platform.governance.security.TenantGrpcInterceptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Lifecycle wrapper for the Data-Cloud gRPC server.
 *
 * <p>Starts a single-port gRPC server exposing:
 * <ul>
 *   <li>{@link EventLogGrpcService} — {@code EventLogService} (Append, ReadByType)</li>
 * </ul>
 *
 * <p>Port is resolved from the {@code DATACLOUD_GRPC_PORT} environment variable with a default
 * of {@value #DEFAULT_PORT}.
 *
 * @doc.type class
 * @doc.purpose gRPC server bootstrap for Data-Cloud
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public final class DataCloudGrpcServer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DataCloudGrpcServer.class);
    static final int DEFAULT_PORT = 9090;

    private final Server server;

    /**
     * Constructs the gRPC server bound to the given port, backed by the provided store.
     *
     * @param eventLogStore the store that backs {@link EventLogGrpcService}
     * @param port          the TCP port to bind the gRPC server to
     */
    public DataCloudGrpcServer(EventLogStore eventLogStore, int port) {
        this.server = ServerBuilder.forPort(port)
                .intercept(TenantGrpcInterceptor.lenient())
                .addService(new EventLogGrpcService(eventLogStore))
                .build();
    }

    /**
     * Constructs the gRPC server using the port from {@code DATACLOUD_GRPC_PORT} or the default.
     *
     * @param eventLogStore the store that backs {@link EventLogGrpcService}
     */
    public DataCloudGrpcServer(EventLogStore eventLogStore) {
        this(eventLogStore, resolvePort());
    }

    /**
     * Starts the gRPC server.
     *
     * @throws IOException if the port cannot be bound
     */
    public void start() throws IOException {
        server.start();
        log.info("Data-Cloud gRPC server started on port {}", server.getPort());
    }

    /**
     * Returns the port on which the server listens (after {@link #start()} has been called).
     */
    public int getPort() {
        return server.getPort();
    }

    /**
     * Initiates a graceful shutdown, waiting up to 30 seconds for in-flight RPCs to complete.
     */
    @Override
    public void close() {
        log.info("Stopping Data-Cloud gRPC server...");
        server.shutdown();
        try {
            if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("gRPC server did not terminate within 30 s — forcing shutdown");
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
        }
    }

    /** Blocks until the server has terminated (useful for main-thread keep-alive). */
    public void awaitTermination() throws InterruptedException {
        server.awaitTermination();
    }

    private static int resolvePort() {
        String env = System.getenv("DATACLOUD_GRPC_PORT");
        if (env != null && !env.isBlank()) {
            try {
                return Integer.parseInt(env.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid DATACLOUD_GRPC_PORT='{}', using default {}", env, DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }
}
