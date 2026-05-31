/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.datacloud;

import java.util.List;
import java.util.Map;

/**
 * Pass 6: Service Provider Interface for Data Cloud media lifecycle integration.
 *
 * <p>Exposes provider interface consumed by Data Cloud:
 * <ul>
 *   <li>Submit media processing job</li>
 *   <li>Get job status</li>
 *   <li>Fetch transcript</li>
 *   <li>Fetch frame index</li>
 *   <li>Fetch extracted events</li>
 *   <li>Fetch embeddings/index metadata</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose SPI for Data Cloud media processing integration
 * @doc.layer product
 * @doc.pattern Service Provider Interface
 */
public interface MediaProcessingProvider {

    /**
     * Submit a media processing job for a Data Cloud artifact.
     *
     * @param artifactId Data Cloud media artifact ID
     * @param tenantId Tenant identifier
     * @param mediaType Media type (audio/wav, video/mp4, image/jpeg, etc.)
     * @param mediaData Raw media bytes
     * @param jobType Type of processing
     * @param parameters Job-specific parameters
     * @return Job ID for tracking
     */
    String submitJob(
            String artifactId,
            String tenantId,
            String mediaType,
            byte[] mediaData,
            JobType jobType,
            Map<String, String> parameters);

    /**
     * Get current status of a media processing job.
     *
     * @param jobId Job identifier
     * @param tenantId Tenant identifier
     * @return Job status or null if not found
     */
    MediaProcessingJob getJobStatus(String jobId, String tenantId);

    /**
     * Fetch transcript result for an audio artifact.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return Transcript result or null if not available
     */
    TranscriptResult fetchTranscript(String artifactId, String tenantId);

    /**
     * Fetch frame index result for an image/video artifact.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return Frame index result or null if not available
     */
    FrameIndexResult fetchFrameIndex(String artifactId, String tenantId);

    /**
     * Fetch extracted events from media analysis.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return List of extracted events
     */
    List<ExtractedEvent> fetchExtractedEvents(String artifactId, String tenantId);

    /**
     * Fetch embeddings and index metadata for the processed media.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return Embedding metadata or null if not available
     */
    EmbeddingMetadata fetchEmbeddings(String artifactId, String tenantId);

    /**
     * List all jobs for a given artifact.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return List of jobs for the artifact
     */
    List<MediaProcessingJob> listJobsForArtifact(String artifactId, String tenantId);
}
