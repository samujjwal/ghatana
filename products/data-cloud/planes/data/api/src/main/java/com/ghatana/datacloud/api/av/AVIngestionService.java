/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.api.av;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Service for AV ingestion and processing jobs.
 * 
 * P8.2: Add ingestion and processing job APIs for audio-video content.
 * Handles upload, transcription, frame extraction, and multimodal processing.
 * 
 * @doc.type interface
 * @doc.purpose AV ingestion and processing job management
 * @doc.layer product
 * @doc.pattern Service
 */
public interface AVIngestionService {

    /**
     * Starts an AV ingestion job.
     *
     * @param request the ingestion request
     * @return a Promise that resolves to the ingestion job
     */
    Promise<IngestionJob> startIngestion(IngestionRequest request);

    /**
     * Gets the status of an ingestion job.
     *
     * @param jobId the job ID
     * @return a Promise that resolves to the ingestion job
     */
    Promise<IngestionJob> getJobStatus(String jobId);

    /**
     * Cancels an ingestion job.
     *
     * @param jobId the job ID
     * @return a Promise that resolves when cancellation is complete
     */
    Promise<Void> cancelJob(String jobId);

    /**
     * Lists ingestion jobs for a tenant.
     *
     * @param tenantId the tenant ID
     * @param status optional status filter
     * @return a Promise that resolves to the list of jobs
     */
    Promise<java.util.List<IngestionJob>> listJobs(String tenantId, String status);

    /**
     * Ingestion request.
     *
     * @param tenantId the tenant ID
     * @param sourceUri the source URI
     * @param assetType the asset type
     * @param format the asset format
     * @param processingOptions processing options
     * @param consent consent information
     * @param retention retention policy
     * @param metadata asset metadata
     * @param requestedBy who requested the ingestion
     */
    record IngestionRequest(
            String tenantId,
            String sourceUri,
            AVAsset.AVAssetType assetType,
            AVAsset.AVAssetFormat format,
            ProcessingOptions processingOptions,
            AVAsset.AVConsent consent,
            AVAsset.AVRetention retention,
            AVAsset.AVMetadata metadata,
            String requestedBy) {

        public IngestionRequest(
                String tenantId,
                String sourceUri,
                AVAsset.AVAssetType assetType,
                AVAsset.AVAssetFormat format,
                AVAsset.AVConsent consent,
                AVAsset.AVRetention retention,
                String requestedBy) {
            this(tenantId, sourceUri, assetType, format, new ProcessingOptions(), consent, retention, null, requestedBy);
        }
    }

    /**
     * Processing options for AV ingestion.
     *
     * @param transcribe whether to transcribe audio
     * @param transcriptLanguage language for transcription
     * @param extractFrames whether to extract video frames
     * @param frameRate frame extraction rate
     * @param detectObjects whether to detect objects in video
     * @param detectScenes whether to detect scene changes
     * @param generateEmbeddings whether to generate embeddings
     */
    record ProcessingOptions(
            boolean transcribe,
            String transcriptLanguage,
            boolean extractFrames,
            Double frameRate,
            boolean detectObjects,
            boolean detectScenes,
            boolean generateEmbeddings) {

        public ProcessingOptions() {
            this(true, "en-US", true, 1.0, false, false, true);
        }
    }

    /**
     * Ingestion job status.
     *
     * @param id job ID
     * @param tenantId tenant ID
     * @param assetId the created asset ID (if completed)
     * @param status job status
     * @param currentStep current processing step
     * @param progress progress percentage (0-100)
     * @param error error message (if failed)
     * @param startedAt when the job started
     * @param completedAt when the job completed (if applicable)
     * @param processingResults processing results
     */
    record IngestionJob(
            String id,
            String tenantId,
            String assetId,
            JobStatus status,
            String currentStep,
            int progress,
            String error,
            Instant startedAt,
            Instant completedAt,
            ProcessingResults processingResults) {

        public boolean isRunning() {
            return status == JobStatus.RUNNING || status == JobStatus.PENDING;
        }

        public boolean isCompleted() {
            return status == JobStatus.COMPLETED || status == JobStatus.FAILED || status == JobStatus.CANCELLED;
        }
    }

    /**
     * Job status.
     */
    enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /**
     * Processing results.
     *
     * @param transcript transcript result
     * @param frameIndex frame index result
     * @param embeddings generated embeddings
     * @param objectDetection object detection results
     * @param sceneDetection scene detection results
     */
    record ProcessingResults(
            AVAsset.AVTranscript transcript,
            AVAsset.AVFrameIndex frameIndex,
            Map<String, float[]> embeddings,
            ObjectDetectionResult objectDetection,
            SceneDetectionResult sceneDetection) {}

    /**
     * Object detection result.
     *
     * @param objects detected objects with timestamps
     * @param confidence overall confidence
     */
    record ObjectDetectionResult(
            java.util.List<DetectedObject> objects,
            double confidence) {

        record DetectedObject(
                String label,
                double confidence,
                long startTimeMs,
                long endTimeMs,
                Map<String, Object> boundingBox) {}
    }

    /**
     * Scene detection result.
     *
     * @param scenes detected scenes with timestamps
     * @param sceneCount total number of scenes
     */
    record SceneDetectionResult(
            java.util.List<Scene> scenes,
            int sceneCount) {

        record Scene(
                long startTimeMs,
                long endTimeMs,
                String description,
                Map<String, Object> features) {}
    }
}
