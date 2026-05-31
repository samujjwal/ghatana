/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
                record.createdAt()
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
                record.createdAt()
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

    private static String key(String artifactId, String tenantId) {
        return tenantId + ":" + artifactId;
    }
}
