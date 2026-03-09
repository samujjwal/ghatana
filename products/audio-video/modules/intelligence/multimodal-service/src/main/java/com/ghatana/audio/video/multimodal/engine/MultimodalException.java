package com.ghatana.audio.video.multimodal.engine;

/**
 * Unchecked exception for multimodal analysis failures.
 */
public class MultimodalException extends RuntimeException {

    public MultimodalException(String message) {
        super(message);
    }

    public MultimodalException(String message, Throwable cause) {
        super(message, cause);
    }
}
