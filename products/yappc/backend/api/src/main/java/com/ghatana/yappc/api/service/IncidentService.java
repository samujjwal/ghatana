/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.products.yappc.domain.model.Incident;
import com.ghatana.yappc.api.repository.IncidentRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing incidents.
 *
 * @doc.type class
 * @doc.purpose Business logic for incident management
 * @doc.layer service
 * @doc.pattern Service
 */
public class IncidentService {

    private static final Logger logger = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository repository;

    @Inject
    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new incident.
     */
    public Promise<Incident> createIncident(UUID workspaceId, CreateIncidentInput input) {
        logger.info("Creating incident: {} for workspace: {}", input.title(), workspaceId);

        Incident incident = Incident.of(workspaceId, input.title(), input.severity());
        incident.setDescription(input.description());
        incident.setPriority(input.priority());
        if (input.projectId() != null) {
            incident.setProjectId(input.projectId());
        }
        if (input.assigneeId() != null) {
            incident.assignTo(input.assigneeId());
        }
        if (input.category() != null) {
            incident.setCategory(input.category());
        }

        return repository.save(incident);
    }

    /**
     * Gets an incident by ID.
     */
    public Promise<Optional<Incident>> getIncident(UUID workspaceId, UUID incidentId) {
        return repository.findById(workspaceId, incidentId)
            .map(Optional::ofNullable);
    }

    /**
     * Lists incidents for a project.
     */
    public Promise<List<Incident>> listProjectIncidents(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId);
    }

    /**
     * Lists open incidents.
     */
    public Promise<List<Incident>> listOpenIncidents(UUID workspaceId) {
        return repository.findOpen(workspaceId);
    }

    /**
     * Lists open incidents for a project.
     */
    public Promise<List<Incident>> listOpenProjectIncidents(UUID workspaceId, UUID projectId) {
        return repository.findOpenByProject(workspaceId, projectId);
    }

    /**
     * Lists incidents by severity.
     */
    public Promise<List<Incident>> listIncidentsBySeverity(UUID workspaceId, String severity) {
        return repository.findBySeverity(workspaceId, severity);
    }

    /**
     * Starts investigation of an incident.
     */
    public Promise<Incident> startInvestigation(UUID workspaceId, UUID incidentId) {
        logger.info("Starting investigation for incident: {}", incidentId);

        return repository.findById(workspaceId, incidentId)
            .then(incident -> {
                if (incident == null) {
                    return Promise.ofException(new IllegalArgumentException("Incident not found"));
                }
                incident.startInvestigation();
                return repository.save(incident);
            });
    }

    /**
     * Resolves an incident.
     */
    public Promise<Incident> resolveIncident(UUID workspaceId, UUID incidentId, ResolveInput input) {
        logger.info("Resolving incident: {}", incidentId);

        return repository.findById(workspaceId, incidentId)
            .then(incident -> {
                if (incident == null) {
                    return Promise.ofException(new IllegalArgumentException("Incident not found"));
                }
                if (input.rootCause() != null) {
                    incident.setRootCause(input.rootCause());
                }
                incident.resolve(input.resolution());
                return repository.save(incident);
            });
    }

    /**
     * Closes an incident.
     */
    public Promise<Incident> closeIncident(UUID workspaceId, UUID incidentId) {
        logger.info("Closing incident: {}", incidentId);

        return repository.findById(workspaceId, incidentId)
            .then(incident -> {
                if (incident == null) {
                    return Promise.ofException(new IllegalArgumentException("Incident not found"));
                }
                incident.close();
                return repository.save(incident);
            });
    }

    /**
     * Assigns an incident to a user.
     */
    public Promise<Incident> assignIncident(UUID workspaceId, UUID incidentId, UUID assigneeId) {
        return repository.findById(workspaceId, incidentId)
            .then(incident -> {
                if (incident == null) {
                    return Promise.ofException(new IllegalArgumentException("Incident not found"));
                }
                incident.assignTo(assigneeId);
                return repository.save(incident);
            });
    }

    /**
     * Gets incident statistics for a project.
     */
    public Promise<IncidentStats> getIncidentStatistics(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId)
            .map(incidents -> {
                IncidentStats stats = new IncidentStats();
                stats.totalIncidents = incidents.size();
                stats.openCount = incidents.stream().filter(Incident::isOpen).count();
                stats.resolvedCount = incidents.stream()
                    .filter(i -> "RESOLVED".equalsIgnoreCase(i.getStatus())).count();
                stats.criticalCount = incidents.stream()
                    .filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity())).count();
                stats.highCount = incidents.stream()
                    .filter(i -> "HIGH".equalsIgnoreCase(i.getSeverity())).count();
                stats.mttrMs = incidents.stream()
                    .filter(i -> i.getResolvedAt() != null)
                    .mapToLong(Incident::getTimeToResolutionMs)
                    .filter(ms -> ms >= 0)
                    .average()
                    .orElse(0.0);
                return stats;
            });
    }

    /**
     * Deletes an incident.
     */
    public Promise<Void> deleteIncident(UUID workspaceId, UUID incidentId) {
        logger.info("Deleting incident: {} for workspace: {}", incidentId, workspaceId);
        return repository.delete(workspaceId, incidentId);
    }

    // ========== Input Records ==========

    public record CreateIncidentInput(
        UUID projectId,
        String title,
        String description,
        String severity,
        int priority,
        UUID assigneeId,
        String category
    ) {}

    public record ResolveInput(
        String rootCause,
        String resolution
    ) {}

    // ========== Stats ==========

    public static class IncidentStats {
        public int totalIncidents;
        public long openCount;
        public long resolvedCount;
        public long criticalCount;
        public long highCount;
        public double mttrMs;
    }
}
