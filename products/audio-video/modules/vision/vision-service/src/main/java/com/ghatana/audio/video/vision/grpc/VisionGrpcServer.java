package com.ghatana.audio.video.vision.grpc;

import com.ghatana.audio.video.common.AudioVideoGrpcServerBase;
import com.ghatana.audio.video.common.observability.MediaProcessingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Standalone gRPC server for the Vision (object detection) service.
 *
 * @doc.type class
 * @doc.purpose Vision gRPC server bootstrap and lifecycle management
 * @doc.layer product
 * @doc.pattern Service
 */
public final class VisionGrpcServer extends AudioVideoGrpcServerBase {

    private static final Logger LOG = LoggerFactory.getLogger(VisionGrpcServer.class);
    private static final int DEFAULT_PORT = 50054;

    /**
     * Constructs and configures the Vision gRPC server.
     *
     * @param port TCP port to bind
     */
    public VisionGrpcServer(int port) {
        super("vision-service", port, new VisionGrpcService(MediaProcessingMetrics.create()));
    }

    /**
     * Application entry point.
     *
     * @param args unused
     */
    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("VISION_GRPC_PORT",
                String.valueOf(DEFAULT_PORT)));

        try {
            new VisionGrpcServer(port).startAndAwaitShutdown();
        } catch (IOException | InterruptedException e) {
            LOG.error("Vision server failed", e);
            System.exit(1);
        }
    }
}
