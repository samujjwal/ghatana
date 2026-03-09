/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import com.ghatana.yappc.api.repository.AlertRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of AlertRepository for development and testing.
 *
 * @doc.type class
 * @doc.purpose In-memory alert storage
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryAlertRepository implements AlertRepository {

    private final Map<UUID, Map<UUID, SecurityAlert>> workspaceAlerts = new ConcurrentHashMap<>();

    @Override
    public Promise<SecurityAlert> save(SecurityAlert alert) {
        if (alert.getId() == null) {
            alert.setId(UUID.randomUUID());
        }
        if (alert.getCreatedAt() == null) {
            alert.setCreatedAt(Instant.now());
        }
        alert.setUpdatedAt(Instant.now());

        workspaceAlerts
            .computeIfAbsent(alert.getWorkspaceId(), k -> new ConcurrentHashMap<>())
            .put(alert.getId(), alert);
        return Promise.of(alert);
    }

    @Override
    public Promise<SecurityAlert> findById(UUID workspaceId, UUID id) {
        return Promise.of(workspaceAlerts.getOrDefault(workspaceId, Map.of()).get(id));
    }

    @Override
    public Promise<List<SecurityAlert>> findByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<SecurityAlert>> findByStatus(UUID workspaceId, String status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> status.equalsIgnoreCase(a.getStatus()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<SecurityAlert>> findBySeverity(UUID workspaceId, String severity) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> severity.equalsIgnoreCase(a.getSeverity()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<SecurityAlert>> findOpen(UUID workspaceId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(SecurityAlert::isOpen)
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<SecurityAlert>> findOpenByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(SecurityAlert::isOpen)
            .filter(a -> Objects.equals(a.getProjectId(), projectId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<SecurityAlert>> findByAssignedTo(UUID workspaceId, UUID userId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getAssignedTo(), userId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<Long> countOpenBySeverity(UUID workspaceId, String severity) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(SecurityAlert::isOpen)
            .filter(a -> severity.equalsIgnoreCase(a.getSeverity()))
            .count());
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        Map<UUID, SecurityAlert> alerts = workspaceAlerts.get(workspaceId);
        if (alerts != null) {
            alerts.remove(id);
        }
        return Promise.of(null);
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.of(workspaceAlerts.getOrDefault(workspaceId, Map.of()).containsKey(id));
    }

    private List<SecurityAlert> getAll(UUID workspaceId) {
        return new ArrayList<>(workspaceAlerts.getOrDefault(workspaceId, Map.of()).values());
    }
}
