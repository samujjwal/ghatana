/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Frame index result for video/image media artifacts.
 *
 * <p>Pass 6 - Audio-video first-class modality: Stores vision analysis output
 * with frame-level labels, objects, scenes, and extracted events.
 *
 * @param frameIndexId    globally unique frame index identifier
 * @param artifactId      associated media artifact ID
 * @param tenantId        tenant scope for isolation
 * @param jobId           processing job that created this frame index
 * @param analysisType    type of vision analysis performed
 * @param frames          analyzed frames with detections
 * @param labels          aggregated labels across all frames
 * @param events          detected events/changes across frames
 * @param confidence      overall confidence score (0.0 - 1.0)
 * @param frameCount      total number of frames analyzed
 * @param durationMs      duration of analyzed video in milliseconds
 * @param metadata        additional metadata (model, processing time, etc.)
 * @param createdAt       frame index creation timestamp
 * @param createdBy       user ID who initiated the analysis
 *
 * @doc.type record
 * @doc.purpose Frame index result storage for Pass 6
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FrameIndex(
        String frameIndexId,
        String artifactId,
        String tenantId,
        String jobId,
        AnalysisType analysisType,
        List<Frame> frames,
        List<Label> labels,
        List<Event> events,
        double confidence,
        int frameCount,
        long durationMs,
        Map<String, String> metadata,
        Instant createdAt,
        String createdBy) {

    public FrameIndex {
        Objects.requireNonNull(frameIndexId, "frameIndexId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(analysisType, "analysisType must not be null");
        Objects.requireNonNull(frames, "frames must not be null");
        Objects.requireNonNull(labels, "labels must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (frameIndexId.isBlank()) throw new IllegalArgumentException("frameIndexId must not be blank");
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        if (frameCount < 0) throw new IllegalArgumentException("frameCount must not be negative");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must not be negative");

        frames = List.copyOf(frames);
        labels = List.copyOf(labels);
        events = events != null ? List.copyOf(events) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Types of vision analysis.
     */
    public enum AnalysisType {
        OBJECT_DETECTION,
        SCENE_RECOGNITION,
        FACIAL_RECOGNITION,
        TEXT_EXTRACTION,
        MOTION_DETECTION,
        COMPREHENSIVE
    }

    /**
     * Creates a new frame index.
     *
     * @param artifactId     associated artifact ID
     * @param tenantId       tenant scope
     * @param jobId          processing job ID
     * @param analysisType   type of analysis
     * @param frames         analyzed frames
     * @param labels         aggregated labels
     * @param confidence     overall confidence
     * @param frameCount     number of frames
     * @param durationMs     duration in milliseconds
     * @param createdBy      user ID who initiated the analysis
     * @return a new FrameIndex with generated ID
     */
    public static FrameIndex create(
            String artifactId,
            String tenantId,
            String jobId,
            AnalysisType analysisType,
            List<Frame> frames,
            List<Label> labels,
            double confidence,
            int frameCount,
            long durationMs,
            String createdBy) {
        return new FrameIndex(
                UUID.randomUUID().toString(),
                artifactId,
                tenantId,
                jobId,
                analysisType,
                frames,
                labels,
                List.of(),
                confidence,
                frameCount,
                durationMs,
                Map.of(),
                Instant.now(),
                createdBy);
    }

    /**
     * A single analyzed frame.
     *
     * @param timestampMs frame timestamp in milliseconds
     * @param detections  detected objects/labels in this frame
     */
    public record Frame(
            long timestampMs,
            List<Detection> detections) {

        public Frame {
            if (timestampMs < 0) throw new IllegalArgumentException("timestampMs must not be negative");
            detections = detections != null ? List.copyOf(detections) : List.of();
        }
    }

    /**
     * A detection within a frame.
     *
     * @param label      detected label/class
     * @param confidence detection confidence (0.0 - 1.0)
     * @param bbox       bounding box coordinates [x, y, width, height]
     */
    public record Detection(
            String label,
            double confidence,
            List<Double> bbox) {

        public Detection {
            Objects.requireNonNull(label, "label must not be null");
            if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
            bbox = bbox != null ? List.copyOf(bbox) : List.of();
        }
    }

    /**
     * An aggregated label across frames.
     *
     * @param label          label/class name
     * @param occurrenceCount how many frames contain this label
     * @param avgConfidence  average confidence across occurrences
     * @param timestamps     timestamps where this label appears
     */
    public record Label(
            String label,
            int occurrenceCount,
            double avgConfidence,
            List<Long> timestamps) {

        public Label {
            Objects.requireNonNull(label, "label must not be null");
            if (occurrenceCount < 0) throw new IllegalArgumentException("occurrenceCount must not be negative");
            if (avgConfidence < 0.0 || avgConfidence > 1.0) throw new IllegalArgumentException("avgConfidence must be between 0.0 and 1.0");
            timestamps = timestamps != null ? List.copyOf(timestamps) : List.of();
        }
    }

    /**
     * A detected event/change across frames.
     *
     * @param eventType   type of event
     * @param startMs     event start timestamp
     * @param endMs       event end timestamp
     * @param description event description
     * @param confidence  event detection confidence
     */
    public record Event(
            String eventType,
            long startMs,
            long endMs,
            String description,
            double confidence) {

        public Event {
            Objects.requireNonNull(eventType, "eventType must not be null");
            if (startMs < 0) throw new IllegalArgumentException("startMs must not be negative");
            if (endMs < startMs) throw new IllegalArgumentException("endMs must not be before startMs");
            if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
