/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.entity.media.MediaArtifact;
import com.ghatana.platform.core.common.pagination.PageRequest;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * JPA-based durable implementation of {@link MediaArtifactRepository}.
 *
 * <p>WS3-7: Bridges between the memory/media SPI (using MediaArtifactRecord) and
 * the JPA entity layer (using MediaArtifact entity). Provides durable persistence
 * through PostgreSQL with comprehensive governance and lifecycle support.
 *
 * <p>This implementation:
 * <ul>
 *   <li>Maps between MediaArtifactRecord (SPI) and MediaArtifact (JPA entity)</li>
 *   <li>Uses JPA MediaArtifactRepository for database operations</li>
 *   <li>Wraps synchronous JPA operations in ActiveJ Promises</li>
 *   <li>Supports all governance fields: consent, retention, classification, lineage</li>
 *   <li>Enforces tenant isolation at the database level via RLS policies</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Durable JPA implementation of MediaArtifactRepository SPI
 * @doc.layer product
 * @doc.pattern Repository Implementation
 */
public final class JpaMediaArtifactRepository implements MediaArtifactRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaMediaArtifactRepository.class);

    private final com.ghatana.datacloud.entity.media.MediaArtifactRepository artifactJpaRepository;
    private final com.ghatana.datacloud.entity.media.MediaProcessingJobRepository jobJpaRepository;
    private final com.ghatana.datacloud.entity.media.TranscriptRepository transcriptJpaRepository;
    private final com.ghatana.datacloud.entity.media.FrameIndexRepository frameIndexJpaRepository;
    private final Executor blockingExecutor;

    public JpaMediaArtifactRepository(com.ghatana.datacloud.entity.media.MediaArtifactRepository artifactJpaRepository, Executor blockingExecutor) {
        this(artifactJpaRepository, null, null, null, blockingExecutor);
    }

    public JpaMediaArtifactRepository(
            com.ghatana.datacloud.entity.media.MediaArtifactRepository artifactJpaRepository,
            com.ghatana.datacloud.entity.media.MediaProcessingJobRepository jobJpaRepository,
            com.ghatana.datacloud.entity.media.TranscriptRepository transcriptJpaRepository,
            com.ghatana.datacloud.entity.media.FrameIndexRepository frameIndexJpaRepository,
            Executor blockingExecutor) {
        this.artifactJpaRepository = Objects.requireNonNull(artifactJpaRepository, "artifactJpaRepository cannot be null");
        this.jobJpaRepository = jobJpaRepository;
        this.transcriptJpaRepository = transcriptJpaRepository;
        this.frameIndexJpaRepository = frameIndexJpaRepository;
        this.blockingExecutor = Objects.requireNonNull(blockingExecutor, "blockingExecutor cannot be null");
        log.info("[media-artifact] JPA repository initialized with durable persistence");
    }

    @Override
    public Promise<MediaArtifactRecord> save(MediaArtifactRecord record) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            MediaArtifact entity = toEntity(record);
            MediaArtifact saved = artifactJpaRepository.save(entity);
            log.debug("Saved media artifact [{}] for tenant [{}]", saved.getArtifactId(), saved.getTenantId());
            return toRecord(saved);
        });
    }

    @Override
    public Promise<Optional<MediaArtifactRecord>> findById(String artifactId, String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entity = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            return entity.map(this::toRecord);
        });
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByAgent(String agentId, String tenantId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            List<MediaArtifact> entities = artifactJpaRepository.findByTenantIdAndAgentId(tenantId, agentId);
            List<MediaArtifactRecord> records = new ArrayList<>();
            for (int i = 0; i < Math.min(entities.size(), limit); i++) {
                records.add(toRecord(entities.get(i)));
            }
            return records;
        });
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByMediaType(String mediaType, String tenantId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            List<MediaArtifact> entities = artifactJpaRepository.findByTenantIdAndMediaType(tenantId, mediaType);
            List<MediaArtifactRecord> records = new ArrayList<>();
            for (int i = 0; i < Math.min(entities.size(), limit); i++) {
                records.add(toRecord(entities.get(i)));
            }
            return records;
        });
    }

    @Override
    public Promise<Boolean> delete(String artifactId, String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entity = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entity.isPresent()) {
                artifactJpaRepository.delete(entity.get());
                log.debug("Deleted media artifact [{}] for tenant [{}]", artifactId, tenantId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateProcessingState(String artifactId, String tenantId, String processingState) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                // Store processing state in metadata for JPA entity
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("processingState", processingState);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated processing state for artifact [{}] to [{}]", artifactId, processingState);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateStatus(String artifactId, String tenantId, String status) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                // Store status in metadata for JPA entity
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("status", status);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated status for artifact [{}] to [{}]", artifactId, status);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByProcessingState(String processingState, String tenantId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // JPA entity doesn't have processingState field, filter via metadata
            List<MediaArtifact> allEntities = artifactJpaRepository.findByTenantId(tenantId, PageRequest.of(0, 1000)).content();
            List<MediaArtifactRecord> records = new ArrayList<>();
            for (MediaArtifact entity : allEntities) {
                String state = entity.getMetadata() != null ? entity.getMetadata().get("processingState") : null;
                if (processingState.equals(state)) {
                    records.add(toRecord(entity));
                    if (records.size() >= limit) break;
                }
            }
            return records;
        });
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByStatus(String status, String tenantId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            // JPA entity doesn't have status field, filter via metadata
            List<MediaArtifact> allEntities = artifactJpaRepository.findByTenantId(tenantId, PageRequest.of(0, 1000)).content();
            List<MediaArtifactRecord> records = new ArrayList<>();
            for (MediaArtifact entity : allEntities) {
                String entityStatus = entity.getMetadata() != null ? entity.getMetadata().get("status") : null;
                if (status.equals(entityStatus)) {
                    records.add(toRecord(entity));
                    if (records.size() >= limit) break;
                }
            }
            return records;
        });
    }

    @Override
    public Promise<List<String>> findProcessingJobsByArtifact(String artifactId, String tenantId, int limit) {
        // JPA entity has processingJobs relationship
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                List<String> jobIds = new ArrayList<>();
                for (com.ghatana.datacloud.entity.media.MediaProcessingJob job : entityOpt.get().getProcessingJobs()) {
                    jobIds.add(job.getJobId());
                    if (jobIds.size() >= limit) break;
                }
                return jobIds;
            }
            return List.of();
        });
    }

    @Override
    public Promise<List<String>> findProcessingResultsByArtifact(String artifactId, String tenantId, int limit) {
        // Results are stored as transcriptId and frameIndexId in metadata
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                List<String> resultIds = new ArrayList<>();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata != null) {
                    if (metadata.containsKey("transcriptId")) {
                        resultIds.add(metadata.get("transcriptId"));
                    }
                    if (metadata.containsKey("frameIndexId")) {
                        resultIds.add(metadata.get("frameIndexId"));
                    }
                }
                return resultIds;
            }
            return List.of();
        });
    }

    @Override
    public Promise<Boolean> updateLastError(String artifactId, String tenantId, String lastError, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("lastError", lastError);
                metadata.put("updatedBy", updatedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated last error for artifact [{}]", artifactId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateFrameIndexId(String artifactId, String tenantId, String frameIndexId, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("frameIndexId", frameIndexId);
                metadata.put("updatedBy", updatedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated frame index ID for artifact [{}]", artifactId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateTranscriptId(String artifactId, String tenantId, String transcriptId, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("transcriptId", transcriptId);
                metadata.put("updatedBy", updatedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated transcript ID for artifact [{}]", artifactId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateProcessingJobId(String artifactId, String tenantId, String processingJobId, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("processingJobId", processingJobId);
                metadata.put("updatedBy", updatedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated processing job ID for artifact [{}]", artifactId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateConsentStatus(String artifactId, String tenantId, String consentStatus, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                // Map consent status string to enum
                MediaArtifact.ConsentStatus status = mapConsentStatus(consentStatus);
                entity.setConsentStatus(status);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated consent status for artifact [{}] to [{}]", artifactId, consentStatus);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> markDeleted(String artifactId, String tenantId, String deletedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("deletedAt", Instant.now().toString());
                metadata.put("deletedBy", deletedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Marked artifact [{}] as deleted", artifactId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByRetentionDue(String tenantId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            List<MediaArtifact> entities = artifactJpaRepository.findExpiredArtifacts(Instant.now());
            List<MediaArtifactRecord> records = new ArrayList<>();
            for (int i = 0; i < Math.min(entities.size(), limit); i++) {
                records.add(toRecord(entities.get(i)));
            }
            return records;
        });
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByConsentStatus(String consentStatus, String tenantId, int limit) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            MediaArtifact.ConsentStatus status = mapConsentStatus(consentStatus);
            List<MediaArtifact> entities = artifactJpaRepository.findByTenantIdAndConsentStatus(tenantId, status);
            List<MediaArtifactRecord> records = new ArrayList<>();
            for (int i = 0; i < Math.min(entities.size(), limit); i++) {
                records.add(toRecord(entities.get(i)));
            }
            return records;
        });
    }

    @Override
    public Promise<List<MediaProcessingJob>> findJobs(String artifactId, String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isEmpty()) {
                return List.of();
            }

            if (jobJpaRepository != null) {
                return jobJpaRepository.findByTenantIdAndMediaArtifactId(tenantId, entityOpt.get().getId()).stream()
                    .map(this::toJobRecord)
                    .toList();
            }

            List<MediaProcessingJob> jobs = new ArrayList<>();
            for (com.ghatana.datacloud.entity.media.MediaProcessingJob entityJob : entityOpt.get().getProcessingJobs()) {
                jobs.add(toJobRecord(entityJob));
            }
            return jobs;
        });
    }

    @Override
    public Promise<Transcript> saveTranscript(String artifactId, String tenantId, Transcript transcript) {
        if (transcriptJpaRepository == null) {
            return updateTranscriptId(artifactId, tenantId, transcript.transcriptId(), transcript.createdBy())
                .map(updated -> transcript);
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> artifactOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (artifactOpt.isEmpty()) {
                return transcript;
            }

            com.ghatana.datacloud.entity.media.Transcript entity = new com.ghatana.datacloud.entity.media.Transcript();
            entity.setTranscriptId(transcript.transcriptId());
            entity.setTenantId(tenantId);
            entity.setMediaArtifact(artifactOpt.get());
            if (jobJpaRepository != null && transcript.jobId() != null) {
                jobJpaRepository.findByTenantIdAndJobId(tenantId, transcript.jobId())
                    .ifPresent(entity::setProcessingJob);
            }
            entity.setLanguageCode(transcript.languageCode());
            entity.setDetectedLanguage(transcript.languageCode());
            entity.setConfidenceScore(transcript.confidence());
            entity.setWordCount(transcript.wordCount());
            entity.setSpeakerCount(transcript.speakerCount());
            entity.setFullText(transcript.fullText());
            entity.setSegments(transcript.segments().stream()
                .map(segment -> new com.ghatana.datacloud.entity.media.Transcript.TranscriptSegment(
                    segment.startMs(),
                    segment.endMs(),
                    segment.text(),
                    segment.confidence(),
                    segment.speakerId(),
                    Map.of("segmentId", segment.segmentId())))
                .toList());
            Map<String, Object> processingMetadata = new HashMap<>();
            processingMetadata.putAll(transcript.metadata());
            processingMetadata.put("durationMs", transcript.durationMs());
            processingMetadata.put("createdBy", transcript.createdBy());
            entity.setProcessingMetadata(processingMetadata);
            entity.setCreatedAt(transcript.createdAt());
            entity.setUpdatedAt(Instant.now());

            transcriptJpaRepository.save(entity);
            return transcript;
        }).then(saved -> updateTranscriptId(artifactId, tenantId, saved.transcriptId(), saved.createdBy())
            .map(updated -> saved));
    }

    @Override
    public Promise<Optional<Transcript>> findTranscript(String artifactId, String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> artifactOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (artifactOpt.isEmpty()) {
                return Optional.empty();
            }

            if (transcriptJpaRepository != null) {
                return transcriptJpaRepository.findByTenantIdAndMediaArtifactId(tenantId, artifactOpt.get().getId()).stream()
                    .findFirst()
                    .map(this::toTranscriptRecord);
            }

            Map<String, String> metadata = artifactOpt.get().getMetadata();
            if (metadata != null && metadata.containsKey("transcriptId")) {
                String createdBy = metadata.getOrDefault("updatedBy", metadata.getOrDefault("createdBy", "media-artifact-service"));
                return Optional.of(new Transcript(
                    metadata.get("transcriptId"),
                    artifactId,
                    tenantId,
                    metadata.get("processingJobId"),
                    "en-US",
                    List.of(),
                    "",
                    0.0,
                    0L,
                    0,
                    0,
                    Map.of(),
                    artifactOpt.get().getUpdatedAt() != null ? artifactOpt.get().getUpdatedAt() : Instant.now(),
                    createdBy
                ));
            }
            return Optional.empty();
        });
    }

    @Override
    public Promise<FrameIndex> saveFrameIndex(String artifactId, String tenantId, FrameIndex frameIndex) {
        if (frameIndexJpaRepository == null) {
            return updateFrameIndexId(artifactId, tenantId, frameIndex.frameIndexId(), frameIndex.createdBy())
                .map(updated -> frameIndex);
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> artifactOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (artifactOpt.isEmpty()) {
                return frameIndex;
            }

            com.ghatana.datacloud.entity.media.FrameIndex entity = new com.ghatana.datacloud.entity.media.FrameIndex();
            entity.setFrameIndexId(frameIndex.frameIndexId());
            entity.setTenantId(tenantId);
            entity.setMediaArtifact(artifactOpt.get());
            if (jobJpaRepository != null && frameIndex.jobId() != null) {
                jobJpaRepository.findByTenantIdAndJobId(tenantId, frameIndex.jobId())
                    .ifPresent(entity::setProcessingJob);
            }
            entity.setExtractionMethod(mapExtractionMethod(frameIndex.analysisType()));
            entity.setTotalFrames(frameIndex.frameCount());
            entity.setQualityScore(frameIndex.confidence());
            entity.setFrames(frameIndex.frames().stream()
                .map(frame -> new com.ghatana.datacloud.entity.media.FrameIndex.FrameData(
                    frame.timestampMs(),
                    null,
                    0,
                    frame.detections().stream().mapToDouble(FrameIndex.Detection::confidence).average().orElse(0.0),
                    frame.detections().stream().map(FrameIndex.Detection::label).toList(),
                    List.of(),
                    Map.of(),
                    Map.of()))
                .toList());
            entity.setScenes(frameIndex.events().stream()
                .map(event -> new com.ghatana.datacloud.entity.media.FrameIndex.SceneBoundary(
                    event.startMs(),
                    event.endMs(),
                    event.eventType(),
                    event.confidence(),
                    List.of(),
                    Map.of("description", event.description())))
                .toList());
            entity.setDetectedObjects(Map.of());
            Map<String, Object> processingMetadata = new HashMap<>();
            processingMetadata.putAll(frameIndex.metadata());
            processingMetadata.put("durationMs", frameIndex.durationMs());
            processingMetadata.put("createdBy", frameIndex.createdBy());
            entity.setProcessingMetadata(processingMetadata);
            entity.setCreatedAt(frameIndex.createdAt());
            entity.setUpdatedAt(Instant.now());

            frameIndexJpaRepository.save(entity);
            return frameIndex;
        }).then(saved -> updateFrameIndexId(artifactId, tenantId, saved.frameIndexId(), saved.createdBy())
            .map(updated -> saved));
    }

    @Override
    public Promise<Optional<FrameIndex>> findFrameIndex(String artifactId, String tenantId) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> artifactOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (artifactOpt.isEmpty()) {
                return Optional.empty();
            }

            if (frameIndexJpaRepository != null) {
                return frameIndexJpaRepository.findByTenantIdAndMediaArtifactId(tenantId, artifactOpt.get().getId()).stream()
                    .findFirst()
                    .map(this::toFrameIndexRecord);
            }

            Map<String, String> metadata = artifactOpt.get().getMetadata();
            if (metadata != null && metadata.containsKey("frameIndexId")) {
                String createdBy = metadata.getOrDefault("updatedBy", metadata.getOrDefault("createdBy", "media-artifact-service"));
                return Optional.of(new FrameIndex(
                    metadata.get("frameIndexId"),
                    artifactId,
                    tenantId,
                    metadata.get("processingJobId"),
                    FrameIndex.AnalysisType.OBJECT_DETECTION,
                    List.of(),
                    List.of(),
                    List.of(),
                    0.0,
                    0,
                    0L,
                    Map.of(),
                    artifactOpt.get().getUpdatedAt() != null ? artifactOpt.get().getUpdatedAt() : Instant.now(),
                    createdBy
                ));
            }
            return Optional.empty();
        });
    }

    @Override
    public Promise<Boolean> updateClassification(String artifactId, String tenantId, String classification, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                MediaArtifact.Classification classEnum = mapClassification(classification);
                entity.setClassification(classEnum);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated classification for artifact [{}] to [{}]", artifactId, classification);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateRedactionPolicy(String artifactId, String tenantId, String redactionPolicy, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("redactionPolicy", redactionPolicy);
                metadata.put("updatedBy", updatedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated redaction policy for artifact [{}] to [{}]", artifactId, redactionPolicy);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateExpiresAt(String artifactId, String tenantId, Instant expiresAt, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                entity.setExpiresAt(expiresAt);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated expiration for artifact [{}] to [{}]", artifactId, expiresAt);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateOwnerId(String artifactId, String tenantId, String ownerId, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                entity.setOwnerId(ownerId);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated owner for artifact [{}] to [{}]", artifactId, ownerId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<Boolean> updateSourceSystem(String artifactId, String tenantId, String sourceSystem, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                entity.setSourceSystem(sourceSystem);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Updated source system for artifact [{}] to [{}]", artifactId, sourceSystem);
                return true;
            }
            return false;
        });
    }

    // WS3-6: Atomic job methods

    @Override
    public Promise<MediaProcessingJob> createJob(MediaProcessingJob job) {
        if (jobJpaRepository == null) {
            return Promise.of(job);
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(job.tenantId(), job.artifactId());
            if (entityOpt.isEmpty()) {
                return job;
            }

            com.ghatana.datacloud.entity.media.MediaProcessingJob entity = new com.ghatana.datacloud.entity.media.MediaProcessingJob();
            entity.setJobId(job.jobId());
            entity.setTenantId(job.tenantId());
            entity.setMediaArtifact(entityOpt.get());
            entity.setJobType(mapEntityJobType(job.jobType()));
            entity.setStatus(mapEntityJobStatus(job.status()));
            entity.setParameters(new HashMap<>(job.parameters()));
            entity.setProgressPercentage(job.progress());
            entity.setRequestedBy(job.createdBy());
            entity.setCreatedAt(job.queuedAt());
            entity.setQueuedAt(job.queuedAt());
            entity.setUpdatedAt(job.queuedAt());
            entity.setStartedAt(job.startedAt());
            entity.setCompletedAt(job.completedAt());
            entity.setFailureCode(job.failureCode());
            entity.setFailureReason(job.failureReason());
            entity.setRetryable(job.retryable());
            entity.setRetryCount(Math.max(0, job.attempt() - 1));
            entity.setMaxRetries(job.maxAttempts());
            entity.setProcessorId(job.processorId());
            entity.setProcessorVersion(job.processorVersion());
            entity.setInputArtifactId(job.inputArtifactId());
            entity.setOutputArtifactIds(job.outputArtifactIds());
            entity.setTraceId(job.traceId());
            entity.setRequestId(job.requestId());
            entity.setCancelledBy(job.cancelledBy());
            entity.setCancelledAt(job.cancelledAt());

            com.ghatana.datacloud.entity.media.MediaProcessingJob saved = jobJpaRepository.save(entity);
            return toJobRecord(saved);
        });
    }

    @Override
    public Promise<Boolean> transitionJobState(String jobId, String tenantId, String newState, String updatedBy) {
        if (jobJpaRepository == null) {
            return Promise.of(true);
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<com.ghatana.datacloud.entity.media.MediaProcessingJob> entityOpt =
                jobJpaRepository.findByTenantIdAndJobId(tenantId, jobId);
            if (entityOpt.isEmpty()) {
                return false;
            }

            com.ghatana.datacloud.entity.media.MediaProcessingJob entity = entityOpt.get();
            entity.setStatus(mapEntityJobStatus(newState));
            entity.setUpdatedAt(Instant.now());
            if (entity.getStartedAt() == null && "PROCESSING".equals(newState)) {
                entity.setStartedAt(Instant.now());
            }
            if ("COMPLETED".equals(newState) || "FAILED".equals(newState) || "CANCELLED".equals(newState)) {
                entity.setCompletedAt(Instant.now());
            }
            jobJpaRepository.save(entity);
            return true;
        });
    }

    @Override
    public Promise<Boolean> attachTranscript(String artifactId, String tenantId, String transcriptId, String updatedBy) {
        return updateTranscriptId(artifactId, tenantId, transcriptId, updatedBy);
    }

    @Override
    public Promise<Boolean> attachFrameIndex(String artifactId, String tenantId, String frameIndexId, String updatedBy) {
        return updateFrameIndexId(artifactId, tenantId, frameIndexId, updatedBy);
    }

    @Override
    public Promise<Boolean> markFailed(String jobId, String tenantId, String failureCode, String failureReason, String updatedBy) {
        if (jobJpaRepository == null) {
            return Promise.of(true);
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<com.ghatana.datacloud.entity.media.MediaProcessingJob> entityOpt =
                jobJpaRepository.findByTenantIdAndJobId(tenantId, jobId);
            if (entityOpt.isEmpty()) {
                return false;
            }

            com.ghatana.datacloud.entity.media.MediaProcessingJob entity = entityOpt.get();
            entity.setStatus(com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.FAILED);
            entity.setFailureCode(failureCode);
            entity.setFailureReason(failureReason);
            entity.setCompletedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            jobJpaRepository.save(entity);
            return true;
        });
    }

    @Override
    public Promise<Boolean> markCancelled(String jobId, String tenantId, String cancelledBy) {
        if (jobJpaRepository == null) {
            return Promise.of(true);
        }

        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<com.ghatana.datacloud.entity.media.MediaProcessingJob> entityOpt =
                jobJpaRepository.findByTenantIdAndJobId(tenantId, jobId);
            if (entityOpt.isEmpty()) {
                return false;
            }

            com.ghatana.datacloud.entity.media.MediaProcessingJob entity = entityOpt.get();
            entity.setStatus(com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.CANCELLED);
            entity.setCancelledBy(cancelledBy);
            entity.setCancelledAt(Instant.now());
            entity.setCompletedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());
            jobJpaRepository.save(entity);
            return true;
        });
    }

    @Override
    public Promise<Boolean> markRetentionExpired(String artifactId, String tenantId, String updatedBy) {
        return Promise.ofBlocking(blockingExecutor, () -> {
            Optional<MediaArtifact> entityOpt = artifactJpaRepository.findByTenantIdAndArtifactId(tenantId, artifactId);
            if (entityOpt.isPresent()) {
                MediaArtifact entity = entityOpt.get();
                Map<String, String> metadata = entity.getMetadata();
                if (metadata == null) {
                    metadata = new java.util.HashMap<>();
                }
                metadata.put("retentionExpired", "true");
                metadata.put("updatedBy", updatedBy);
                entity.setMetadata(metadata);
                entity.setUpdatedAt(Instant.now());
                artifactJpaRepository.save(entity);
                log.debug("Marked artifact [{}] as retention expired", artifactId);
                return true;
            }
            return false;
        });
    }

    @Override
    public Promise<List<MediaProcessingJob>> findJobsByState(String state, String tenantId, int limit) {
        if (jobJpaRepository == null) {
            return Promise.of(List.of());
        }

        return Promise.ofBlocking(blockingExecutor, () -> jobJpaRepository
            .findByTenantIdAndStatus(tenantId, mapEntityJobStatus(state), PageRequest.of(0, limit)).content().stream()
            .limit(limit)
            .map(this::toJobRecord)
            .toList());
    }

    // ============ Mapping Methods ============

    private MediaArtifact toEntity(MediaArtifactRecord record) {
        MediaArtifact entity = new MediaArtifact();
        entity.setArtifactId(record.artifactId());
        entity.setTenantId(record.tenantId());
        entity.setAgentId(record.agentId());
        entity.setMediaType(record.mediaType());
        entity.setStorageUri(record.storageUri());
        entity.setSizeBytes(record.sizeBytes());
        entity.setChecksum(record.checksum());
        entity.setDurationMs(record.durationMs());
        entity.setOriginToolId(record.originToolId());
        entity.setCorrelationId(record.correlationId());
        entity.setClassification(mapClassification(record.contentClass()));
        entity.setConsentStatus(mapConsentStatus(record.consentStatus()));
        entity.setRetentionPolicy(record.retentionPolicy());
        entity.setExpiresAt(record.retentionUntil());
        entity.setOwnerId(record.ownerId());
        entity.setSourceSystem(record.sourceSystem());
        entity.setCreatedAt(record.createdAt());
        entity.setUpdatedAt(record.updatedAt());

        // Store governance fields in metadata
        Map<String, String> metadata = new java.util.HashMap<>(record.metadata());
        metadata.put("status", record.status());
        metadata.put("processingState", record.processingState());
        metadata.put("privacyClass", record.privacyClass());
        metadata.put("storageProvider", record.storageProvider());
        metadata.put("lineageRef", record.lineageRef());
        metadata.put("policyContext", record.policyContext());
        metadata.put("redactionState", record.redactionState());
        if (record.processingJobId() != null) {
            metadata.put("processingJobId", record.processingJobId());
        }
        if (record.transcriptId() != null) {
            metadata.put("transcriptId", record.transcriptId());
        }
        if (record.frameIndexId() != null) {
            metadata.put("frameIndexId", record.frameIndexId());
        }
        if (record.lastError() != null) {
            metadata.put("lastError", record.lastError());
        }
        if (record.createdBy() != null) {
            metadata.put("createdBy", record.createdBy());
        }
        if (record.updatedBy() != null) {
            metadata.put("updatedBy", record.updatedBy());
        }
        if (record.deletedAt() != null) {
            metadata.put("deletedAt", record.deletedAt().toString());
        }
        entity.setMetadata(metadata);

        return entity;
    }

    private MediaArtifactRecord toRecord(MediaArtifact entity) {
        Map<String, String> metadata = entity.getMetadata() != null ? entity.getMetadata() : Map.of();

        return MediaArtifactRecord.create(
            entity.getTenantId(),
            entity.getAgentId(),
            entity.getMediaType(),
            entity.getStorageUri(),
            entity.getSizeBytes(),
            entity.getChecksum(),
            entity.getDurationMs(),
            entity.getOriginToolId(),
            entity.getCorrelationId(),
            metadata.getOrDefault("status", "ACTIVE"),
            metadata.getOrDefault("processingState", "REGISTERED"),
            entity.getClassification() != null ? entity.getClassification().name() : null,
            metadata.getOrDefault("privacyClass", "INTERNAL"),
            entity.getConsentStatus() != null ? mapConsentStatusToString(entity.getConsentStatus()) : null,
            entity.getRetentionPolicy(),
            entity.getExpiresAt(),
            metadata.get("storageProvider"),
            metadata.get("lineageRef"),
            metadata.get("policyContext"),
            metadata.getOrDefault("redactionState", "NONE"),
            entity.getOwnerId(),
            entity.getSourceSystem(),
            metadata,
            metadata.getOrDefault("createdBy", entity.getAgentId())
        );
    }

    private MediaProcessingJob toJobRecord(com.ghatana.datacloud.entity.media.MediaProcessingJob entityJob) {
        String artifactId = entityJob.getMediaArtifact() != null
            ? entityJob.getMediaArtifact().getArtifactId()
            : "unknown";
        String tenantId = entityJob.getTenantId();
        Map<String, String> parameters = new HashMap<>();
        if (entityJob.getParameters() != null) {
            entityJob.getParameters().forEach((key, value) -> parameters.put(key, value == null ? "" : String.valueOf(value)));
        }

        return new MediaProcessingJob(
            entityJob.getJobId(),
            artifactId,
            tenantId,
            mapApiJobType(entityJob.getJobType()),
            mapApiJobStatus(entityJob.getStatus()),
            Map.copyOf(parameters),
            entityJob.getResults() != null && entityJob.getResults().containsKey("resultId")
                ? String.valueOf(entityJob.getResults().get("resultId"))
                : null,
            entityJob.getFailureReason(),
            entityJob.getProgressPercentage() != null ? entityJob.getProgressPercentage() : 0,
            entityJob.getQueuedAt() != null ? entityJob.getQueuedAt() : entityJob.getCreatedAt(),
            entityJob.getStartedAt(),
            entityJob.getCompletedAt(),
            (entityJob.getRetryCount() != null ? entityJob.getRetryCount() : 0) + 1,
            entityJob.getMaxRetries() != null ? entityJob.getMaxRetries() : 1,
            entityJob.getProcessorId(),
            entityJob.getProcessorVersion(),
            entityJob.getInputArtifactId(),
            entityJob.getOutputArtifactIds() != null ? entityJob.getOutputArtifactIds() : List.of(),
            entityJob.getTraceId(),
            entityJob.getRequestId(),
            entityJob.getFailureCode(),
            entityJob.getFailureReason(),
            entityJob.getRetryable() == null || entityJob.getRetryable(),
            entityJob.getCancelledBy(),
            entityJob.getCancelledAt(),
            entityJob.getRequestedBy()
        );
    }

    private MediaProcessingJob.JobType mapApiJobType(com.ghatana.datacloud.entity.media.MediaProcessingJob.JobType type) {
        return switch (type) {
            case TRANSCRIPTION -> MediaProcessingJob.JobType.TRANSCRIPTION;
            case VISION_ANALYSIS, FRAME_EXTRACTION, VIDEO_ANALYSIS -> MediaProcessingJob.JobType.VISION_ANALYSIS;
            default -> MediaProcessingJob.JobType.MULTIMODAL_INDEXING;
        };
    }

    private MediaProcessingJob.JobStatus mapApiJobStatus(com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus status) {
        return switch (status) {
            case RUNNING -> MediaProcessingJob.JobStatus.PROCESSING;
            case COMPLETED -> MediaProcessingJob.JobStatus.COMPLETED;
            case FAILED, TIMEOUT -> MediaProcessingJob.JobStatus.FAILED;
            case CANCELLED -> MediaProcessingJob.JobStatus.CANCELLED;
            default -> MediaProcessingJob.JobStatus.QUEUED;
        };
    }

    private com.ghatana.datacloud.entity.media.MediaProcessingJob.JobType mapEntityJobType(MediaProcessingJob.JobType type) {
        return switch (type) {
            case TRANSCRIPTION -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobType.TRANSCRIPTION;
            case VISION_ANALYSIS -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobType.VISION_ANALYSIS;
            case MULTIMODAL_INDEXING, RETRY -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobType.CUSTOM_PROCESSING;
        };
    }

    private com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus mapEntityJobStatus(MediaProcessingJob.JobStatus status) {
        return switch (status) {
            case QUEUED -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.QUEUED;
            case PROCESSING -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.RUNNING;
            case COMPLETED -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.COMPLETED;
            case FAILED -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.FAILED;
            case CANCELLED -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.CANCELLED;
        };
    }

    private com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus mapEntityJobStatus(String status) {
        return switch (status) {
            case "QUEUED" -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.QUEUED;
            case "PROCESSING" -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.RUNNING;
            case "COMPLETED" -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.COMPLETED;
            case "FAILED" -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.FAILED;
            case "CANCELLED" -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.CANCELLED;
            default -> com.ghatana.datacloud.entity.media.MediaProcessingJob.JobStatus.PENDING;
        };
    }

    private Transcript toTranscriptRecord(com.ghatana.datacloud.entity.media.Transcript entity) {
        List<Transcript.TranscriptSegment> segments = new ArrayList<>();
        int segmentId = 0;
        for (com.ghatana.datacloud.entity.media.Transcript.TranscriptSegment segment : entity.getSegments()) {
            segments.add(new Transcript.TranscriptSegment(
                segmentId++,
                segment.startTimeMs(),
                segment.endTimeMs(),
                segment.speakerId(),
                segment.text(),
                segment.confidence() != null ? segment.confidence() : 0.0
            ));
        }

        Map<String, String> metadata = new HashMap<>();
        if (entity.getProcessingMetadata() != null) {
            entity.getProcessingMetadata().forEach((key, value) -> metadata.put(key, value == null ? "" : String.valueOf(value)));
        }
        String createdBy = metadata.getOrDefault("createdBy", "media-artifact-service");
        long durationMs = entity.getProcessingMetadata() != null && entity.getProcessingMetadata().containsKey("durationMs")
            ? Long.parseLong(String.valueOf(entity.getProcessingMetadata().get("durationMs")))
            : 0L;

        return new Transcript(
            entity.getTranscriptId(),
            entity.getMediaArtifact() != null ? entity.getMediaArtifact().getArtifactId() : "unknown",
            entity.getTenantId(),
            entity.getProcessingJob() != null ? entity.getProcessingJob().getJobId() : null,
            entity.getLanguageCode() != null ? entity.getLanguageCode() : "en-US",
            segments,
            entity.getFullText() != null ? entity.getFullText() : "",
            entity.getConfidenceScore() != null ? entity.getConfidenceScore() : 0.0,
            durationMs,
            entity.getWordCount() != null ? entity.getWordCount() : 0,
            entity.getSpeakerCount() != null ? entity.getSpeakerCount() : 0,
            Map.copyOf(metadata),
            entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
            createdBy
        );
    }

    private FrameIndex toFrameIndexRecord(com.ghatana.datacloud.entity.media.FrameIndex entity) {
        List<FrameIndex.Frame> frames = entity.getFrames().stream()
            .map(frame -> new FrameIndex.Frame(
                frame.timestampMs(),
                frame.detectedObjects().stream()
                    .map(objectClass -> new FrameIndex.Detection(objectClass, frame.confidence(), List.of()))
                    .toList()))
            .toList();

        List<FrameIndex.Label> labels = new ArrayList<>();
        if (entity.getDetectedObjects() != null) {
            entity.getDetectedObjects().forEach((label, detections) -> {
                double avgConfidence = detections.stream()
                    .map(com.ghatana.datacloud.entity.media.FrameIndex.ObjectDetection::confidence)
                    .filter(Objects::nonNull)
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
                List<Long> timestamps = detections.stream()
                    .map(com.ghatana.datacloud.entity.media.FrameIndex.ObjectDetection::timestampMs)
                    .toList();
                labels.add(new FrameIndex.Label(label, detections.size(), avgConfidence, timestamps));
            });
        }

        List<FrameIndex.Event> events = entity.getScenes().stream()
            .map(scene -> new FrameIndex.Event(
                scene.sceneType(),
                scene.startTimeMs(),
                scene.endTimeMs(),
                scene.characteristics().containsKey("description") ? String.valueOf(scene.characteristics().get("description")) : scene.sceneType(),
                scene.confidence()))
            .toList();

        Map<String, String> metadata = new HashMap<>();
        if (entity.getProcessingMetadata() != null) {
            entity.getProcessingMetadata().forEach((key, value) -> metadata.put(key, value == null ? "" : String.valueOf(value)));
        }
        String createdBy = metadata.getOrDefault("createdBy", "media-artifact-service");
        long durationMs = entity.getProcessingMetadata() != null && entity.getProcessingMetadata().containsKey("durationMs")
            ? Long.parseLong(String.valueOf(entity.getProcessingMetadata().get("durationMs")))
            : 0L;

        return new FrameIndex(
            entity.getFrameIndexId(),
            entity.getMediaArtifact() != null ? entity.getMediaArtifact().getArtifactId() : "unknown",
            entity.getTenantId(),
            entity.getProcessingJob() != null ? entity.getProcessingJob().getJobId() : null,
            mapAnalysisType(entity.getExtractionMethod()),
            frames,
            labels,
            events,
            entity.getQualityScore() != null ? entity.getQualityScore() : 0.0,
            entity.getTotalFrames() != null ? entity.getTotalFrames() : frames.size(),
            durationMs,
            Map.copyOf(metadata),
            entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now(),
            createdBy
        );
    }

    private FrameIndex.AnalysisType mapAnalysisType(com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod method) {
        return switch (method) {
            case SCENE_CHANGE -> FrameIndex.AnalysisType.SCENE_RECOGNITION;
            case MOTION_BASED -> FrameIndex.AnalysisType.MOTION_DETECTION;
            case CONTENT_AWARE -> FrameIndex.AnalysisType.COMPREHENSIVE;
            case CUSTOM -> FrameIndex.AnalysisType.TEXT_EXTRACTION;
            default -> FrameIndex.AnalysisType.OBJECT_DETECTION;
        };
    }

    private com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod mapExtractionMethod(FrameIndex.AnalysisType analysisType) {
        return switch (analysisType) {
            case SCENE_RECOGNITION -> com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod.SCENE_CHANGE;
            case MOTION_DETECTION -> com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod.MOTION_BASED;
            case COMPREHENSIVE -> com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod.CONTENT_AWARE;
            case FACIAL_RECOGNITION, TEXT_EXTRACTION -> com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod.CUSTOM;
            default -> com.ghatana.datacloud.entity.media.FrameIndex.ExtractionMethod.KEYFRAME;
        };
    }

    private MediaArtifact.Classification mapClassification(String classification) {
        if (classification == null) return MediaArtifact.Classification.INTERNAL;
        try {
            return MediaArtifact.Classification.valueOf(classification);
        } catch (IllegalArgumentException e) {
            return MediaArtifact.Classification.INTERNAL;
        }
    }

    private MediaArtifact.ConsentStatus mapConsentStatus(String consentStatus) {
        if (consentStatus == null) return null;
        return switch (consentStatus) {
            case "GRANTED" -> MediaArtifact.ConsentStatus.CONSENTED;
            case "PENDING" -> MediaArtifact.ConsentStatus.PENDING;
            case "DENIED" -> MediaArtifact.ConsentStatus.REVOKED;
            case "NOT_REQUIRED" -> MediaArtifact.ConsentStatus.NONE;
            default -> null;
        };
    }

    private String mapConsentStatusToString(MediaArtifact.ConsentStatus status) {
        if (status == null) return null;
        return switch (status) {
            case CONSENTED -> "GRANTED";
            case PENDING -> "PENDING";
            case REVOKED -> "DENIED";
            case NONE -> "NOT_REQUIRED";
            case EXPIRED -> "DENIED";
        };
    }
}
