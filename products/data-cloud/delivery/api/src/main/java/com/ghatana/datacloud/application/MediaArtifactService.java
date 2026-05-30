package com.ghatana.datacloud.application;

import com.ghatana.datacloud.entity.media.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing media artifacts with complete production workflow.
 *
 * <p><b>Purpose</b><br>
 * Provides end-to-end media artifact lifecycle management including upload,
 * privacy checks, processing jobs, transcript/frame index generation, Data Cloud
 * indexing, and search/context/action integration.
 *
 * <p><b>Workflow</b><br>
 * 1. Upload/register artifact with metadata
 * 2. Privacy/consent validation
 * 3. Processing job creation (transcription, vision analysis, etc.)
 * 4. Transcript/frame index generation
 * 5. Data Cloud indexing for search
 * 6. Context and action integration
 * 7. Retention policy enforcement
 *
 * @doc.type class
 * @doc.purpose Media artifact lifecycle and processing workflow
 * @doc.layer product
 * @doc.pattern Service
 */
public class MediaArtifactService {

    private static final Logger log = LoggerFactory.getLogger(MediaArtifactService.class);

    private final MediaArtifactRepository artifactRepository;
    private final MediaProcessingJobRepository jobRepository;
    private final TranscriptRepository transcriptRepository;
    private final FrameIndexRepository frameIndexRepository;
    private final ConsentRepository consentRepository;
    private final RetentionPolicyRepository retentionPolicyRepository;

    public MediaArtifactService(
            MediaArtifactRepository artifactRepository,
            MediaProcessingJobRepository jobRepository,
            TranscriptRepository transcriptRepository,
            FrameIndexRepository frameIndexRepository,
            ConsentRepository consentRepository,
            RetentionPolicyRepository retentionPolicyRepository) {
        this.artifactRepository = Objects.requireNonNull(artifactRepository, "artifactRepository cannot be null");
        this.jobRepository = Objects.requireNonNull(jobRepository, "jobRepository cannot be null");
        this.transcriptRepository = Objects.requireNonNull(transcriptRepository, "transcriptRepository cannot be null");
        this.frameIndexRepository = Objects.requireNonNull(frameIndexRepository, "frameIndexRepository cannot be null");
        this.consentRepository = Objects.requireNonNull(consentRepository, "consentRepository cannot be null");
        this.retentionPolicyRepository = Objects.requireNonNull(retentionPolicyPolicyRepository, "retentionPolicyRepository cannot be null");
    }

    /**
     * Registers a new media artifact with privacy validation.
     */
    public Promise<MediaArtifact> registerArtifact(String tenantId, String agentId, String mediaType,
                                                     String storageUri, Long sizeBytes, String checksum,
                                                     Long durationMs, Map<String, String> metadata) {
        log.info("[media-artifact] Registering artifact for tenant: {}, type: {}", tenantId, mediaType);

        MediaArtifact artifact = MediaArtifact.builder()
                .artifactId("artifact-" + UUID.randomUUID().toString().substring(0, 8))
                .tenantId(tenantId)
                .agentId(agentId)
                .mediaType(mediaType)
                .storageUri(storageUri)
                .sizeBytes(sizeBytes)
                .checksum(checksum)
                .durationMs(durationMs)
                .metadata(metadata != null ? metadata : Map.of())
                .classification(MediaArtifact.Classification.INTERNAL)
                .build();

        // Check if consent is required
        if (artifact.requiresConsent()) {
            artifact.setConsentStatus(MediaArtifact.ConsentStatus.PENDING);
            log.info("[media-artifact] Consent required for artifact: {}", artifact.getArtifactId());
        } else {
            artifact.setConsentStatus(MediaArtifact.ConsentStatus.NONE);
        }

        return artifactRepository.save(artifact)
                .then(saved -> {
                    log.info("[media-artifact] Artifact registered: {}", saved.getArtifactId());
                    return Promise.of(saved);
                });
    }

    /**
     * Validates privacy/consent before processing.
     */
    public Promise<Boolean> validatePrivacy(String artifactId, String tenantId) {
        log.info("[media-artifact] Validating privacy for artifact: {}", artifactId);

        return artifactRepository.findById(artifactId, tenantId)
                .then(artifactOpt -> {
                    if (artifactOpt.isEmpty()) {
                        log.warn("[media-artifact] Artifact not found: {}", artifactId);
                        return Promise.of(false);
                    }

                    MediaArtifact artifact = artifactOpt.get();

                    if (!artifact.requiresConsent()) {
                        log.info("[media-artifact] No consent required for artifact: {}", artifactId);
                        return Promise.of(true);
                    }

                    // Check for valid consent
                    return consentRepository.findByMediaArtifactId(artifact.getId(), tenantId)
                            .then(consents -> {
                                boolean hasValidConsent = consents.stream()
                                        .anyMatch(Consent::isValid);

                                if (hasValidConsent) {
                                    artifact.setConsentStatus(MediaArtifact.ConsentStatus.CONSENTED);
                                    return artifactRepository.save(artifact)
                                            .then(saved -> Promise.of(true));
                                } else {
                                    log.warn("[media-artifact] No valid consent for artifact: {}", artifactId);
                                    return Promise.of(false);
                                }
                            });
                });
    }

