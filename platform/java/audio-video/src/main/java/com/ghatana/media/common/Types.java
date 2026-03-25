/**
 * @doc.type types
 * @doc.purpose Common types shared across all audio-video engines
 * @doc.layer common
 */
package com.ghatana.media.common;

import java.time.Duration;
import java.util.Objects;

/**
 * Represents audio data with associated metadata.
 * This is the canonical audio representation used across all engines.
 */
public record AudioData(
    byte[] data,
    int sampleRate,
    int channels,
    int bitsPerSample,
    Duration duration,
    AudioFormat format
) {
    public AudioData {
        Objects.requireNonNull(data, "data cannot be null");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        if (channels <= 0) throw new IllegalArgumentException("channels must be positive");
        if (bitsPerSample <= 0) throw new IllegalArgumentException("bitsPerSample must be positive");
    }

    /**
     * Get the number of samples per channel.
     */
    public int getSampleCount() {
        return data.length / (channels * (bitsPerSample / 8));
    }

    /**
     * Create a builder for constructing AudioData.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private byte[] data;
        private int sampleRate = 16000;
        private int channels = 1;
        private int bitsPerSample = 16;
        private Duration duration;
        private AudioFormat format = AudioFormat.PCM;

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        public Builder bitsPerSample(int bitsPerSample) {
            this.bitsPerSample = bitsPerSample;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder format(AudioFormat format) {
            this.format = format;
            return this;
        }

        public AudioData build() {
            return new AudioData(data, sampleRate, channels, bitsPerSample, duration, format);
        }
    }
}

/**
 * Audio format types.
 */
public enum AudioFormat {
    PCM,
    WAV,
    MP3,
    FLAC,
    OGG,
    AAC
}

/**
 * Represents image data with associated metadata.
 */
public record ImageData(
    byte[] data,
    int width,
    int height,
    ImageFormat format,
    ColorSpace colorSpace
) {
    public ImageData {
        Objects.requireNonNull(data, "data cannot be null");
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private byte[] data;
        private int width;
        private int height;
        private ImageFormat format = ImageFormat.PNG;
        private ColorSpace colorSpace = ColorSpace.RGB;

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder format(ImageFormat format) {
            this.format = format;
            return this;
        }

        public Builder colorSpace(ColorSpace colorSpace) {
            this.colorSpace = colorSpace;
            return this;
        }

        public ImageData build() {
            return new ImageData(data, width, height, format, colorSpace);
        }
    }
}

/**
 * Image format types.
 */
public enum ImageFormat {
    PNG,
    JPEG,
    WEBP,
    BMP,
    TIFF,
    RAW
}

/**
 * Color space representations.
 */
public enum ColorSpace {
    RGB,
    RGBA,
    BGR,
    BGRA,
    GRAYSCALE,
    YUV,
    HSV
}

/**
 * Bounding box for object detection (shared across vision and multimodal).
 */
public record BoundingBox(
    double x,
    double y,
    double width,
    double height,
    double confidence
) {
    public BoundingBox {
        if (width < 0) throw new IllegalArgumentException("width cannot be negative");
        if (height < 0) throw new IllegalArgumentException("height cannot be negative");
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
    }

    /**
     * Get the center X coordinate.
     */
    public double centerX() {
        return x + width / 2.0;
    }

    /**
     * Get the center Y coordinate.
     */
    public double centerY() {
        return y + height / 2.0;
    }

    /**
     * Get the area of the bounding box.
     */
    public double area() {
        return width * height;
    }

    /**
     * Check if this box intersects with another.
     */
    public boolean intersects(BoundingBox other) {
        return x < other.x + other.width &&
               x + width > other.x &&
               y < other.y + other.height &&
               y + height > other.y;
    }

    /**
     * Calculate Intersection over Union (IoU) with another box.
     */
    public double iou(BoundingBox other) {
        if (!intersects(other)) return 0.0;

        double intersectX = Math.max(x, other.x);
        double intersectY = Math.max(y, other.y);
        double intersectW = Math.min(x + width, other.x + other.width) - intersectX;
        double intersectH = Math.min(y + height, other.y + other.height) - intersectY;

        double intersectArea = intersectW * intersectH;
        double unionArea = area() + other.area() - intersectArea;

        return intersectArea / unionArea;
    }
}

/**
 * Common engine status representation.
 */
public record EngineStatus(
    State state,
    String modelId,
    String version,
    long uptimeMs,
    String errorMessage
) {
    public enum State {
        INITIALIZING,
        READY,
        BUSY,
        DEGRADED,
        ERROR,
        CLOSED
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean isHealthy() {
        return state == State.READY || state == State.BUSY;
    }
}

/**
 * Common metrics for all engines.
 */
public record EngineMetrics(
    long requestCount,
    long errorCount,
    double avgLatencyMs,
    long activeRequests,
    long memoryUsageBytes
) {
    /**
     * Calculate error rate.
     */
    public double errorRate() {
        return requestCount > 0 ? (double) errorCount / requestCount : 0.0;
    }
}

/**
 * Represents a chunk of audio data in a streaming context.
 */
public record AudioChunk(
    byte[] data,
    int sequenceNumber,
    boolean isLast,
    long timestampMs
) {}

/**
 * Represents a processing error with categorization.
 */
public sealed class ProcessingError extends Exception {
    private final ErrorCategory category;
    private final boolean retryable;

    public ProcessingError(String message, ErrorCategory category, boolean retryable) {
        super(message);
        this.category = category;
        this.retryable = retryable;
    }

    public ProcessingError(String message, Throwable cause, ErrorCategory category, boolean retryable) {
        super(message, cause);
        this.category = category;
        this.retryable = retryable;
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public enum ErrorCategory {
        INPUT_VALIDATION,
        MODEL_LOADING,
        INFERENCE,
        RESOURCE_EXHAUSTED,
        NETWORK,
        INTERNAL
    }
}

/**
 * Input validation error.
 */
public final class ValidationError extends ProcessingError {
    public ValidationError(String message) {
        super(message, ErrorCategory.INPUT_VALIDATION, false);
    }
}

/**
 * Model loading error.
 */
public final class ModelLoadingError extends ProcessingError {
    public ModelLoadingError(String message, Throwable cause) {
        super(message, cause, ErrorCategory.MODEL_LOADING, false);
    }
}

/**
 * Inference error that may be retryable.
 */
public final class InferenceError extends ProcessingError {
    public InferenceError(String message, Throwable cause, boolean retryable) {
        super(message, cause, ErrorCategory.INFERENCE, retryable);
    }
}

/**
 * Resource exhausted error.
 */
public final class ResourceExhaustedError extends ProcessingError {
    public ResourceExhaustedError(String message) {
        super(message, ErrorCategory.RESOURCE_EXHAUSTED, true);
    }
}
