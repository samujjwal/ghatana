package com.ghatana.audio.video.vision.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.service.AudioFileService;
import com.ghatana.audio.video.vision.detection.VisionDetector;
import com.ghatana.audio.video.vision.model.Detection;
import com.ghatana.audio.video.vision.model.DetectionResult;
import com.ghatana.audio.video.vision.video.VideoFrameExtractor;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Vision service with persistence integration.
 *              Detects objects in video/audio files and persists results.
 * @doc.layer product
 * @doc.pattern Service
 */
public class PersistentVisionService {

    private static final Logger LOG = LoggerFactory.getLogger(PersistentVisionService.class);

    private final VisionDetector detector;
    private final VideoFrameExtractor frameExtractor;
    private final AudioFileService audioFileService;
    private final Timer detectTimer;

    public PersistentVisionService(
            VisionDetector detector,
            VideoFrameExtractor frameExtractor,
            AudioFileService audioFileService,
            MeterRegistry meterRegistry) {
        this.detector = Objects.requireNonNull(detector, "detector cannot be null");
        this.frameExtractor = Objects.requireNonNull(frameExtractor, "frameExtractor cannot be null");
        this.audioFileService = Objects.requireNonNull(audioFileService, "audioFileService cannot be null");
        this.detectTimer = Timer.builder("vision.persistent.detect")
            .description("Vision detection latency with persistence")
            .register(meterRegistry);
    }

    /**
     * Detect objects in a video file and persist results.
     *
     * @param tenantId   the tenant ID
     * @param userId       the user ID
     * @param fileName     the video file name
     * @param videoData    the video binary data
     * @param confidenceThreshold minimum confidence for detections (0.0-1.0)
     * @return Promise containing detection results with persisted file ID
     */
    public Promise<DetectionResult> detectAndPersist(
            String tenantId,
            UUID userId,
            String fileName,
            byte[] videoData,
            double confidenceThreshold) {

        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(videoData, "videoData cannot be null");

        long startTime = System.currentTimeMillis();

        // Step 1: Persist video file metadata
        return persistVideoFile(tenantId, userId, fileName, videoData.length)
            .then(audioFile -> {
                LOG.info("[tenant={}] Video file persisted: id={}, file={}",
                    tenantId, audioFile.getId(), fileName);

                // Step 2: Extract frames and detect objects
                return performDetection(videoData, confidenceThreshold)
                    .map(detectionResult -> {
                        long elapsedMs = System.currentTimeMillis() - startTime;
                        detectTimer.record(Duration.ofMillis(elapsedMs));

                        int detectionCount = detectionResult.detections().size();
                        LOG.info("[tenant={}] Vision detection completed: audioId={}, detections={}, elapsedMs={}",
                            tenantId, audioFile.getId(), detectionCount, elapsedMs);

                        return detectionResult;
                    });
            })
            .whenException(e -> {
                LOG.error("[tenant={}] Vision detection failed: {}", tenantId, e.getMessage(), e);
            });
    }

    /**
     * Detect objects in a single frame/image.
     */
    public Promise<DetectionResult> detectFrame(byte[] imageData, double confidenceThreshold) {
        return Promise.ofCallable(() -> {
            List<Detection> detections = detector.detect(imageData, confidenceThreshold);
            return new DetectionResult(detections, detections.size(), Duration.ofMillis(0));
        });
    }

    /**
     * Stream detection results for video frames.
     */
    public void detectStream(
            byte[] videoData,
            double confidenceThreshold,
            java.util.function.Consumer<FrameDetection> onFrame) {

        frameExtractor.extractFrames(videoData, frame -> {
            List<Detection> detections = detector.detect(frame.data(), confidenceThreshold);
            onFrame.accept(new FrameDetection(frame.timestamp(), frame.frameNumber(), detections));
        });
    }

    private Promise<AudioFileEntity> persistVideoFile(
            String tenantId,
            UUID userId,
            String fileName,
            int fileSize) {

        String extension = getExtension(fileName);

        AudioFileEntity entity = new AudioFileEntity(
            UUID.randomUUID(),
            tenantId,
            userId,
            fileName,
            "/storage/video/" + tenantId + "/" + UUID.randomUUID() + "." + extension,
            extension
        );
        entity.setFileSizeBytes((long) fileSize);
        entity.setStatus(AudioFileEntity.ProcessingStatus.PROCESSING);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        return audioFileService.save(tenantId, entity);
    }

    private Promise<DetectionResult> performDetection(byte[] videoData, double confidenceThreshold) {
        return Promise.ofCallable(() -> {
            long startTime = System.currentTimeMillis();

            // For single-frame images
            if (isImageData(videoData)) {
                List<Detection> detections = detector.detect(videoData, confidenceThreshold);
                return new DetectionResult(detections, detections.size(),
                    Duration.ofMillis(System.currentTimeMillis() - startTime));
            }

            // For video, extract key frames and detect
            final int[] totalDetections = {0};
            final List<Detection> allDetections = new java.util.ArrayList<>();

            frameExtractor.extractFrames(videoData, frame -> {
                List<Detection> frameDetections = detector.detect(frame.data(), confidenceThreshold);
                allDetections.addAll(frameDetections);
                totalDetections[0] += frameDetections.size();
            });

            return new DetectionResult(allDetections, totalDetections[0],
                Duration.ofMillis(System.currentTimeMillis() - startTime));
        });
    }

    private boolean isImageData(byte[] data) {
        // Simple check: if data is small, assume it's an image
        // In production, use proper mime type detection
        return data.length < 10 * 1024 * 1024; // < 10MB = image
    }

    private String getExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "mp4";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    /**
     * Detection result for a single video frame.
     */
    public record FrameDetection(
        double timestamp,
        int frameNumber,
        List<Detection> detections
    ) {}
}
