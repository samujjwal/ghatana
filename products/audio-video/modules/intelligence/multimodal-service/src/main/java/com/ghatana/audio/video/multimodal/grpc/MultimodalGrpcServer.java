package com.ghatana.audio.video.multimodal.grpc;

import com.ghatana.audio.video.common.AudioVideoGrpcServerBase;
import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Standalone gRPC server for the Multimodal analysis service.
 *
 * @doc.type class
 * @doc.purpose Multimodal gRPC server bootstrap and lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MultimodalGrpcServer extends AudioVideoGrpcServerBase {

    private static final Logger LOG = LoggerFactory.getLogger(MultimodalGrpcServer.class);
    private static final int DEFAULT_PORT = 50055;

    /**
     * Constructs and configures the Multimodal gRPC server.
     *
     * @param port TCP port to bind
     */
    public MultimodalGrpcServer(int port) {
        super("multimodal-service", port, new MultimodalGrpcService(MediaProcessingMetrics.create()));
    }

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        // AV-P0-003: Smoke-test mode — verify classpath + mainClass resolution and exit cleanly.
        if (Boolean.getBoolean("av.smokeTest")) {
            LOG.info("[smoke-test] MultimodalGrpcServer classpath check passed — exiting cleanly.");
            return;
        }

        int port = Integer.parseInt(System.getenv().getOrDefault("MULTIMODAL_GRPC_PORT",
                String.valueOf(DEFAULT_PORT)));

        try {
            new MultimodalGrpcServer(port).startAndAwaitShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("Multimodal server failed", e);
            System.exit(1);
        }
    }
}
