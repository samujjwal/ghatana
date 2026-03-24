package com.ghatana.audio.video.vision.detection;

import com.ghatana.audio.video.vision.model.BoundingBox;

/**
 * Immutable detection result returned by a vision detector.
 *
 * @doc.type class
 * @doc.purpose Canonical object detection result for vision detectors
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class Detection {

    private final String className;
    private final float confidence;
    private final BoundingBox bbox;
    private final int classId;

    public Detection(String className, float confidence, BoundingBox bbox, int classId) {
        this.className = className;
        this.confidence = confidence;
        this.bbox = bbox;
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public float getConfidence() {
        return confidence;
    }

    public BoundingBox getBbox() {
        return bbox;
    }

    public int getClassId() {
        return classId;
    }

    @Override
    public String toString() {
        return String.format("Detection{class=%s, conf=%.2f, bbox=%s}", className, confidence, bbox);
    }
}