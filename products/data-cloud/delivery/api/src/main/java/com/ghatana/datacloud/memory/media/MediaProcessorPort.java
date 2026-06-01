/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import io.activej.promise.Promise;

import java.util.Map;

/**
 * SPI for media processing adapters (speech-to-text, vision analysis, multimodal).
 *
 * <p>WS3: Defines the contract for media processing backends that can transcribe audio,
 * analyze images/video, and perform multimodal indexing. Implementations of this port
 * are wired through runtime truth surfaces to ensure feature gate compliance.
 *
 * <p>All processing operations are:
 * <ul>
 *   <li>Tenant-scoped via the tenantId parameter</li>
 *   <li>Consent-aware - must check MediaArtifactRecord.hasConsentForProcessing() before processing</li>
 *   <li>Retention-aware - must check MediaArtifactRecord.isRetentionPolicyValid() before processing</li>
 *   <li>Event-emitting - results must be emitted via MediaArtifactEventEmitter</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for media processing adapters (STT, vision, multimodal)
 * @doc.layer product
 * @doc.pattern Port
 */
public interface MediaProcessorPort {

    /**
     * Processes audio transcription for a media artifact.
     *
     * @param artifactId the artifact identifier
     * @param tenantId the tenant scope
     * @param languageCode the language code for transcription (e.g., "en-US")
     * @param parameters additional processing parameters
     * @return promise of the transcript ID, or empty if processing failed
     */
    Promise<String> transcribeAudio(String artifactId, String tenantId, String languageCode, Map<String, String> parameters);

    /**
     * Performs vision analysis on an image or video artifact.
     *
     * @param artifactId the artifact identifier
     * @param tenantId the tenant scope
     * @param analysisType the type of analysis (e.g., "OBJECT_DETECTION", "FACE_RECOGNITION")
     * @param parameters additional processing parameters
     * @return promise of the frame index ID, or empty if processing failed
     */
    Promise<String> analyzeVision(String artifactId, String tenantId, String analysisType, Map<String, String> parameters);

    /**
     * Performs multimodal indexing on a media artifact (combines audio and vision).
     *
     * @param artifactId the artifact identifier
     * @param tenantId the tenant scope
     * @param indexType the type of multimodal index (e.g., "AUDIO_VISUAL", "SCENE_UNDERSTANDING")
     * @param parameters additional processing parameters
     * @return promise of the multimodal index ID, or empty if processing failed
     */
    Promise<String> indexMultimodal(String artifactId, String tenantId, String indexType, Map<String, String> parameters);

    /**
     * Checks if the processor is available for the given operation type.
     *
     * @param operationType the operation type ("TRANSCRIPTION", "VISION", "MULTIMODAL")
     * @return true if the processor is available and configured
     */
    boolean isAvailable(String operationType);

    /**
     * Gets the supported language codes for transcription.
     *
     * @return array of supported language codes (e.g., ["en-US", "es-ES", "fr-FR"])
     */
    String[] getSupportedLanguages();

    /**
     * Gets the supported analysis types for vision processing.
     *
     * @return array of supported analysis types (e.g., ["OBJECT_DETECTION", "FACE_RECOGNITION"])
     */
    String[] getSupportedAnalysisTypes();

    /**
     * Gets the supported index types for multimodal processing.
     *
     * @return array of supported index types (e.g., ["AUDIO_VISUAL", "SCENE_UNDERSTANDING"])
     */
    String[] getSupportedIndexTypes();
}
