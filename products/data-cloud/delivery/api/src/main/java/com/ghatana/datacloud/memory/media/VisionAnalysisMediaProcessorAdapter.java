/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Adapter for vision analysis (image/video processing) as a MediaProcessorPort implementation.
 *
 * <p>WS3: This adapter provides vision analysis capabilities for media artifacts including
 * object detection, face recognition, scene classification, and text detection. It integrates
 * with vision analysis backends (cloud APIs, local models, etc.) to process image and video artifacts.
 *
 * <p>This is a stub implementation that simulates vision analysis. In production, this would
 * integrate with actual vision analysis services like AWS Rekognition, Google Vision API, or
 * local models like YOLO, OpenCV, etc.
 *
 * @doc.type class
 * @doc.purpose Vision analysis adapter for MediaProcessorPort
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class VisionAnalysisMediaProcessorAdapter implements MediaProcessorPort {

    private static final Logger log = LoggerFactory.getLogger(VisionAnalysisMediaProcessorAdapter.class);

    private static final Set<String> SUPPORTED_ANALYSIS_TYPES = Set.of(
        "OBJECT_DETECTION",
        "FACE_RECOGNITION",
        "SCENE_CLASSIFICATION",
        "TEXT_DETECTION",
        "LABEL_DETECTION",
        "LANDMARK_DETECTION"
    );

    private final MediaArtifactRepository artifactRepository;

    /**
     * Creates a new vision analysis adapter.
     *
     * @param artifactRepository repository for fetching artifact data
     */
    public VisionAnalysisMediaProcessorAdapter(MediaArtifactRepository artifactRepository) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
    }

    @Override
    public Promise<String> transcribeAudio(String artifactId, String tenantId, String languageCode, Map<String, String> parameters) {
        // Vision adapter does not support audio transcription
        log.warn("[vision-adapter] Audio transcription not supported by vision adapter");
        return Promise.of((String) null);
    }

    @Override
    public Promise<String> analyzeVision(String artifactId, String tenantId, String analysisType, Map<String, String> parameters) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(analysisType, "analysisType must not be null");

        log.info("[vision-adapter] Analyzing vision artifact [{}] for tenant [{}] with type [{}]",
            artifactId, tenantId, analysisType);

        // Fetch the artifact to verify it's an image or video
        return artifactRepository.findById(artifactId, tenantId)
            .then(optionalArtifact -> {
                if (optionalArtifact.isEmpty()) {
                    log.warn("[vision-adapter] Artifact [{}] not found for tenant [{}]", artifactId, tenantId);
                    return Promise.of((String) null);
                }

                MediaArtifactRecord artifact = optionalArtifact.get();

                // Check if artifact is an image or video
                if (!artifact.mediaType().startsWith("image/") && !artifact.mediaType().startsWith("video/")) {
                    log.warn("[vision-adapter] Artifact [{}] is not image/video (type: {})", artifactId, artifact.mediaType());
                    return Promise.of((String) null);
                }

                // Check consent and retention policy
                if (!artifact.hasConsentForProcessing()) {
                    log.warn("[vision-adapter] Artifact [{}] does not have consent for processing", artifactId);
                    return Promise.of((String) null);
                }

                if (!artifact.isRetentionPolicyValid()) {
                    log.warn("[vision-adapter] Artifact [{}] retention policy is not valid", artifactId);
                    return Promise.of((String) null);
                }

                log.info("[vision-adapter] Would fetch image/video from [{}] and analyze with [{}]",
                    artifact.storageUri(), analysisType);

                String frameIndexId = stableResultId(
                    "frame-index",
                    artifactId,
                    tenantId,
                    analysisType,
                    artifact.storageUri());
                return Promise.of(frameIndexId);
            });
    }

    @Override
    public Promise<String> indexMultimodal(String artifactId, String tenantId, String indexType, Map<String, String> parameters) {
        // Vision adapter primarily handles single-modality vision analysis
        // Multimodal indexing would be handled by a separate multimodal adapter
        log.warn("[vision-adapter] Multimodal indexing not supported by vision adapter");
        return Promise.of((String) null);
    }

    @Override
    public boolean isAvailable(String operationType) {
        Objects.requireNonNull(operationType, "operationType must not be null");
        return "VISION".equals(operationType);
    }

    @Override
    public String[] getSupportedLanguages() {
        // Vision adapter does not support transcription languages
        return new String[0];
    }

    @Override
    public String[] getSupportedAnalysisTypes() {
        return SUPPORTED_ANALYSIS_TYPES.toArray(new String[0]);
    }

    @Override
    public String[] getSupportedIndexTypes() {
        // Vision adapter does not support multimodal indexing
        return new String[0];
    }

    private static String stableResultId(
            String prefix,
            String artifactId,
            String tenantId,
            String operationType,
            String storageUri) {
        String seed = String.join(
            "|",
            prefix,
            artifactId,
            tenantId,
            operationType,
            storageUri == null ? "" : storageUri);
        return prefix + "-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
