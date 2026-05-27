package com.ghatana.stt.grpc;

import com.ghatana.audio.video.common.AudioVideoGrpcServerBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Standalone gRPC server for the STT (Speech-to-Text) service.
 *
 * <p>Binds {@link SttGrpcService} on the port configured by {@code STT_GRPC_PORT}
 * (default 50051) and starts a sidecar health/metrics server for liveness
 * and Prometheus scraping.
 *
 * @doc.type class
 * @doc.purpose STT gRPC server bootstrap and lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SttGrpcServer extends AudioVideoGrpcServerBase {

    private static final Logger LOG = LoggerFactory.getLogger(SttGrpcServer.class);
    private static final int DEFAULT_PORT = 50051;

    /**
     * Constructs and configures the STT gRPC server.
     *
     * @param port TCP port to bind
     */
    public SttGrpcServer(int port) {
        super("stt-service", port, new SttGrpcService(new SimpleMeterRegistry()));
    }

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        // AV-P0-003: Smoke-test mode — verify classpath + mainClass resolution and exit cleanly.
        if (Boolean.getBoolean("av.smokeTest")) {
            LOG.info("[smoke-test] SttGrpcServer classpath check passed — exiting cleanly.");
            return;
        }

        int port = Integer.parseInt(
                System.getenv().getOrDefault("STT_GRPC_PORT", String.valueOf(DEFAULT_PORT)));

        try {
            new SttGrpcServer(port).startAndAwaitShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("STT server failed", e);
            System.exit(1);
        }
    }
}
