/**
 * @doc.type interface
 * @doc.purpose Computer Vision Engine API for embedded library usage
 * @doc.layer api
 */
package com.ghatana.media.vision.api;

import com.ghatana.media.common.*;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Computer Vision Engine interface for object detection and image analysis.
 *
 * <p>Usage:
 * <pre>{@code
 * try (VisionEngine engine = library.getVisionEngine()) {
 *     // Object detection
 *     DetectionResult result = engine.detect(imageData);
 *
 *     // Detection with options
 *     DetectionResult result = engine.detect(imageData, DetectionOptions.builder()
 *         .confidenceThreshold(0.7)
 *         .maxDetections(10)
 *         .build());
 *
 *     // Streaming detection for video
 *     engine.detectStreaming(videoStream, options, result -> {
 *         drawBoundingBoxes(result.objects());
 *     });
 * }
 * }</pre>
 */
public interface VisionEngine extends AutoCloseable {

    // ====================================================================================
    // Object Detection
    // ====================================================================================

    /**
     * Detect objects in an image.
     *
     * @param image image data
     * @return detection result
     * @throws ValidationError if image format is invalid
     * @throws InferenceError if detection fails
     */
    default DetectionResult detect(ImageData image) {
        return detect(image, DetectionOptions.defaults());
    }

    /**
     * Detect objects with options.
     *
     * @param image image data
     * @param options detection options
     * @return detection result
     */
    DetectionResult detect(ImageData image, DetectionOptions options);

    /**
     * Detect objects asynchronously.
     *
     * @param image image data
     * @param options detection options
     * @return promise of detection result
     */
    default Promise<DetectionResult> detectAsync(ImageData image, DetectionOptions options) {
        try {
            return Promise.of(detect(image, options));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Detect asynchronously with default options.
     */
    default Promise<DetectionResult> detectAsync(ImageData image) {
        return detectAsync(image, DetectionOptions.defaults());
    }

    // ====================================================================================
    // Streaming Detection (for video)
    // ====================================================================================

    /**
     * Create a streaming detection session for video.
     *
     * @param options detection options
     * @param resultCallback callback for detection results
     * @return streaming session
     */
    StreamingDetectionSession createStreamingSession(DetectionOptions options, Consumer<DetectionResult> resultCallback);

    /**
     * Create streaming session with default options.
     */
    default StreamingDetectionSession createStreamingSession(Consumer<DetectionResult> resultCallback) {
        return createStreamingSession(DetectionOptions.defaults(), resultCallback);
    }

    // ====================================================================================
    // Image Analysis
    // ====================================================================================

    /**
     * Analyze image and generate caption.
     *
     * @param image image data
     * @return generated caption
     */
    String caption(ImageData image);

    /**
     * Analyze image asynchronously.
     */
    default Promise<String> captionAsync(ImageData image) {
        try {
            return Promise.of(caption(image));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Classify image into categories.
     *
     * @param image image data
     * @param topK number of top categories to return
     * @return list of classifications
     */
    List<Classification> classify(ImageData image, int topK);

    /**
     * Classify with default top-5.
     */
    default List<Classification> classify(ImageData image) {
        return classify(image, 5);
    }

    // ====================================================================================
    // Model Management
    // ====================================================================================

    /**
     * Get available detection models.
     */
    List<DetectionModelInfo> getAvailableModels();

    /**
     * Load a specific model.
     *
     * @param modelId model identifier
     * @throws ModelLoadingError if model cannot be loaded
     */
    void loadModel(String modelId);

    /**
     * Get the currently active model.
     */
    DetectionModelInfo getActiveModel();

    /**
     * Warm up the engine.
     */
    void warmup();

    // ====================================================================================
    // Lifecycle
    // ====================================================================================

    @Override
    void close();

    /**
     * Get engine status.
     */
    EngineStatus getStatus();

    /**
     * Get engine metrics.
     */
    EngineMetrics getMetrics();
}

/**
 * Streaming detection session for video processing.
 */
public interface StreamingDetectionSession extends AutoCloseable {

    /**
     * Feed a video frame.
     *
     * @param frame video frame image
     * @param frameNumber frame sequence number
     */
    void feedFrame(ImageData frame, long frameNumber);

    /**
     * Signal end of stream.
     */
    void endStream();

    /**
     * Check if session is active.
     */
    boolean isActive();

    @Override
    void close();
}

/**
 * Detection options.
 */
public record DetectionOptions(
    double confidenceThreshold,
    int maxDetections,
    List<String> classFilter,
    boolean enableTracking,
    int inputSize,
    NonMaxSuppression nms
) {
    public static DetectionOptions defaults() {
        return new DetectionOptions(
            0.5,    // confidence threshold
            100,    // max detections
            null,   // no class filter
            false,  // tracking disabled
            640,    // default input size
            new NonMaxSuppression(0.45, true)
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double confidenceThreshold = 0.5;
        private int maxDetections = 100;
        private List<String> classFilter;
        private boolean enableTracking = false;
        private int inputSize = 640;
        private NonMaxSuppression nms = new NonMaxSuppression(0.45, true);

        public Builder confidenceThreshold(double threshold) {
            this.confidenceThreshold = Math.max(0.0, Math.min(1.0, threshold));
            return this;
        }

        public Builder maxDetections(int max) {
            this.maxDetections = Math.max(1, max);
            return this;
        }

        public Builder classFilter(List<String> classes) {
            this.classFilter = classes;
            return this;
        }

        public Builder enableTracking(boolean enable) {
            this.enableTracking = enable;
            return this;
        }

        public Builder inputSize(int size) {
            this.inputSize = size;
            return this;
        }

        public Builder nms(NonMaxSuppression nms) {
            this.nms = nms;
            return this;
        }

        public DetectionOptions build() {
            return new DetectionOptions(
                confidenceThreshold, maxDetections, classFilter,
                enableTracking, inputSize, nms
            );
        }
    }
}

/**
 * Non-max suppression settings.
 */
public record NonMaxSuppression(
    double iouThreshold,
    boolean enabled
) {}

/**
 * Detection result.
 */
public record DetectionResult(
    List<DetectedObject> objects,
    int imageWidth,
    int imageHeight,
    long processingTimeMs,
    String modelId
) {
    /**
     * Get objects filtered by confidence.
     */
    public List<DetectedObject> getObjectsAboveConfidence(double threshold) {
        return objects.stream()
            .filter(obj -> obj.confidence() >= threshold)
            .toList();
    }

    /**
     * Get count of detected objects.
     */
    public int count() {
        return objects.size();
    }
}

/**
 * Detected object.
 */
public record DetectedObject(
    String className,
    double confidence,
    BoundingBox bbox,
    Integer trackingId,
    List<Keypoint> keypoints
) {
    /**
     * Create detection without tracking.
     */
    public DetectedObject(String className, double confidence, BoundingBox bbox) {
        this(className, confidence, bbox, null, null);
    }

    /**
     * Create detection with keypoints.
     */
    public DetectedObject(String className, double confidence, BoundingBox bbox, List<Keypoint> keypoints) {
        this(className, confidence, bbox, null, keypoints);
    }
}

/**
 * Keypoint for pose detection.
 */
public record Keypoint(
    String name,
    double x,
    double y,
    double confidence
) {}

/**
 * Image classification result.
 */
public record Classification(
    String className,
    double confidence
) {}

/**
 * Detection model information.
 */
public record DetectionModelInfo(
    String modelId,
    String name,
    String version,
    String[] supportedClasses,
    long sizeBytes,
    boolean supportsGpu,
    int inputWidth,
    int inputHeight,
    Optional<String> description
) {}
