package com.ghatana.datacloud.launcher.bootstrap;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.DataCloudTransportStartupException;
import com.ghatana.datacloud.launcher.grpc.DataCloudGrpcServer;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * @doc.type class
 * @doc.purpose Starts and manages the standalone Data-Cloud gRPC transport lifecycle
 * @doc.layer product
 * @doc.pattern Bootstrap
 */
public final class DataCloudGrpcLauncherBootstrap {

    private DataCloudGrpcLauncherBootstrap() {}

    public static void start(DataCloudClient client, Logger log) {
        // TODO: Fix EventLogStore type mismatch - DataCloudClient returns spi.EventLogStore but gRPC server expects platform.domain.eventstore.EventLogStore
        // DataCloudGrpcServer grpcServer = new DataCloudGrpcServer(client.eventLogStore());
        // startTransport(log, grpcServer::start, grpcServer::close, Runtime.getRuntime()::addShutdownHook);
        log.warn("gRPC transport temporarily disabled due to EventLogStore type mismatch");
    }

    static void startTransport(
            Logger log,
            ThrowingRunnable startServer,
            Runnable stopServer,
            Consumer<Thread> shutdownHookRegistrar) {
        try {
            startServer.run();

            shutdownHookRegistrar.accept(new Thread(() -> {
                log.info("Stopping gRPC server...");
                stopServer.run();
            }));
        } catch (Exception e) {
            log.error("Failed to start gRPC server", e);
            throw new DataCloudTransportStartupException("Failed to start gRPC server", e);
        }
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
