/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.yappc.api.repository.SecurityScanRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of SecurityScanRepository for development and testing.
 *
 * @doc.type class
 * @doc.purpose In-memory security scan storage
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemorySecurityScanRepository implements SecurityScanRepository {

    private final Map<UUID, Map<UUID, ScanJob>> workspaceScans = new ConcurrentHashMap<>();

    @Override
    public Promise<ScanJob> save(ScanJob scan) {
        if (scan.getId() == null) {
            scan.setId(UUID.randomUUID());
        }
        if (scan.getCreatedAt() == null) {
            scan.setCreatedAt(Instant.now());
        }
        scan.setUpdatedAt(Instant.now());

        workspaceScans
            .computeIfAbsent(scan.getWorkspaceId(), k -> new ConcurrentHashMap<>())
            .put(scan.getId(), scan);
        return Promise.of(scan);
    }

    @Override
    public Promise<ScanJob> findById(UUID workspaceId, UUID id) {
        return Promise.of(workspaceScans.getOrDefault(workspaceId, Map.of()).get(id));
    }

    @Override
    public Promise<List<ScanJob>> findByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> Objects.equals(s.getProjectId(), projectId))
            .sorted(Comparator.comparing(ScanJob::getCreatedAt).reversed())
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ScanJob>> findByType(UUID workspaceId, ScanType type) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> s.getScanType() == type)
            .sorted(Comparator.comparing(ScanJob::getCreatedAt).reversed())
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ScanJob>> findByStatus(UUID workspaceId, ScanStatus status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> s.getStatus() == status)
            .sorted(Comparator.comparing(ScanJob::getCreatedAt).reversed())
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ScanJob>> findRunning(UUID workspaceId) {
        return findByStatus(workspaceId, ScanStatus.RUNNING);
    }

    @Override
    public Promise<List<ScanJob>> findByProjectAndType(UUID workspaceId, UUID projectId, ScanType type) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> Objects.equals(s.getProjectId(), projectId) && s.getScanType() == type)
            .sorted(Comparator.comparing(ScanJob::getCreatedAt).reversed())
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ScanJob>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> s.getCreatedAt() != null
                && !s.getCreatedAt().isBefore(start)
                && !s.getCreatedAt().isAfter(end))
            .sorted(Comparator.comparing(ScanJob::getCreatedAt).reversed())
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<ScanJob> findLatestByProjectAndType(UUID workspaceId, UUID projectId, ScanType type) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> Objects.equals(s.getProjectId(), projectId) && s.getScanType() == type)
            .max(Comparator.comparing(ScanJob::getCreatedAt))
            .orElse(null));
    }

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, ScanStatus status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(s -> s.getStatus() == status)
            .count());
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        Map<UUID, ScanJob> scans = workspaceScans.get(workspaceId);
        if (scans != null) {
            scans.remove(id);
        }
        return Promise.of(null);
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.of(workspaceScans.getOrDefault(workspaceId, Map.of()).containsKey(id));
    }

    private List<ScanJob> getAll(UUID workspaceId) {
        return new ArrayList<>(workspaceScans.getOrDefault(workspaceId, Map.of()).values());
    }
}