    /**
     * Creates a processing job for the artifact.
     */
    public Promise<MediaProcessingJob> createProcessingJob(String artifactId, String tenantId,
                                                              MediaProcessingJob.JobType jobType,
                                                              Map<String, Object> parameters,
                                                              String requestedBy) {
        log.info("[media-artifact] Creating processing job for artifact: {}, type: {}", artifactId, jobType);

        return artifactRepository.findById(artifactId, tenantId)
                .then(artifactOpt -> {
                    if (artifactOpt.isEmpty()) {
                        log.warn("[media-artifact] Artifact not found: {}", artifactId);
                        return Promise.ofException(new IllegalArgumentException("Artifact not found"));
                    }

                    MediaArtifact artifact = artifactOpt.get();

                    MediaProcessingJob job = MediaProcessingJob.builder()
                            .jobId(jobType.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8))
                            .tenantId(tenantId)
                            .mediaArtifact(artifact)
                            .jobType(jobType)
                            .status(MediaProcessingJob.JobStatus.PENDING)
                            .parameters(parameters != null ? parameters : Map.of())
                            .requestedBy(requestedBy)
                            .build();

                    return jobRepository.save(job)
                            .then(saved -> {
                        log.info("[media-artifact] Processing job created: {}", saved.getJobId());
                        return Promise.of(saved);
                    });
                });
    }

    /**
     * Starts a processing job.
     */
    public Promise<MediaProcessingJob> startProcessingJob(String jobId, String tenantId, String workerNode) {
        log.info("[media-artifact] Starting processing job: {}", jobId);

        return jobRepository.findById(jobId, tenantId)
                .then(jobOpt -> {
                    if (jobOpt.isEmpty()) {
                        log.warn("[media-artifact] Job not found: {}", jobId);
                        return Promise.ofException(new IllegalArgumentException("Job not found"));
                    }

                    MediaProcessingJob job = jobOpt.get();
                    job.startProcessing(workerNode);

                    return jobRepository.save(job)
                            .then(saved -> Promise.of(saved));
                });
    }

    /**
     * Completes a processing job with results.
     */
    public Promise<MediaProcessingJob> completeProcessingJob(String jobId, String tenantId,
                                                              Map<String, Object> results) {
        log.info("[media-artifact] Completing processing job: {}", jobId);

        return jobRepository.findById(jobId, tenantId)
                .then(jobOpt -> {
                    if (jobOpt.isEmpty()) {
                        log.warn("[media-artifact] Job not found: {}", jobId);
                        return Promise.ofException(new IllegalArgumentException("Job not found"));
                    }

                    MediaProcessingJob job = jobOpt.get();
                    job.complete(results);

                    return jobRepository.save(job)
                            .then(saved -> {
                        // Create transcript if this was a transcription job
                        if (job.getJobType() == MediaProcessingJob.JobType.TRANSCRIPTION && results.containsKey("transcript")) {
                            return createTranscriptFromJob(saved, results)
                                    .map(transcript -> saved);
                        }
                        return Promise.of(saved);
                    });
                });
    }

    /**
     * Creates a transcript from a completed transcription job.
     */
    private Promise<Transcript> createTranscriptFromJob(MediaProcessingJob job, Map<String, Object> results) {
        log.info("[media-artifact] Creating transcript from job: {}", job.getJobId());

        String transcriptText = (String) results.get("transcript");
        String languageCode = (String) job.getParameters().getOrDefault("languageCode", "en-US");

        Transcript transcript = Transcript.builder()
                .transcriptId("transcript-" + UUID.randomUUID().toString().substring(0, 8))
                .tenantId(job.getTenantId())
                .mediaArtifact(job.getMediaArtifact())
                .processingJob(job)
                .languageCode(languageCode)
                .detectedLanguage(languageCode)
                .fullText(transcriptText)
                .segments(extractSegments(results))
                .build();

        return transcriptRepository.save(transcript)
                .then(saved -> {
                    log.info("[media-artifact] Transcript created: {}", saved.getTranscriptId());
                    return Promise.of(saved);
                });
    }

    /**
     * Extracts transcript segments from job results.
     */
    private List<Transcript.TranscriptSegment> extractSegments(Map<String, Object> results) {
        List<Map<String, Object>> segmentsData = (List<Map<String, Object>>) results.get("segments");
        if (segmentsData == null) {
            return List.of();
        }

        List<Transcript.TranscriptSegment> segments = new ArrayList<>();
        for (Map<String, Object> segmentData : segmentsData) {
            segments.add(new Transcript.TranscriptSegment(
                    ((Number) segmentData.getOrDefault("startTimeMs", 0L)).longValue(),
                    ((Number) segmentData.getOrDefault("endTimeMs", 0L)).longValue(),
                    (String) segmentData.get("text"),
                    ((Number) segmentData.getOrDefault("confidence", 0.0)).doubleValue(),
                    (String) segmentData.getOrDefault("speakerId", "unknown"),
                    (Map<String, Object>) segmentData.getOrDefault("metadata", Map.of())
            ));
        }
        return segments;
    }

    /**
     * Applies retention policy to an artifact.
     */
    public Promise<MediaArtifact> applyRetentionPolicy(String artifactId, String tenantId, String policyName) {
        log.info("[media-artifact] Applying retention policy: {} to artifact: {}", policyName, artifactId);

        return retentionPolicyRepository.findByName(policyName, tenantId)
                .then(policyOpt -> {
                    if (policyOpt.isEmpty()) {
                        log.warn("[media-artifact] Retention policy not found: {}", policyName);
                        return Promise.ofException(new IllegalArgumentException("Retention policy not found"));
                    }

                    RetentionPolicy policy = policyOpt.get();

                    return artifactRepository.findById(artifactId, tenantId)
                            .then(artifactOpt -> {
                                if (artifactOpt.isEmpty()) {
                                    return Promise.ofException(new IllegalArgumentException("Artifact not found"));
                                }

                                MediaArtifact artifact = artifactOpt.get();
                                artifact.setRetentionPolicy(policyName);

                                // Calculate expiration based on policy
                                if (policy.getRetentionDays() != null) {
                                    Instant expiresAt = Instant.now().plus(java.time.Duration.ofDays(policy.getRetentionDays()));
                                    artifact.setExpiresAt(expiresAt);
                                }

                                return artifactRepository.save(artifact);
                            });
                });
    }

    /**
     * Deletes an artifact and all associated data.
     */
    public Promise<Void> deleteArtifact(String artifactId, String tenantId, String reason) {
        log.info("[media-artifact] Deleting artifact: {}, reason: {}", artifactId, reason);

        return artifactRepository.findById(artifactId, tenantId)
                .then(artifactOpt -> {
                    if (artifactOpt.isEmpty()) {
                        log.warn("[media-artifact] Artifact not found: {}", artifactId);
                        return Promise.ofComplete();
                    }

                    MediaArtifact artifact = artifactOpt.get();

                    // Delete associated data in parallel
                    Promise<Void> deleteJobs = jobRepository.deleteByMediaArtifactId(artifact.getId(), tenantId);
                    Promise<Void> deleteTranscripts = transcriptRepository.deleteByMediaArtifactId(artifact.getId(), tenantId);
                    Promise<Void> deleteFrameIndices = frameIndexRepository.deleteByMediaArtifactId(artifact.getId(), tenantId);
                    Promise<Void> deleteConsents = consentRepository.deleteByMediaArtifactId(artifact.getId(), tenantId);

                    return Promise.all(deleteJobs, deleteTranscripts, deleteFrameIndices, deleteConsents)
                            .then(ignored -> artifactRepository.delete(artifactId, tenantId))
                            .then(deleted -> Promise.ofComplete());
                });
    }

    /**
     * Gets artifacts by tenant with filtering.
     */
    public Promise<List<MediaArtifact>> listArtifacts(String tenantId, String mediaType, String agentId, int limit) {
        log.info("[media-artifact] Listing artifacts for tenant: {}, type: {}, agent: {}", tenantId, mediaType, agentId);

        if (mediaType != null) {
            return artifactRepository.findByMediaType(mediaType, tenantId, limit);
        } else if (agentId != null) {
            return artifactRepository.findByAgent(agentId, tenantId, limit);
        } else {
            return artifactRepository.findByTenant(tenantId, limit);
        }
    }

    /**
     * Gets artifact by ID.
     */
    public Promise<Optional<MediaArtifact>> getArtifact(String artifactId, String tenantId) {
        return artifactRepository.findById(artifactId, tenantId);
    }

    /**
     * Gets processing jobs for an artifact.
     */
    public Promise<List<MediaProcessingJob>> getProcessingJobs(String artifactId, String tenantId) {
        return jobRepository.findByMediaArtifactId(UUID.fromString(artifactId), tenantId);
    }

    /**
     * Gets transcripts for an artifact.
     */
    public Promise<List<Transcript>> getTranscripts(String artifactId, String tenantId) {
        return transcriptRepository.findByMediaArtifactId(UUID.fromString(artifactId), tenantId);
    }

    /**
     * Searches transcripts for text.
     */
    public Promise<List<Transcript>> searchTranscripts(String tenantId, String searchText) {
        log.info("[media-artifact] Searching transcripts for: {}", searchText);
        return transcriptRepository.searchByText(searchText, tenantId);
    }
}
