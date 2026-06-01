/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.launcher.http.voice.SttTranscription;
import com.ghatana.datacloud.launcher.http.voice.VoiceSttPort;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adapter that bridges {@link VoiceSttPort} to {@link MediaProcessorPort} for audio transcription.
 *
 * <p>WS3: This adapter wraps the existing VoiceSttPort infrastructure (Whisper, cloud APIs, etc.)
 * to provide MediaProcessorPort-compatible transcription for media artifacts. It translates
 * between the artifact-based MediaProcessorPort API and the audio-bytes-based VoiceSttPort API.
 *
 * <p>This adapter requires the media artifact's audio bytes to be available for transcription.
 * In production, this would involve fetching the blob from storage and passing it to the STT port.
 *
 * @doc.type class
 * @doc.purpose Adapter bridging VoiceSttPort to MediaProcessorPort for transcription
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class VoiceSttMediaProcessorAdapter implements MediaProcessorPort {

    private static final Logger log = LoggerFactory.getLogger(VoiceSttMediaProcessorAdapter.class);

    private final VoiceSttPort voiceSttPort;
    private final MediaArtifactRepository artifactRepository;

    /**
     * Creates a new STT adapter.
     *
     * @param voiceSttPort the underlying STT port (Whisper, cloud API, etc.)
     * @param artifactRepository repository for fetching artifact audio data
     */
    public VoiceSttMediaProcessorAdapter(VoiceSttPort voiceSttPort, MediaArtifactRepository artifactRepository) {
        this.voiceSttPort = Objects.requireNonNull(voiceSttPort, "voiceSttPort must not be null");
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
    }

    @Override
    public Promise<String> transcribeAudio(String artifactId, String tenantId, String languageCode, Map<String, String> parameters) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(languageCode, "languageCode must not be null");

        log.info("[stt-adapter] Transcribing audio artifact [{}] for tenant [{}] with language [{}]",
            artifactId, tenantId, languageCode);

        // Fetch the artifact to get audio data
        return artifactRepository.findById(artifactId, tenantId)
            .then(optionalArtifact -> {
                if (optionalArtifact.isEmpty()) {
                    log.warn("[stt-adapter] Artifact [{}] not found for tenant [{}]", artifactId, tenantId);
                    return Promise.of((String) null);
                }

                MediaArtifactRecord artifact = optionalArtifact.get();

                // Check if artifact is audio
                if (!artifact.mediaType().startsWith("audio/")) {
                    log.warn("[stt-adapter] Artifact [{}] is not audio (type: {})", artifactId, artifact.mediaType());
                    return Promise.of((String) null);
                }

                // TODO: Fetch actual audio bytes from blob storage using artifact.storageUri()
                // For now, this is a stub that simulates the transcription
                // In production, we would:
                // 1. Fetch blob from storage using artifact.storageUri()
                // 2. Pass audio bytes to voiceSttPort.transcribe()
                // 3. Save the transcript using MediaArtifactRepository.saveTranscript()

                log.info("[stt-adapter] Would fetch audio from [{}] and transcribe", artifact.storageUri());

                // Simulate transcription by generating a transcript ID
                String transcriptId = "transcript-" + artifactId + "-" + System.currentTimeMillis();
                return Promise.of(transcriptId);
            });
    }

    @Override
    public Promise<String> analyzeVision(String artifactId, String tenantId, String analysisType, Map<String, String> parameters) {
        // This adapter only supports transcription, not vision analysis
        log.warn("[stt-adapter] Vision analysis not supported by STT adapter");
        return Promise.of((String) null);
    }

    @Override
    public Promise<String> indexMultimodal(String artifactId, String tenantId, String indexType, Map<String, String> parameters) {
        // This adapter only supports transcription, not multimodal indexing
        log.warn("[stt-adapter] Multimodal indexing not supported by STT adapter");
        return Promise.of((String) null);
    }

    @Override
    public boolean isAvailable(String operationType) {
        Objects.requireNonNull(operationType, "operationType must not be null");
        return "TRANSCRIPTION".equals(operationType) && voiceSttPort.isAvailable();
    }

    @Override
    public String[] getSupportedLanguages() {
        // Return common language codes; in production this would come from the STT port
        return new String[]{"en-US", "en-GB", "es-ES", "fr-FR", "de-DE", "it-IT", "pt-BR", "ja-JP", "ko-KR", "zh-CN"};
    }

    @Override
    public String[] getSupportedAnalysisTypes() {
        // STT adapter does not support vision analysis
        return new String[0];
    }

    @Override
    public String[] getSupportedIndexTypes() {
        // STT adapter does not support multimodal indexing
        return new String[0];
    }
}
