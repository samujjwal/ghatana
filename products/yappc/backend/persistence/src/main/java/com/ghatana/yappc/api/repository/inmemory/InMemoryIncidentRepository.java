/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.products.yappc.domain.model.Incident;
import com.ghatana.yappc.api.repository.IncidentRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of IncidentRepository for development and testing.
 *
 * @doc.type class
 * @doc.purpose In-memory incident storage
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryIncidentRepository implements IncidentRepository {

    private final Map<UUID, Map<UUID, Incident>> workspaceIncidents = new ConcurrentHashMap<>();

    @Override
    public Promise<Incident> save(Incident incident) {
        if (incident.getId() == null) {
            incident.setId(UUID.randomUUID());
        }
        if (incident.getCreatedAt() == null) {
            incident.setCreatedAt(Instant.now());
        }
        incident.setUpdatedAt(Instant.now());

        workspaceIncidents
            .computeIfAbsent(incident.getWorkspaceId(), k -> new ConcurrentHashMap<>())
            .put(incident.getId(), incident);
        return Promise.of(incident);
    }

    @Override
    public Promise<Incident> findById(UUID workspaceId, UUID id) {
        Map<UUID, Incident> incidents = workspaceIncidents.getOrDefault(workspaceId, Map.of());
        return Promise.of(incidents.get(id));
    }

    @Override
    public Promise<List<Incident>> findByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(i -> Objects.equals(i.getProjectId(), projectId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<Incident>> findByStatus(UUID workspaceId, String status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(i -> status.equalsIgnoreCase(i.getStatus()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<Incident>> findBySeverity(UUID workspaceId, String severity) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(i -> severity.equalsIgnoreCase(i.getSeverity()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<Incident>> findOpen(UUID workspaceId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(Incident::isOpen)
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<Incident>> findOpenByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(Incident::isOpen)
            .filter(i -> Objects.equals(i.getProjectId(), projectId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<Incident>> findByTimeRange(UUID workspaceId, Instant start, Instant end) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(i -> i.getCreatedAt() != null
                && !i.getCreatedAt().isBefore(start)
                && !i.getCreatedAt().isAfter(end))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<Long> countOpen(UUID workspaceId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(Incident::isOpen)
            .count());
    }

    @Override
    public Promise<Long> countBySeverity(UUID workspaceId, String severity) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(i -> severity.equalsIgnoreCase(i.getSeverity()))
            .count());
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        Map<UUID, Incident> incidents = workspaceIncidents.get(workspaceId);
        if (incidents != null) {
            incidents.remove(id);
        }
        return Promise.of(null);
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        Map<UUID, Incident> incidents = workspaceIncidents.getOrDefault(workspaceId, Map.of());
        return Promise.of(incidents.containsKey(id));
    }

    private List<Incident> getAll(UUID workspaceId) {
        return new ArrayList<>(workspaceIncidents.getOrDefault(workspaceId, Map.of()).values());
    }
}
