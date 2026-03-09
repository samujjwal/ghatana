#!/usr/bin/env python3
"""Rewrite repository interfaces, InMemory implementations, and services to use L3 entity API."""

import os

BASE = "products/yappc/backend/api/src/main/java/com/ghatana/yappc/api"

files = {}

# ============================================================
# 1. REPOSITORY INTERFACES
# ============================================================

files[f"{BASE}/repository/IncidentRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.Incident;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for Incident persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Incident repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface IncidentRepository {

    /** Save an incident. */
    Promise<Incident> save(Incident incident);

    /** Find an incident by ID within a workspace. */
    Promise<Incident> findById(UUID workspaceId, UUID id);

    /** Find incidents by project. */
    Promise<List<Incident>> findByProject(UUID workspaceId, UUID projectId);

    /** Find incidents by status. */
    Promise<List<Incident>> findByStatus(UUID workspaceId, String status);

    /** Find incidents by severity. */
    Promise<List<Incident>> findBySeverity(UUID workspaceId, String severity);

    /** Find open incidents. */
    Promise<List<Incident>> findOpen(UUID workspaceId);

    /** Find open incidents by project. */
    Promise<List<Incident>> findOpenByProject(UUID workspaceId, UUID projectId);

    /** Find incidents within a time range. */
    Promise<List<Incident>> findByTimeRange(UUID workspaceId, Instant start, Instant end);

    /** Count open incidents. */
    Promise<Long> countOpen(UUID workspaceId);

    /** Count incidents by severity. */
    Promise<Long> countBySeverity(UUID workspaceId, String severity);

    /** Delete an incident. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if an incident exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
"""

files[f"{BASE}/repository/AlertRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for SecurityAlert persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Alert repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface AlertRepository {

    /** Save an alert. */
    Promise<SecurityAlert> save(SecurityAlert alert);

    /** Find an alert by ID. */
    Promise<SecurityAlert> findById(UUID workspaceId, UUID id);

    /** Find alerts by project. */
    Promise<List<SecurityAlert>> findByProject(UUID workspaceId, UUID projectId);

    /** Find alerts by status. */
    Promise<List<SecurityAlert>> findByStatus(UUID workspaceId, String status);

    /** Find alerts by severity. */
    Promise<List<SecurityAlert>> findBySeverity(UUID workspaceId, String severity);

    /** Find open alerts. */
    Promise<List<SecurityAlert>> findOpen(UUID workspaceId);

    /** Find open alerts by project. */
    Promise<List<SecurityAlert>> findOpenByProject(UUID workspaceId, UUID projectId);

    /** Find alerts assigned to a user. */
    Promise<List<SecurityAlert>> findByAssignedTo(UUID workspaceId, UUID userId);

    /** Count open alerts by severity. */
    Promise<Long> countOpenBySeverity(UUID workspaceId, String severity);

    /** Delete an alert. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if an alert exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
"""

files[f"{BASE}/repository/SecurityScanRepository.java"] = r"""/*
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
"""

files[f"{BASE}/repository/ComplianceRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for ComplianceAssessment persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Compliance assessment repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface ComplianceRepository {

    /** Save a compliance assessment. */
    Promise<ComplianceAssessment> save(ComplianceAssessment assessment);

    /** Find a compliance assessment by ID. */
    Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id);

    /** Find assessments by project. */
    Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId);

    /** Find assessments by framework. */
    Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId);

    /** Find assessments by project and framework. */
    Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId);

    /** Find assessments by status. */
    Promise<List<ComplianceAssessment>> findByStatus(UUID workspaceId, String status);

    /** Find assessments by assessment type. */
    Promise<List<ComplianceAssessment>> findByAssessmentType(UUID workspaceId, String assessmentType);

    /** Count assessments by status within a project. */
    Promise<Long> countByStatus(UUID workspaceId, UUID projectId, String status);

    /** Delete an assessment. */
    Promise<Void> delete(UUID workspaceId, UUID id);

    /** Check if an assessment exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);
}
"""

files[f"{BASE}/repository/ProjectRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.products.yappc.domain.model.Project;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Project persistence operations.
 *
 * @doc.type interface
 * @doc.purpose Project repository for CRUD operations
 * @doc.layer repository
 * @doc.pattern Repository
 */
public interface ProjectRepository {

    /** Save a project. */
    Promise<Project> save(Project project);

    /** Find a project by ID. */
    Promise<Optional<Project>> findById(UUID workspaceId, UUID id);

    /** Find a project by key. */
    Promise<Optional<Project>> findByKey(UUID workspaceId, String key);

