package com.ghatana.audio.video.multimodal.engine;

/**
 * Port for the STT service — decouples the multimodal engine from the gRPC wire format.
 */
public interface SttClientAdapter {

    /**
     * Transcribe raw audio bytes.
     *
     * @param audioData raw PCM/compressed audio
     * @return transcription result including optional timed segments
     */
    AudioResult transcribe(byte[] audioData);
}
