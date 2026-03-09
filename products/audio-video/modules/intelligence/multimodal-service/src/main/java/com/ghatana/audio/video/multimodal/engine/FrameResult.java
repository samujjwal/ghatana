package com.ghatana.audio.video.multimodal.engine;

import java.util.List;

/**
 * Detection results for a single video frame.
 */
public class FrameResult {

    private final int frameNumber;
    private final long timestampMs;
    private final List<DetectionResult> detections;

    public FrameResult(int frameNumber, long timestampMs, List<DetectionResult> detections) {
        this.frameNumber = frameNumber;
        this.timestampMs = timestampMs;
        this.detections = detections;
    }

    public int getFrameNumber() { return frameNumber; }
    public long getTimestampMs() { return timestampMs; }
    public List<DetectionResult> getDetections() { return detections; }
}
