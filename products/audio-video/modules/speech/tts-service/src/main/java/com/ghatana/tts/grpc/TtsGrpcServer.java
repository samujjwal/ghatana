package com.ghatana.tts.grpc;

import com.ghatana.audio.video.common.AudioVideoGrpcServerBase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Standalone gRPC server for the TTS (Text-to-Speech) service.
 *
 * <p>Binds {@link TtsGrpcService} on the port configured by {@code TTS_GRPC_PORT}
 * (default 50052) and starts a sidecar health/metrics server for liveness
 * and Prometheus scraping.
 *
 * @doc.type class
 * @doc.purpose TTS gRPC server bootstrap and lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */
public final class TtsGrpcServer extends AudioVideoGrpcServerBase {

    private static final Logger LOG = LoggerFactory.getLogger(TtsGrpcServer.class);
    private static final int DEFAULT_PORT = 50052;

    /**
     * Constructs and configures the TTS gRPC server.
     *
     * @param port TCP port to bind
     */
    public TtsGrpcServer(int port) {
        super("tts-service", port, new TtsGrpcService(new SimpleMeterRegistry()));
    }

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        int port = Integer.parseInt(
                System.getenv().getOrDefault("TTS_GRPC_PORT", String.valueOf(DEFAULT_PORT)));

        try {
            new TtsGrpcServer(port).startAndAwaitShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("TTS server failed", e);
            System.exit(1);
        }
    }
}
