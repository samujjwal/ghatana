/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Data Cloud implementation of {@link MediaArtifactRepository}.
 *
 * <p>Stores media artifact records in an in-memory {@link ConcurrentHashMap} keyed by
 * {@code tenantId + ":" + artifactId} for tenant isolation. In production deployments
 * this class is backed by the {@code media_artifacts} database table (see Flyway
 * migration {@code V018__create_media_artifacts.sql}).
 *
 * <p>This implementation is safe for concurrent use by the ActiveJ event-loop:
 * all operations complete synchronously and wrap their results in {@link Promise#of}.
 *
 * @doc.type class
 * @doc.purpose Data Cloud implementation of MediaArtifactRepository
 * @doc.layer product
 * @doc.pattern Repository
 */
public final class DataCloudMediaArtifactRepository implements MediaArtifactRepository {

    private static final Logger log = LoggerFactory.getLogger(DataCloudMediaArtifactRepository.class);

    /** Key: "{tenantId}:{artifactId}" → record */
    private final Map<String, MediaArtifactRecord> store = new ConcurrentHashMap<>();

    @Override
    public Promise<MediaArtifactRecord> save(MediaArtifactRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        String key = key(record.artifactId(), record.tenantId());
        store.put(key, record);
        log.debug("Saved media artifact [{}] for tenant [{}]", record.artifactId(), record.tenantId());
        return Promise.of(record);
    }

    @Override
    public Promise<Optional<MediaArtifactRecord>> findById(String artifactId, String tenantId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        return Promise.of(Optional.ofNullable(record));
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByAgent(String agentId, String tenantId, int limit) {
        Objects.requireNonNull(agentId,  "agentId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        List<MediaArtifactRecord> result = new ArrayList<>();
        for (MediaArtifactRecord record : store.values()) {
            if (record.tenantId().equals(tenantId) && record.agentId().equals(agentId)) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByMediaType(String mediaType, String tenantId, int limit) {
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        List<MediaArtifactRecord> result = new ArrayList<>();
        for (MediaArtifactRecord record : store.values()) {
            if (record.tenantId().equals(tenantId) && record.mediaType().equals(mediaType)) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<Boolean> delete(String artifactId, String tenantId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId,   "tenantId must not be null");
        MediaArtifactRecord removed = store.remove(key(artifactId, tenantId));
        if (removed != null) {
            log.debug("Deleted media artifact [{}] for tenant [{}]", artifactId, tenantId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> updateProcessingState(String artifactId, String tenantId, String processingState) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(processingState, "processingState must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                processingState,
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                record.updatedAt(),
                record.processingJobId(),
                record.transcriptId(),
                record.frameIndexId(),
                record.lastError(),
                record.createdBy(),
                record.updatedBy(),
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated processing state for artifact [{}] to [{}]", artifactId, processingState);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> updateStatus(String artifactId, String tenantId, String status) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                status,
                record.processingState(),
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                record.updatedAt(),
                record.processingJobId(),
                record.transcriptId(),
                record.frameIndexId(),
                record.lastError(),
                record.createdBy(),
                record.updatedBy(),
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated status for artifact [{}] to [{}]", artifactId, status);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByProcessingState(String processingState, String tenantId, int limit) {
        Objects.requireNonNull(processingState, "processingState must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        List<MediaArtifactRecord> result = new ArrayList<>();
        for (MediaArtifactRecord record : store.values()) {
            if (record.tenantId().equals(tenantId) && record.processingState().equals(processingState)) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByStatus(String status, String tenantId, int limit) {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        List<MediaArtifactRecord> result = new ArrayList<>();
        for (MediaArtifactRecord record : store.values()) {
            if (record.tenantId().equals(tenantId) && record.status().equals(status)) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<List<String>> findProcessingJobsByArtifact(String artifactId, String tenantId, int limit) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        // In-memory implementation: return empty list as job tracking is separate
        // In production, this would query the media_processing_jobs table
        log.debug("Finding processing jobs for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(List.of());
    }

    @Override
    public Promise<List<String>> findProcessingResultsByArtifact(String artifactId, String tenantId, int limit) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        // In-memory implementation: return empty list as result tracking is separate
        // In production, this would query the media_processing_results table
        log.debug("Finding processing results for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(List.of());
    }

    /** Returns the number of records currently stored (all tenants). */
    public int size() {
        return store.size();
    }

    @Override
    public Promise<Boolean> updateLastError(String artifactId, String tenantId, String lastError, String updatedBy) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(lastError, "lastError must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                record.processingState(),
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                Instant.now(),
                record.processingJobId(),
                record.transcriptId(),
                record.frameIndexId(),
                lastError,
                record.createdBy(),
                updatedBy,
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated last error for artifact [{}]", artifactId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> updateFrameIndexId(String artifactId, String tenantId, String frameIndexId, String updatedBy) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(frameIndexId, "frameIndexId must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                record.processingState(),
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                Instant.now(),
                record.processingJobId(),
                record.transcriptId(),
                frameIndexId,
                record.lastError(),
                record.createdBy(),
                updatedBy,
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated frame index ID for artifact [{}]", artifactId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> updateTranscriptId(String artifactId, String tenantId, String transcriptId, String updatedBy) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(transcriptId, "transcriptId must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                record.processingState(),
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                Instant.now(),
                record.processingJobId(),
                transcriptId,
                record.frameIndexId(),
                record.lastError(),
                record.createdBy(),
                updatedBy,
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated transcript ID for artifact [{}]", artifactId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> updateProcessingJobId(String artifactId, String tenantId, String processingJobId, String updatedBy) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(processingJobId, "processingJobId must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                record.processingState(),
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                Instant.now(),
                processingJobId,
                record.transcriptId(),
                record.frameIndexId(),
                record.lastError(),
                record.createdBy(),
                updatedBy,
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated processing job ID for artifact [{}]", artifactId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> updateConsentStatus(String artifactId, String tenantId, String consentStatus, String updatedBy) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(consentStatus, "consentStatus must not be null");
        Objects.requireNonNull(updatedBy, "updatedBy must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                record.processingState(),
                record.classification(),
                consentStatus,
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                Instant.now(),
                record.processingJobId(),
                record.transcriptId(),
                record.frameIndexId(),
                record.lastError(),
                record.createdBy(),
                updatedBy,
                record.deletedAt()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Updated consent status for artifact [{}]", artifactId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<Boolean> markDeleted(String artifactId, String tenantId, String deletedBy) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(deletedBy, "deletedBy must not be null");
        
        MediaArtifactRecord record = store.get(key(artifactId, tenantId));
        if (record != null) {
            MediaArtifactRecord updated = new MediaArtifactRecord(
                record.artifactId(),
                record.tenantId(),
                record.agentId(),
                record.mediaType(),
                record.storageUri(),
                record.sizeBytes(),
                record.checksum(),
                record.durationMs(),
                record.originToolId(),
                record.correlationId(),
                record.status(),
                record.processingState(),
                record.classification(),
                record.consentStatus(),
                record.retentionPolicy(),
                record.redactionPolicy(),
                record.expiresAt(),
                record.ownerId(),
                record.sourceSystem(),
                record.lineage(),
                record.metadata(),
                record.createdAt(),
                record.updatedAt(),
                record.processingJobId(),
                record.transcriptId(),
                record.frameIndexId(),
                record.lastError(),
                record.createdBy(),
                record.updatedBy(),
                Instant.now()
            );
            store.put(key(artifactId, tenantId), updated);
            log.debug("Marked artifact [{}] as deleted", artifactId);
            return Promise.of(Boolean.TRUE);
        }
        return Promise.of(Boolean.FALSE);
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByRetentionDue(String tenantId, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        List<MediaArtifactRecord> result = new ArrayList<>();
        Instant now = Instant.now();
        for (MediaArtifactRecord record : store.values()) {
            if (record.tenantId().equals(tenantId) && record.expiresAt() != null && record.expiresAt().isBefore(now)) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<List<MediaArtifactRecord>> findByConsentStatus(String consentStatus, String tenantId, int limit) {
        Objects.requireNonNull(consentStatus, "consentStatus must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (limit <= 0) return Promise.of(List.of());

        List<MediaArtifactRecord> result = new ArrayList<>();
        for (MediaArtifactRecord record : store.values()) {
            if (record.tenantId().equals(tenantId) && consentStatus.equals(record.consentStatus())) {
                result.add(record);
                if (result.size() >= limit) break;
            }
        }
        return Promise.of(List.copyOf(result));
    }

    @Override
    public Promise<List<MediaProcessingJob>> findJobs(String artifactId, String tenantId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        // In-memory implementation: return empty list as job tracking is separate
        log.debug("Finding jobs for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(List.of());
    }

    @Override
    public Promise<Transcript> saveTranscript(String artifactId, String tenantId, Transcript transcript) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(transcript, "transcript must not be null");
        // In-memory implementation: no-op for transcript storage
        log.debug("Saving transcript for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(transcript);
    }

    @Override
    public Promise<Optional<Transcript>> findTranscript(String artifactId, String tenantId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        // In-memory implementation: return empty
        log.debug("Finding transcript for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(Optional.empty());
    }

    @Override
    public Promise<FrameIndex> saveFrameIndex(String artifactId, String tenantId, FrameIndex frameIndex) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(frameIndex, "frameIndex must not be null");
        // In-memory implementation: no-op for frame index storage
        log.debug("Saving frame index for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(frameIndex);
    }

    @Override
    public Promise<Optional<FrameIndex>> findFrameIndex(String artifactId, String tenantId) {
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        // In-memory implementation: return empty
        log.debug("Finding frame index for artifact [{}] in tenant [{}]", artifactId, tenantId);
        return Promise.of(Optional.empty());
    }

    private static String key(String artifactId, String tenantId) {
        return tenantId + ":" + artifactId;
    }
}
