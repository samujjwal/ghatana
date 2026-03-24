package com.ghatana.audio.video.vision.detection;

import org.opencv.core.Mat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Strategy interface for pluggable vision detector implementations.
 *
 * @doc.type interface
 * @doc.purpose Abstraction for interchangeable object detection backends
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface VisionDetector {

    List<Detection> detect(Path imagePath, DetectionConfig config) throws IOException;

    List<Detection> detect(Mat image, DetectionConfig config);

    List<Detection> detectFromBytes(byte[] imageBytes, DetectionConfig config) throws IOException;
}