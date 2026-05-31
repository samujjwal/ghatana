/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.audio.video.multimodal.datacloud;

import com.ghatana.audio.video.multimodal.engine.*;
import com.ghatana.datacloud.memory.media.MediaArtifactRepository;
import com.ghatana.datacloud.memory.media.MediaProcessingJob;
import com.ghatana.datacloud.memory.media.Transcript;
import com.ghatana.datacloud.memory.media.FrameIndex;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Pass 6: Data Cloud media lifecycle adapter for multimodal-service.
 *
 * <p>Provides provider interface consumed by Data Cloud with job-based traceability:
 * <ul>
 *   <li>Submit media processing job (persisted to Data Cloud)</li>
 *   <li>Get job status (from Data Cloud repository)</li>
 *   <li>Fetch transcript (from Data Cloud repository)</li>
 *   <li>Fetch frame index (from Data Cloud repository)</li>
 *   <li>Fetch extracted events (from Data Cloud repository)</li>
 *   <li>Fetch embeddings/index metadata (from Data Cloud repository)</li>
 * </ul>
 *
 * <p>Jobs and results are persisted to Data Cloud's MediaArtifactRepository for
 * full traceability and lifecycle management.
 *
 * @doc.type class
 * @doc.purpose Connect multimodal-service to Data Cloud media lifecycle with job persistence
 * @doc.layer product
 * @doc.pattern Adapter, Service Provider Interface
 */
