package com.ghatana.audio.video.infrastructure.persistence.service;

import com.ghatana.audio.video.infrastructure.persistence.entity.AudioFileEntity;
import com.ghatana.audio.video.infrastructure.persistence.repository.AudioFileRepository;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @doc.type class
 * @doc.purpose Async service for AudioFile operations.
 *              Follows AEP pattern - service layer is async,
 *              repository layer is synchronous.
 * @doc.layer infrastructure
 * @doc.pattern Service
 */
public class AudioFileService {

    private static final Logger LOG = LoggerFactory.getLogger(AudioFileService.class);

    private final AudioFileRepository repository;
    private final Eventloop eventloop;
    private final ExecutorService dbExecutor;

    public AudioFileService(AudioFileRepository repository, Eventloop eventloop) {
        this(repository, eventloop, Executors.newVirtualThreadPerTaskExecutor());
    }

    public AudioFileService(AudioFileRepository repository, Eventloop eventloop, ExecutorService dbExecutor) {
        this.repository = repository;
        this.eventloop = eventloop;
        this.dbExecutor = dbExecutor;
    }

    /**
     * Save an audio file entity.
     */
    public Promise<AudioFileEntity> save(String tenantId, AudioFileEntity entity) {
        return Promise.ofBlocking(dbExecutor, () -> repository.save(tenantId, entity))
            .whenResult(saved -> LOG.debug("AudioFile saved: tenantId={}, id={}", tenantId, saved.getId()))
            .whenException(e -> LOG.error("Failed to save AudioFile: tenantId={}", tenantId, e));
    }

    /**
     * Find audio file by ID.
     */
    public Promise<Optional<AudioFileEntity>> findById(String tenantId, UUID id) {
        return Promise.ofBlocking(dbExecutor, () -> repository.findById(tenantId, id))
            .whenException(e -> LOG.error("Failed to find AudioFile: tenantId={}, id={}", tenantId, id, e));
    }

    /**
     * Find all audio files for a tenant.
     */
    public Promise<List<AudioFileEntity>> findByTenantId(String tenantId) {
        return Promise.ofBlocking(dbExecutor, () -> repository.findByTenantId(tenantId))
            .whenException(e -> LOG.error("Failed to find AudioFiles: tenantId={}", tenantId, e));
    }

    /**
     * Find audio files by user ID.
     */
    public Promise<List<AudioFileEntity>> findByUserId(String tenantId, UUID userId) {
        return Promise.ofBlocking(dbExecutor, () -> repository.findByUserId(tenantId, userId))
            .whenException(e -> LOG.error("Failed to find AudioFiles by user: tenantId={}, userId={}", tenantId, userId, e));
    }

    /**
     * Find audio files by status.
     */
    public Promise<List<AudioFileEntity>> findByStatus(String tenantId, AudioFileEntity.ProcessingStatus status) {
        return Promise.ofBlocking(dbExecutor, () -> repository.findByStatus(tenantId, status))
            .whenException(e -> LOG.error("Failed to find AudioFiles by status: tenantId={}, status={}", tenantId, status, e));
    }

    /**
     * Soft delete an audio file.
     */
    public Promise<Boolean> softDelete(String tenantId, UUID id) {
        return Promise.ofBlocking(dbExecutor, () -> repository.softDelete(tenantId, id))
            .whenResult(deleted -> LOG.debug("AudioFile soft deleted: tenantId={}, id={}, success={}", tenantId, id, deleted))
            .whenException(e -> LOG.error("Failed to soft delete AudioFile: tenantId={}, id={}", tenantId, id, e));
    }

    /**
     * Hard delete an audio file.
     */
    public Promise<Boolean> hardDelete(String tenantId, UUID id) {
        return Promise.ofBlocking(dbExecutor, () -> repository.hardDelete(tenantId, id))
            .whenResult(deleted -> LOG.debug("AudioFile hard deleted: tenantId={}, id={}, success={}", tenantId, id, deleted))
            .whenException(e -> LOG.error("Failed to hard delete AudioFile: tenantId={}, id={}", tenantId, id, e));
    }

    /**
     * Check if audio file exists.
     */
    public Promise<Boolean> existsById(String tenantId, UUID id) {
        return Promise.ofBlocking(dbExecutor, () -> repository.existsById(tenantId, id));
    }

    /**
     * Count audio files for a tenant.
     */
    public Promise<Long> countByTenantId(String tenantId) {
        return Promise.ofBlocking(dbExecutor, () -> repository.countByTenantId(tenantId))
            .whenException(e -> LOG.error("Failed to count AudioFiles: tenantId={}", tenantId, e));
    }
}