    /** Find projects by workspace. */
    Promise<List<Project>> findByWorkspace(UUID workspaceId);

    /** Find active (non-archived) projects by workspace. */
    Promise<List<Project>> findActiveByWorkspace(UUID workspaceId);

    /** Search projects by name. */
    Promise<List<Project>> searchByName(UUID workspaceId, String query);

    /** Delete a project. */
    Promise<Boolean> delete(UUID workspaceId, UUID id);

    /** Check if a project exists. */
    Promise<Boolean> exists(UUID workspaceId, UUID id);

    /** Check if a project key is available. */
    Promise<Boolean> isKeyAvailable(UUID workspaceId, String key);

    /** Count projects by workspace. */
    Promise<Long> countByWorkspace(UUID workspaceId);
}
"""

# ============================================================
# 2. IN-MEMORY REPOSITORY IMPLEMENTATIONS
# ============================================================

files[f"{BASE}/repository/inmemory/InMemoryIncidentRepository.java"] = r"""/*
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
"""

files[f"{BASE}/repository/inmemory/InMemoryAlertRepository.java"] = r"""/*
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
"""

files[f"{BASE}/repository/inmemory/InMemorySecurityScanRepository.java"] = r"""/*
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
"""

files[f"{BASE}/repository/inmemory/InMemoryComplianceRepository.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository.inmemory;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ComplianceRepository for development and testing.
 *
 * @doc.type class
 * @doc.purpose In-memory compliance assessment storage
 * @doc.layer repository
 * @doc.pattern Repository
 */
public class InMemoryComplianceRepository implements ComplianceRepository {

    private final Map<UUID, Map<UUID, ComplianceAssessment>> workspaceAssessments = new ConcurrentHashMap<>();

    @Override
    public Promise<ComplianceAssessment> save(ComplianceAssessment assessment) {
        if (assessment.getId() == null) {
            assessment.setId(UUID.randomUUID());
        }
        if (assessment.getCreatedAt() == null) {
            assessment.setCreatedAt(Instant.now());
        }
        assessment.setUpdatedAt(Instant.now());

        workspaceAssessments
            .computeIfAbsent(assessment.getWorkspaceId(), k -> new ConcurrentHashMap<>())
            .put(assessment.getId(), assessment);
        return Promise.of(assessment);
    }

    @Override
    public Promise<ComplianceAssessment> findById(UUID workspaceId, UUID id) {
        return Promise.of(workspaceAssessments.getOrDefault(workspaceId, Map.of()).get(id));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProject(UUID workspaceId, UUID projectId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByFramework(UUID workspaceId, UUID frameworkId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getFrameworkId(), frameworkId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByProjectAndFramework(UUID workspaceId, UUID projectId, UUID frameworkId) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId) && Objects.equals(a.getFrameworkId(), frameworkId))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByStatus(UUID workspaceId, String status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> status.equalsIgnoreCase(a.getStatus()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<List<ComplianceAssessment>> findByAssessmentType(UUID workspaceId, String assessmentType) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> assessmentType.equalsIgnoreCase(a.getAssessmentType()))
            .collect(Collectors.toList()));
    }

    @Override
    public Promise<Long> countByStatus(UUID workspaceId, UUID projectId, String status) {
        return Promise.of(getAll(workspaceId).stream()
            .filter(a -> Objects.equals(a.getProjectId(), projectId))
            .filter(a -> status.equalsIgnoreCase(a.getStatus()))
            .count());
    }

    @Override
    public Promise<Void> delete(UUID workspaceId, UUID id) {
        Map<UUID, ComplianceAssessment> assessments = workspaceAssessments.get(workspaceId);
        if (assessments != null) {
            assessments.remove(id);
        }
        return Promise.of(null);
    }

    @Override
    public Promise<Boolean> exists(UUID workspaceId, UUID id) {
        return Promise.of(workspaceAssessments.getOrDefault(workspaceId, Map.of()).containsKey(id));
    }

    private List<ComplianceAssessment> getAll(UUID workspaceId) {
        return new ArrayList<>(workspaceAssessments.getOrDefault(workspaceId, Map.of()).values());
    }
}
"""

# ============================================================
# Write all files
# ============================================================
for path, content in files.items():
    full_path = os.path.join(os.getcwd(), path)
    os.makedirs(os.path.dirname(full_path), exist_ok=True)
    with open(full_path, 'w') as f:
        f.write(content.lstrip('\n'))
    print(f"OK: {path}")

print(f"\nWrote {len(files)} files")
