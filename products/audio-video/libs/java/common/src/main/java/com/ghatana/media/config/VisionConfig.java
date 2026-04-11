package com.ghatana.media.config;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Vision engine configuration.
 *
 * @doc.type record
 * @doc.purpose Configuration for embedded vision engine instances
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public record VisionConfig(
    Path modelPath,
    String modelId,
    String modelType,
    boolean useGpu,
    int maxConcurrentRequests,
    Duration timeout,
    int batchSize,
    double defaultConfidenceThreshold,
    int defaultMaxDetections,
    int inputSize,
    boolean enableTracking,
    boolean enableSegmentation,
    boolean enableClassification,
    long maxMemoryBytes
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path modelPath;
        private String modelId = "yolov8n";
        private String modelType = "yolo";
        private boolean useGpu = false;
        private int maxConcurrentRequests = 10;
        private Duration timeout = Duration.ofSeconds(10);
        private int batchSize = 1;
        private double defaultConfidenceThreshold = 0.5;
        private int defaultMaxDetections = 100;
        private int inputSize = 640;
        private boolean enableTracking = false;
        private boolean enableSegmentation = false;
        private boolean enableClassification = true;
        private long maxMemoryBytes = 512L * 1024 * 1024;

        public Builder modelPath(Path value) { this.modelPath = value; return this; }
        public Builder modelId(String value) { this.modelId = value; return this; }
        public Builder modelType(String value) { this.modelType = value; return this; }
        public Builder useGpu(boolean value) { this.useGpu = value; return this; }
        public Builder maxConcurrentRequests(int value) { this.maxConcurrentRequests = value; return this; }
        public Builder timeout(Duration value) { this.timeout = value; return this; }
        public Builder batchSize(int value) { this.batchSize = value; return this; }
        public Builder defaultConfidenceThreshold(double value) { this.defaultConfidenceThreshold = value; return this; }
        public Builder defaultMaxDetections(int value) { this.defaultMaxDetections = value; return this; }
        public Builder inputSize(int value) { this.inputSize = value; return this; }
        public Builder enableTracking(boolean value) { this.enableTracking = value; return this; }
        public Builder enableSegmentation(boolean value) { this.enableSegmentation = value; return this; }
        public Builder enableClassification(boolean value) { this.enableClassification = value; return this; }
        public Builder maxMemoryBytes(long value) { this.maxMemoryBytes = value; return this; }

        public VisionConfig build() {
            return new VisionConfig(
                modelPath,
                modelId,
                modelType,
                useGpu,
                maxConcurrentRequests,
                timeout,
                batchSize,
                defaultConfidenceThreshold,
                defaultMaxDetections,
                inputSize,
                enableTracking,
                enableSegmentation,
                enableClassification,
                maxMemoryBytes
            );
        }
    }
}
