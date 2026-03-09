package com.ghatana.audio.video.multimodal.engine;

import java.util.List;

/**
 * Aligns a speech segment with the visual detections at the same video timestamp.
 */
public class TemporalAlignment {

    private final long timestampMs;
    private final int frameNumber;
    private final String speechText;
    private final List<DetectionResult> detections;

    public TemporalAlignment(long timestampMs, int frameNumber,
                             String speechText, List<DetectionResult> detections) {
        this.timestampMs = timestampMs;
        this.frameNumber = frameNumber;
        this.speechText = speechText;
        this.detections = detections;
    }

    public long getTimestampMs() { return timestampMs; }
    public int getFrameNumber() { return frameNumber; }
    public String getSpeechText() { return speechText; }
    public List<DetectionResult> getDetections() { return detections; }
}
