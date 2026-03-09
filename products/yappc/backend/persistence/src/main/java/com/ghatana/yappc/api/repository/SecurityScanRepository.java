/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ScanJob persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Security scan repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface SecurityScanRepository {

    /** Save a scan job. */
    Promise<ScanJob> save(ScanJob scan);

    /** Find a scan job by ID. */
    Promise<ScanJob> findById(UUID workspaceId, UUID id);

    /** Find scans by project. */
    Promise<List<ScanJob>> findByProject(UUID workspaceId, UUID projectId);

    /** Find scans by type. */
    Promise<List<ScanJob>> findByType(UUID workspaceId, ScanType type);

    /** Find scans by status. */
    Promise<List<ScanJob>> findByStatus(UUID workspaceId, ScanStatus status);

    /** Find running scans. */
    Promise<List<ScanJob>> findRunning(UUID workspaceId);

    /** Find scans by project and type. */
    Promise<List<ScanJob>> findByProjectAndType(UUID workspaceId, UUID projectId, ScanType type);

    /** Find scans within a time range. */
    Promise<List<ScanJob>> findByTimeRange(UUID workspaceId, Instant start, Instant end);

    /** Find latest scan for a project and type. */
    Promise<ScanJob> findLatestByProjectAndType(UUID workspaceId, UUID projectId, ScanType type);

    /** Count scans by status. */
    Promise<Long> countByStatus(UUID workspaceId, ScanStatus status);

    /** Delete a scan. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if a scan exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
