package com.ghatana.audio.video.multimodal.adapter;

import com.ghatana.audio.video.multimodal.engine.VisualResult;
import com.ghatana.audio.video.multimodal.engine.VisionClientAdapter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * gRPC-backed implementation of {@link VisionClientAdapter}.
 *
 * <p>Calls DetectObjects for single images and AnalyzeVideoFile for video data.
 * The channel is kept open for the lifetime of this adapter.
 */
public class GrpcVisionClientAdapter implements VisionClientAdapter, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcVisionClientAdapter.class);

    private final ManagedChannel channel;

    public GrpcVisionClientAdapter(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(100 * 1024 * 1024) // 100 MB for video frames
                .build();
        LOG.info("Vision gRPC client connected to {}:{}", host, port);
    }

    @Override
    public VisualResult detectObjects(byte[] imageData) {
        try {
            LOG.debug("Sending image ({} bytes) to Vision service", imageData.length);

            /*
             * Replace with generated stub call once proto stubs exist:
             *
             *   VisionServiceGrpc.VisionServiceBlockingStub stub =
             *       VisionServiceGrpc.newBlockingStub(channel)
             *           .withDeadlineAfter(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
             *   DetectRequest req = DetectRequest.newBuilder()
             *       .setImageData(ByteString.copyFrom(imageData))
             *       .setMaxDetections(100)
             *       .build();
             *   DetectResponse resp = stub.detectObjects(req);
             *   List<DetectionResult> detections = resp.getDetectionsList().stream()
             *       .map(d -> new DetectionResult(
             *           d.getClassName(), d.getConfidence(),
             *           d.getBoundingBox().getX(), d.getBoundingBox().getY(),
             *           d.getBoundingBox().getWidth(), d.getBoundingBox().getHeight()))
             *       .toList();
             *   return VisualResult.builder()
             *       .detections(detections)
             *       .sceneDescription(buildScene(detections))
             *       .confidence(detections.isEmpty() ? 0.0 : detections.get(0).getConfidence())
             *       .build();
             */

            LOG.warn("Vision stub not yet wired — returning empty detections");
            return VisualResult.builder()
                    .sceneDescription("No detections (stub)")
                    .detections(new ArrayList<>())
                    .build();

        } catch (StatusRuntimeException e) {
            LOG.error("Vision gRPC call failed: {}", e.getStatus(), e);
            return VisualResult.error("Vision service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            LOG.error("Image detection error", e);
            return VisualResult.error(e.getMessage());
        }
    }

    @Override
    public VisualResult analyseVideo(byte[] videoData, int sampleFps, int maxFrames) {
        try {
            LOG.debug("Sending video ({} bytes, {}fps, max {} frames) to Vision service",
                    videoData.length, sampleFps, maxFrames);

            /*
             * Replace with generated stub call:
             *
             *   VisionServiceGrpc.VisionServiceBlockingStub stub =
             *       VisionServiceGrpc.newBlockingStub(channel)
             *           .withDeadlineAfter(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
             *   VideoFileRequest req = VideoFileRequest.newBuilder()
             *       .setVideoData(ByteString.copyFrom(videoData))
             *       .setSampleFps(sampleFps)
             *       .setMaxFrames(maxFrames)
             *       .build();
             *   VideoFileResponse resp = stub.analyzeVideoFile(req);
             *   List<FrameResult> frameResults = resp.getFrameResultsList().stream()
             *       .map(fr -> new FrameResult(
             *           fr.getFrameNumber(), fr.getTimestampMs(),
             *           fr.getDetectionsList().stream()
             *               .map(d -> new DetectionResult(
             *                   d.getClassName(), d.getConfidence(),
             *                   d.getBoundingBox().getX(), d.getBoundingBox().getY(),
             *                   d.getBoundingBox().getWidth(), d.getBoundingBox().getHeight()))
             *               .toList()))
             *       .toList();
             *   return VisualResult.builder()
             *       .sceneDescription(resp.getSummary())
             *       .frameResults(frameResults)
             *       .detections(frameResults.stream()
             *           .flatMap(f -> f.getDetections().stream()).toList())
             *       .build();
             */

            LOG.warn("Vision video stub not yet wired — returning empty result");
            return VisualResult.builder()
                    .sceneDescription("No video analysis (stub)")
                    .detections(new ArrayList<>())
                    .frameResults(new ArrayList<>())
                    .build();

        } catch (StatusRuntimeException e) {
            LOG.error("Vision gRPC video call failed: {}", e.getStatus(), e);
            return VisualResult.error("Vision service error: " + e.getStatus().getDescription());
        } catch (Exception e) {
            LOG.error("Video analysis error", e);
            return VisualResult.error(e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
