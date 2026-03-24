package com.ghatana.audio.video.vision.detection;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for object detection requests.
 *
 * @doc.type class
 * @doc.purpose Shared detector configuration across vision implementations
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class DetectionConfig {

    private final float confidenceThreshold;
    private final float nmsThreshold;
    private final int maxDetections;
    private final List<String> targetClasses;

    private DetectionConfig(Builder builder) {
        this.confidenceThreshold = builder.confidenceThreshold;
        this.nmsThreshold = builder.nmsThreshold;
        this.maxDetections = builder.maxDetections;
        this.targetClasses = List.copyOf(builder.targetClasses);
    }

    public static Builder builder() {
        return new Builder();
    }

    public float getConfidenceThreshold() {
        return confidenceThreshold;
    }

    public float getNmsThreshold() {
        return nmsThreshold;
    }

    public int getMaxDetections() {
        return maxDetections;
    }

    public List<String> getTargetClasses() {
        return targetClasses;
    }

    public static final class Builder {
        private float confidenceThreshold = 0.5f;
        private float nmsThreshold = 0.4f;
        private int maxDetections = 100;
        private List<String> targetClasses = new ArrayList<>();

        public Builder confidenceThreshold(float threshold) {
            this.confidenceThreshold = threshold;
            return this;
        }

        public Builder nmsThreshold(float threshold) {
            this.nmsThreshold = threshold;
            return this;
        }

        public Builder maxDetections(int max) {
            this.maxDetections = max;
            return this;
        }

        public Builder targetClasses(List<String> classes) {
            this.targetClasses = new ArrayList<>(classes);
            return this;
        }

        public DetectionConfig build() {
            return new DetectionConfig(this);
        }
    }
}