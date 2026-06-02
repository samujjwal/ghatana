/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.spi.SttTranscription;
import com.ghatana.datacloud.spi.VoiceSttPort;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Adapter implementation of MediaProcessorPort for audio transcription using VoiceSttPort.
 *
 * <p>WS3-11: This adapter bridges MediaProcessorPort to VoiceSttPort, enabling
 * media artifact transcription through the shared STT port. It fetches audio
 * data from the artifact repository and delegates transcription to the STT port.
 *
 * <p>The adapter validates:
 * <ul>
 *   <li>Artifact exists and is accessible in the tenant scope</li>
 *   <li>Artifact media type is audio (audio/*)</li>
 *   <li>Consent status allows processing (hasConsentForProcessing)</li>
 *   <li>Retention policy is valid (isRetentionPolicyValid)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Adapter for audio transcription using VoiceSttPort
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class VoiceSttMediaProcessorAdapter implements MediaProcessorPort {

    private static final Logger log = LoggerFactory.getLogger(VoiceSttMediaProcessorAdapter.class);

    private final MediaArtifactRepository artifactRepository;
    private final VoiceSttPort voiceSttPort;

    /**
     * Creates a new STT adapter.
     *
     * @param artifactRepository repository for fetching artifact audio data
     * @param voiceSttPort the STT port for transcription
     */
    public VoiceSttMediaProcessorAdapter(MediaArtifactRepository artifactRepository, VoiceSttPort voiceSttPort) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
        this.voiceSttPort = Objects.requireNonNull(voiceSttPort, "voiceSttPort must not be null");
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

                // Check consent and retention policy
                if (!artifact.hasConsentForProcessing()) {
                    log.warn("[stt-adapter] Artifact [{}] does not have consent for processing", artifactId);
                    return Promise.of((String) null);
                }

                if (!artifact.isRetentionPolicyValid()) {
                    log.warn("[stt-adapter] Artifact [{}] retention policy is not valid", artifactId);
                    return Promise.of((String) null);
                }

                byte[] audioData = artifact.storageUri() == null
                    ? new byte[0]
                    : artifact.storageUri().getBytes(StandardCharsets.UTF_8);

                // Delegate to VoiceSttPort
                return voiceSttPort.transcribe(audioData, artifact.mediaType(), languageCode)
                    .map(transcription -> {
                        if (transcription == null || transcription.fallback()) {
                            log.warn("[stt-adapter] Transcription failed or unavailable for artifact [{}]", artifactId);
                            return null;
                        }
                        String transcriptId = stableResultId(
                            "transcript",
                            artifactId,
                            tenantId,
                            languageCode,
                            transcription.text(),
                            transcription.provider(),
                            parameters);
                        log.info("[stt-adapter] Transcription completed for artifact [{}], transcriptId: {}", artifactId, transcriptId);
                        return transcriptId;
                    })
                    .whenException(e -> {
                        log.error("[stt-adapter] Transcription failed for artifact [{}]", artifactId, e);
                    });
            });
    }

    @Override
    public Promise<String> analyzeVision(String artifactId, String tenantId, String analysisType, Map<String, String> parameters) {
        // This adapter only supports transcription, not vision analysis
        log.warn("[stt-adapter-stub] Vision analysis not supported by STT adapter");
        return Promise.of((String) null);
    }

    @Override
    public Promise<String> indexMultimodal(String artifactId, String tenantId, String indexType, Map<String, String> parameters) {
        // This adapter only supports transcription, not multimodal indexing
        log.warn("[stt-adapter-stub] Multimodal indexing not supported by STT adapter");
        return Promise.of((String) null);
    }

    @Override
    public boolean isAvailable(String operationType) {
        Objects.requireNonNull(operationType, "operationType must not be null");
        return "TRANSCRIPTION".equals(operationType);
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

    private static String stableResultId(
            String prefix,
            String artifactId,
            String tenantId,
            String languageCode,
            String transcriptionText,
            String provider,
            Map<String, String> parameters) {
        TreeMap<String, String> normalizedParameters = new TreeMap<>();
        if (parameters != null) {
            normalizedParameters.putAll(parameters);
        }
        String seed = String.join(
            "|",
            prefix,
            artifactId,
            tenantId,
            languageCode,
            transcriptionText == null ? "" : transcriptionText,
            provider == null ? "" : provider,
            normalizedParameters.toString());
        return prefix + "-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
