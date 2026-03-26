package com.ghatana.audio.video.vision.detection;

import com.ghatana.audio.video.vision.model.DetectedObject;
import com.ghatana.audio.video.vision.model.DetectionOptions;

import java.util.List;

/**
 * Strategy interface for object detection backends.
 *
 * <p>Implementations include {@link com.ghatana.audio.video.vision.yolo.YoloV8Adapter}
 * for production use and simple stubs for unit testing.
 *
 * @doc.type interface
 * @doc.purpose Detection backend abstraction for testability and engine swap
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface VisionDetector {

    /**
     * Detect objects in the supplied raw image bytes.
     *
     * @param imageData  Raw image bytes (JPEG, PNG, etc.)
     * @param options    Detection configuration.
     * @return Ordered list of detected objects, highest confidence first.
     * @throws com.ghatana.audio.video.vision.detection.VisionDetector.DetectionException on unrecoverable failure.
     */
    List<DetectedObject> detectObjects(byte[] imageData, DetectionOptions options);

    /**
     * Whether this detector has been successfully initialised and is ready.
     */
    boolean isInitialized();

    /**
     * Checked exception thrown by detector implementations.
     */
    class DetectionException extends RuntimeException {
        public DetectionException(String message, Throwable cause) {
            super(message, cause);
        }
        public DetectionException(String message) {
            super(message);
        }
    }
}
