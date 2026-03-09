package com.ghatana.audio.video.multimodal.engine;

/**
 * A single object detection result.
 */
public class DetectionResult {

    private final String className;
    private final double confidence;
    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public DetectionResult(String className, double confidence,
                           double x, double y, double width, double height) {
        this.className = className;
        this.confidence = confidence;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public String getClassName() { return className; }
    public double getConfidence() { return confidence; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
}