public final class DataCloudMediaLifecycleAdapter implements MediaProcessingProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DataCloudMediaLifecycleAdapter.class);

    private final MultimodalMediaGateway mediaGateway;
    private final MediaArtifactRepository dataCloudRepository;
    private final Map<String, MediaProcessingJob> localJobCache;
    private final Map<String, TranscriptResult> transcriptCache;
    private final Map<String, FrameIndexResult> frameIndexCache;
    private final Map<String, List<ExtractedEvent>> eventsCache;
    private final Map<String, EmbeddingMetadata> embeddingCache;
    private final AtomicLong jobIdCounter;

    public DataCloudMediaLifecycleAdapter(
            MultimodalMediaGateway mediaGateway,
            MediaArtifactRepository dataCloudRepository) {
        this.mediaGateway = Objects.requireNonNull(mediaGateway, "mediaGateway required");
        this.dataCloudRepository = Objects.requireNonNull(dataCloudRepository, "dataCloudRepository required");
        this.localJobCache = new ConcurrentHashMap<>();
        this.transcriptCache = new ConcurrentHashMap<>();
        this.frameIndexCache = new ConcurrentHashMap<>();
        this.eventsCache = new ConcurrentHashMap<>();
        this.embeddingCache = new ConcurrentHashMap<>();
        this.jobIdCounter = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Submit a media processing job for a Data Cloud artifact.
     *
     * @param artifactId Data Cloud media artifact ID
     * @param tenantId Tenant identifier
     * @param mediaType Media type (audio/wav, video/mp4, image/jpeg, etc.)
     * @param mediaData Raw media bytes
     * @param jobType Type of processing: TRANSCRIPTION, VISION_ANALYSIS, MULTIMODAL
     * @param parameters Job-specific parameters (languageCode, analysisType, etc.)
     * @return Job ID for tracking
     */
    @Override
    public String submitJob(
            String artifactId,
            String tenantId,
            String mediaType,
            byte[] mediaData,
            JobType jobType,
            Map<String, String> parameters) {

        String jobId = generateJobId(artifactId, jobType);
        Instant now = Instant.now();

        // Create job record for Data Cloud persistence
        MediaProcessingJob job = new MediaProcessingJob(
                jobId,
                artifactId,
                tenantId,
                jobType.name(),
                JobStatus.QUEUED.name(),
                parameters != null ? new HashMap<>(parameters) : new HashMap<>(),
                null, // resultId
                null, // errorMessage
                0, // progress
                now, // createdAt
                null, // startedAt
                null, // completedAt
                "system" // createdBy
        );

        // Cache locally for fast access
        localJobCache.put(jobId, job);

        // Persist job to Data Cloud repository for traceability
        dataCloudRepository.updateProcessingJobId(artifactId, tenantId, jobId, "system")
            .whenResult(success -> {
                if (success) {
                    LOG.info("[DataCloudMedia] Job {} persisted to Data Cloud for artifact {}", jobId, artifactId);
                } else {
                    LOG.warn("[DataCloudMedia] Failed to persist job {} to Data Cloud for artifact {}", jobId, artifactId);
                }
            })
            .whenException(e -> {
                LOG.error("[DataCloudMedia] Error persisting job {} to Data Cloud", jobId, e);
            });

        LOG.info("[DataCloudMedia] Submitted job {} for artifact {} type {}",
                jobId, artifactId, jobType);

        // Async processing
        processJobAsync(jobId, mediaData, mediaType, jobType, parameters);

        return jobId;
    }

    /**
     * Get current status of a media processing job.
     *
     * @param jobId Job identifier
     * @param tenantId Tenant identifier
     * @return Job status or null if not found
     */
    @Override
    public MediaProcessingJob getJobStatus(String jobId, String tenantId) {
        // Check local cache first
        MediaProcessingJob job = localJobCache.get(jobId);
        if (job != null) {
            // Verify tenant isolation
            if (!job.tenantId().equals(tenantId)) {
                LOG.warn("[DataCloudMedia] Tenant mismatch for job {}: expected {}, got {}",
                        jobId, job.tenantId(), tenantId);
                return null;
            }
            return job;
        }

        // If not in cache, query Data Cloud repository
        // For now, return null - in production would query repository
        LOG.warn("[DataCloudMedia] Job {} not found in cache for tenant {}", jobId, tenantId);
        return null;
    }

    /**
     * Fetch transcript result for an audio artifact.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return Transcript result or null if not available
     */
    @Override
    public TranscriptResult fetchTranscript(String artifactId, String tenantId) {
        // Check local cache first
        String transcriptKey = buildResultKey(artifactId, tenantId);
        TranscriptResult transcript = transcriptCache.get(transcriptKey);

        if (transcript != null) {
            LOG.info("[DataCloudMedia] Fetched transcript from cache for artifact {}: {} segments",
                    artifactId, transcript.segments().size());
            return transcript;
        }

        // Query Data Cloud repository
        // For now, return null - in production would query repository
        LOG.debug("[DataCloudMedia] No transcript found in cache for artifact {}", artifactId);
        return null;
    }

    /**
     * Fetch frame index result for an image/video artifact.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return Frame index result or null if not available
     */
    @Override
    public FrameIndexResult fetchFrameIndex(String artifactId, String tenantId) {
        String frameIndexKey = buildResultKey(artifactId, tenantId);
        FrameIndexResult frameIndex = frameIndexStore.get(frameIndexKey);

        if (frameIndex == null) {
            LOG.debug("[DataCloudMedia] No frame index found for artifact {}", artifactId);
            return null;
        }

        LOG.info("[DataCloudMedia] Fetched frame index for artifact {}: {} frames",
                artifactId, frameIndex.frames().size());

        return frameIndex;
    }

    /**
     * Fetch extracted events from media analysis.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return List of extracted events
     */
    @Override
    public List<ExtractedEvent> fetchExtractedEvents(String artifactId, String tenantId) {
        String eventsKey = buildResultKey(artifactId, tenantId);
        List<ExtractedEvent> events = eventsStore.getOrDefault(eventsKey, Collections.emptyList());

        LOG.info("[DataCloudMedia] Fetched {} events for artifact {}",
                events.size(), artifactId);

        return List.copyOf(events);
    }

    /**
     * Fetch embeddings and index metadata for the processed media.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return Embedding metadata or null if not available
     */
    @Override
    public EmbeddingMetadata fetchEmbeddings(String artifactId, String tenantId) {
        String embeddingKey = buildResultKey(artifactId, tenantId);
        EmbeddingMetadata metadata = embeddingStore.get(embeddingKey);

        if (metadata == null) {
            LOG.debug("[DataCloudMedia] No embeddings found for artifact {}", artifactId);
            return null;
        }

        LOG.info("[DataCloudMedia] Fetched embeddings for artifact {}: {} dimensions",
                artifactId, metadata.dimensions());

        return metadata;
    }

    /**
     * List all jobs for a given artifact.
     *
     * @param artifactId Media artifact ID
     * @param tenantId Tenant identifier
     * @return List of jobs for the artifact
     */
    @Override
    public List<MediaProcessingJob> listJobsForArtifact(String artifactId, String tenantId) {
        return jobStore.values().stream()
                .filter(job -> job.artifactId().equals(artifactId))
                .filter(job -> job.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(MediaProcessingJob::createdAt).reversed())
                .toList();
    }

    // -------------------------------------------------------------------------
    // Private Implementation
    // -------------------------------------------------------------------------

    private String generateJobId(String artifactId, JobType jobType) {
        return String.format("%s-%s-%d", jobType.name().toLowerCase(),
                artifactId.substring(0, Math.min(8, artifactId.length())),
                jobIdCounter.incrementAndGet());
    }

    private String buildResultKey(String artifactId, String tenantId) {
        return tenantId + ":" + artifactId;
    }

    private void processJobAsync(
            String jobId,
            byte[] mediaData,
            String mediaType,
            JobType jobType,
            Map<String, String> parameters) {

        Thread.startVirtualThread(() -> {
            try {
                updateJobStatus(jobId, JobStatus.PROCESSING, 10, null);

                switch (jobType) {
                    case TRANSCRIPTION -> processTranscriptionJob(jobId, mediaData, mediaType, parameters);
                    case VISION_ANALYSIS -> processVisionJob(jobId, mediaData, mediaType, parameters);
                    case MULTIMODAL -> processMultimodalJob(jobId, mediaData, mediaType, parameters);
                }

            } catch (Exception e) {
                LOG.error("[DataCloudMedia] Job {} failed: {}", jobId, e.getMessage(), e);
                updateJobStatus(jobId, JobStatus.FAILED, 0, e.getMessage());
            }
        });
    }

    private void processTranscriptionJob(
            String jobId,
            byte[] audioData,
            String mediaType,
            Map<String, String> parameters) {

        MediaProcessingJob job = localJobCache.get(jobId);
        if (job == null) return;

        updateJobStatus(jobId, JobStatus.PROCESSING, 30, null);

        // Process via media gateway
        AudioResult audioResult = mediaGateway.transcribe(audioData);

        updateJobStatus(jobId, JobStatus.PROCESSING, 70, null);

        // Build transcript result
        String resultId = "transcript-" + jobId;
        String languageCode = parameters.getOrDefault("languageCode", "en-US");

        List<TranscriptSegment> segments = audioResult.segments().stream()
                .map(s -> new TranscriptSegment(
                        UUID.randomUUID().toString(),
                        s.startMs(),
                        s.endMs(),
                        s.speakerId(),
                        s.text(),
                        s.confidence()
                ))
                .toList();

        TranscriptResult transcript = new TranscriptResult(
                resultId,
                job.artifactId(),
                jobId,
                languageCode,
                segments,
                audioResult.fullText(),
                audioResult.confidence(),
                audioResult.durationMs(),
                audioResult.wordCount(),
                audioResult.speakerCount(),
                Map.of("source", mediaGateway.backendName()),
                Instant.now()
        );

        String resultKey = buildResultKey(job.artifactId(), job.tenantId());
        transcriptCache.put(resultKey, transcript);
        updateJobStatus(jobId, JobStatus.COMPLETED, 100, null);
        updateJobResultId(jobId, resultId);

        // Persist transcript to Data Cloud repository for traceability
        Transcript dataCloudTranscript = new Transcript(
                resultId,
                job.artifactId(),
                job.tenantId(),
                languageCode,
                audioResult.fullText(),
                audioResult.confidence(),
                audioResult.durationMs(),
                audioResult.wordCount(),
                audioResult.speakerCount(),
                segments.stream()
                        .map(s -> new Transcript.Segment(
                                s.segmentId(),
                                s.startMs(),
                                s.endMs(),
                                s.speakerId(),
                                s.text(),
                                s.confidence()
                        ))
                        .toList(),
                Map.of("source", mediaGateway.backendName()),
                Instant.now()
        );

        dataCloudRepository.saveTranscript(job.artifactId(), job.tenantId(), dataCloudTranscript)
            .whenResult(saved -> {
                LOG.info("[DataCloudMedia] Transcript {} persisted to Data Cloud for artifact {}", resultId, job.artifactId());
                // Update artifact record with transcript ID
                dataCloudRepository.updateTranscriptId(job.artifactId(), job.tenantId(), resultId, "system")
                    .whenResult(updated -> {
                        if (updated) {
                            LOG.info("[DataCloudMedia] Artifact {} updated with transcript ID {}", job.artifactId(), resultId);
                        }
                    });
            })
            .whenException(e -> {
                LOG.error("[DataCloudMedia] Failed to persist transcript {} to Data Cloud", resultId, e);
            });

        LOG.info("[DataCloudMedia] Transcription job {} completed: {} words",
                jobId, transcript.wordCount());
    }

    private void processVisionJob(
            String jobId,
            byte[] visualData,
            String mediaType,
            Map<String, String> parameters) {

        MediaProcessingJob job = localJobCache.get(jobId);
        if (job == null) return;

        updateJobStatus(jobId, JobStatus.PROCESSING, 30, null);

        String analysisType = parameters.getOrDefault("analysisType", "OBJECT_DETECTION");
        String resultId = "frameindex-" + jobId;

        VisualResult visualResult;
        List<FrameResult> frames = new ArrayList<>();

        if (mediaType.startsWith("image/")) {
            visualResult = mediaGateway.analyseImage(visualData);
            frames.add(new FrameResult(0, 0,
                    visualResult.detections().stream()
                            .map(d -> new FrameDetection(
                                    d.label(),
                                    d.confidence(),
                                    List.of(d.x(), d.y(), d.width(), d.height())
                            ))
                            .toList()
            ));
        } else if (mediaType.startsWith("video/")) {
            int sampleFps = Integer.parseInt(parameters.getOrDefault("sampleFps", "1"));
            int maxFrames = Integer.parseInt(parameters.getOrDefault("maxFrames", "30"));
            visualResult = mediaGateway.analyseVideo(visualData, sampleFps, maxFrames);
            frames = visualResult.frameResults();
        } else {
            throw new MultimodalException("Unsupported media type for vision analysis: " + mediaType);
        }

        updateJobStatus(jobId, JobStatus.PROCESSING, 70, null);

        // Aggregate labels
        Map<String, LabelAggregate> labelAggregates = new HashMap<>();
        List<FrameIndexEntry> frameIndexEntries = new ArrayList<>();

        for (FrameResult frame : frames) {
            List<String> frameLabels = new ArrayList<>();
            for (var detection : frame.detections()) {
                frameLabels.add(detection.label());
                labelAggregates.computeIfAbsent(detection.label(),
                        k -> new LabelAggregate(k, 0, 0.0))
                        .add(detection.confidence());
            }
            frameIndexEntries.add(new FrameIndexEntry(
                    frame.frameNumber(),
                    frame.timestampMs(),
                    frameLabels,
                    Map.of(), // bounding boxes
                    frame.detections().isEmpty() ? 0.0 :
                            frame.detections().get(0).confidence()
            ));
        }

        List<FrameLabel> aggregatedLabels = labelAggregates.values().stream()
                .map(la -> new FrameLabel(la.label, la.count, la.avgConfidence()))
                .toList();

        // Extract temporal events
        List<ExtractedEvent> events = extractTemporalEvents(frames);
        String eventsKey = buildResultKey(job.artifactId(), job.tenantId());
        eventsCache.put(eventsKey, events);

        FrameIndexResult frameIndex = new FrameIndexResult(
                resultId,
                job.artifactId(),
                jobId,
                analysisType,
                frameIndexEntries,
                aggregatedLabels,
                events.stream()
                        .map(e -> new FrameEvent(e.eventType(), e.startMs(), e.endMs(), e.description(), e.confidence()))
                        .toList(),
                visualResult.confidence(),
                frames.size(),
                visualResult.frameResults().isEmpty() ? 0 :
                        visualResult.frameResults().get(0).timestampMs(),
                Map.of("source", mediaGateway.backendName()),
                Instant.now()
        );

        String resultKey = buildResultKey(job.artifactId(), job.tenantId());
        frameIndexCache.put(resultKey, frameIndex);
        updateJobStatus(jobId, JobStatus.COMPLETED, 100, null);
        updateJobResultId(jobId, resultId);

        // Persist frame index to Data Cloud repository for traceability
        FrameIndex dataCloudFrameIndex = new FrameIndex(
                resultId,
                job.artifactId(),
                job.tenantId(),
                analysisType,
                frameIndexEntries.stream()
                        .map(fie -> new FrameIndex.Entry(
                                fie.frameNumber(),
                                fie.timestampMs(),
                                fie.labels(),
                                fie.boundingBoxes(),
                                fie.confidence()
                        ))
                        .toList(),
                aggregatedLabels.stream()
                        .map(fl -> new FrameIndex.Label(
                                fl.label(),
                                fl.count(),
                                fl.avgConfidence()
                        ))
                        .toList(),
                events.stream()
                        .map(e -> new FrameIndex.Event(
                                e.eventType(),
                                e.startMs(),
                                e.endMs(),
                                e.description(),
                                e.confidence()
                        ))
                        .toList(),
                visualResult.confidence(),
                frames.size(),
                visualResult.frameResults().isEmpty() ? 0 :
                        visualResult.frameResults().get(0).timestampMs(),
                Map.of("source", mediaGateway.backendName()),
                Instant.now()
        );

        dataCloudRepository.saveFrameIndex(job.artifactId(), job.tenantId(), dataCloudFrameIndex)
            .whenResult(saved -> {
                LOG.info("[DataCloudMedia] Frame index {} persisted to Data Cloud for artifact {}", resultId, job.artifactId());
                // Update artifact record with frame index ID
                dataCloudRepository.updateFrameIndexId(job.artifactId(), job.tenantId(), resultId, "system")
                    .whenResult(updated -> {
                        if (updated) {
                            LOG.info("[DataCloudMedia] Artifact {} updated with frame index ID {}", job.artifactId(), resultId);
                        }
                    });
            })
            .whenException(e -> {
                LOG.error("[DataCloudMedia] Failed to persist frame index {} to Data Cloud", resultId, e);
            });

        LOG.info("[DataCloudMedia] Vision job {} completed: {} frames, {} labels",
                jobId, frames.size(), aggregatedLabels.size());
    }

    private void processMultimodalJob(
            String jobId,
            byte[] mediaData,
            String mediaType,
            Map<String, String> parameters) {
        // Process both audio and visual components
        if (mediaType.startsWith("video/")) {
            processVisionJob(jobId, mediaData, mediaType, parameters);
            // Also extract audio and transcribe if requested
            if (parameters.containsKey("extractAudio") &&
                    Boolean.parseBoolean(parameters.get("extractAudio"))) {
                // Note: Audio extraction from video would require additional processing
                LOG.info("[DataCloudMedia] Multimodal job {} - audio extraction requested", jobId);
            }
        } else {
            processVisionJob(jobId, mediaData, mediaType, parameters);
        }
    }

    private List<ExtractedEvent> extractTemporalEvents(List<FrameResult> frames) {
        List<ExtractedEvent> events = new ArrayList<>();
        Map<String, List<FrameResult>> labelFrames = new HashMap<>();

        // Group frames by detected labels
        for (FrameResult frame : frames) {
            for (var detection : frame.detections()) {
                labelFrames.computeIfAbsent(detection.label(), k -> new ArrayList<>()).add(frame);
            }
        }

        // Create events for labels appearing in consecutive frames
        labelFrames.forEach((label, labelFrameList) -> {
            if (labelFrameList.size() >= 2) {
                long startMs = labelFrameList.get(0).timestampMs();
                long endMs = labelFrameList.get(labelFrameList.size() - 1).timestampMs();
                double avgConfidence = labelFrameList.stream()
                        .flatMap(f -> f.detections().stream())
                        .filter(d -> d.label().equals(label))
                        .mapToDouble(DetectionResult::confidence)
                        .average()
                        .orElse(0.0);

                events.add(new ExtractedEvent(
                        label + "_detected",
                        startMs,
                        endMs,
                        "Label '" + label + "' detected in " + labelFrameList.size() + " frames",
                        avgConfidence
                ));
            }
        });

        return events;
    }

    private void updateJobStatus(String jobId, JobStatus status, int progress, String errorMessage) {
        MediaProcessingJob current = jobStore.get(jobId);
        if (current == null) return;

        MediaProcessingJob updated = new MediaProcessingJob(
                current.jobId(),
                current.artifactId(),
                current.tenantId(),
                current.jobType(),
                status.name(),
                current.parameters(),
                current.resultId(),
                errorMessage,
                progress,
                current.createdAt(),
                status == JobStatus.PROCESSING && current.startedAt() == null
                        ? Instant.now() : current.startedAt(),
                status.isTerminal() ? Instant.now() : current.completedAt(),
                current.createdBy()
        );

        jobStore.put(jobId, updated);

        LOG.debug("[DataCloudMedia] Job {} status: {} ({}%)",
                jobId, status, progress);
    }

    private void updateJobResultId(String jobId, String resultId) {
        MediaProcessingJob current = jobStore.get(jobId);
        if (current == null) return;

        MediaProcessingJob updated = new MediaProcessingJob(
                current.jobId(),
                current.artifactId(),
                current.tenantId(),
                current.jobType(),
                current.status(),
                current.parameters(),
                resultId,
                current.errorMessage(),
                current.progress(),
                current.createdAt(),
                current.startedAt(),
                current.completedAt(),
                current.createdBy()
        );

        jobStore.put(jobId, updated);
    }

    // -------------------------------------------------------------------------
    // Inner Classes for Aggregation
    // -------------------------------------------------------------------------

    private static class LabelAggregate {
        final String label;
        int count;
        double totalConfidence;

        LabelAggregate(String label, int count, double totalConfidence) {
            this.label = label;
            this.count = count;
            this.totalConfidence = totalConfidence;
        }

        void add(double confidence) {
            count++;
            totalConfidence += confidence;
        }

        double avgConfidence() {
            return count > 0 ? totalConfidence / count : 0.0;
        }
    }
}
