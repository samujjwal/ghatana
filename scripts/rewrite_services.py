#!/usr/bin/env python3
"""Rewrite service classes to use L3 entity API."""

import os

BASE = "products/yappc/backend/api/src/main/java/com/ghatana/yappc/api"

files = {}

# ============================================================
# 1. IncidentService
# ============================================================
files[f"{BASE}/service/IncidentService.java"] = r"""/*
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
"""

# ============================================================
# 2. AlertService
# ============================================================
files[f"{BASE}/service/AlertService.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.products.yappc.domain.model.SecurityAlert;
import com.ghatana.yappc.api.repository.AlertRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing security alerts.
 *
 * @doc.type class
 * @doc.purpose Business logic for alert operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository repository;

    @Inject
    public AlertService(AlertRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new security alert.
     */
    public Promise<SecurityAlert> createAlert(UUID workspaceId, CreateAlertInput input) {
        logger.info("Creating alert: {} for workspace: {}", input.title(), workspaceId);

        SecurityAlert alert = SecurityAlert.of(workspaceId, input.alertType(), input.severity(), input.title());
        alert.setDescription(input.description());
        alert.setSource(input.source());
        if (input.projectId() != null) {
            alert.setProjectId(input.projectId());
        }
        if (input.resourceId() != null) {
            alert.setResourceId(input.resourceId());
        }
        if (input.ruleId() != null) {
            alert.setRuleId(input.ruleId());
            alert.setRuleName(input.ruleName());
        }
        if (input.assignedTo() != null) {
            alert.setAssignedTo(input.assignedTo());
        }
        alert.setDetectedAt(Instant.now());

        return repository.save(alert);
    }

    /**
     * Gets an alert by ID.
     */
    public Promise<Optional<SecurityAlert>> getAlert(UUID workspaceId, UUID alertId) {
        return repository.findById(workspaceId, alertId)
            .map(Optional::ofNullable);
    }

    /**
     * Lists alerts for a project.
     */
    public Promise<List<SecurityAlert>> listProjectAlerts(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId);
    }

    /**
     * Lists open alerts.
     */
    public Promise<List<SecurityAlert>> listOpenAlerts(UUID workspaceId) {
        return repository.findOpen(workspaceId);
    }

    /**
     * Lists open alerts for a project.
     */
    public Promise<List<SecurityAlert>> listOpenProjectAlerts(UUID workspaceId, UUID projectId) {
        return repository.findOpenByProject(workspaceId, projectId);
    }

    /**
     * Lists alerts by severity.
     */
    public Promise<List<SecurityAlert>> listAlertsBySeverity(UUID workspaceId, String severity) {
        return repository.findBySeverity(workspaceId, severity);
    }

    /**
     * Lists alerts assigned to a user.
     */
    public Promise<List<SecurityAlert>> listAssignedAlerts(UUID workspaceId, UUID userId) {
        return repository.findByAssignedTo(workspaceId, userId);
    }

    /**
     * Acknowledges an alert.
     */
    public Promise<SecurityAlert> acknowledgeAlert(UUID workspaceId, UUID alertId, UUID userId) {
        logger.info("Acknowledging alert: {} by user: {}", alertId, userId);

        return repository.findById(workspaceId, alertId)
            .then(alert -> {
                if (alert == null) {
                    return Promise.ofException(new IllegalArgumentException("Alert not found"));
                }
                alert.acknowledge(userId);
                return repository.save(alert);
            });
    }

    /**
     * Resolves an alert.
     */
    public Promise<SecurityAlert> resolveAlert(UUID workspaceId, UUID alertId, UUID userId) {
        logger.info("Resolving alert: {} by user: {}", alertId, userId);

        return repository.findById(workspaceId, alertId)
            .then(alert -> {
                if (alert == null) {
                    return Promise.ofException(new IllegalArgumentException("Alert not found"));
                }
                alert.resolve(userId);
                return repository.save(alert);
            });
    }

    /**
     * Gets alert statistics for a project.
     */
    public Promise<AlertStats> getAlertStatistics(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId)
            .map(alerts -> {
                AlertStats stats = new AlertStats();
                stats.totalAlerts = alerts.size();
                stats.openCount = alerts.stream().filter(SecurityAlert::isOpen).count();
                stats.criticalCount = alerts.stream()
                    .filter(SecurityAlert::isCritical).count();
                stats.acknowledgedCount = alerts.stream()
                    .filter(a -> "ACKNOWLEDGED".equalsIgnoreCase(a.getStatus())).count();
                stats.resolvedCount = alerts.stream()
                    .filter(a -> "RESOLVED".equalsIgnoreCase(a.getStatus())).count();
                return stats;
            });
    }

    /**
     * Deletes an alert.
     */
    public Promise<Void> deleteAlert(UUID workspaceId, UUID alertId) {
        logger.info("Deleting alert: {} for workspace: {}", alertId, workspaceId);
        return repository.delete(workspaceId, alertId);
    }

    // ========== Input Records ==========

    public record CreateAlertInput(
        UUID projectId,
        String alertType,
        String severity,
        String title,
        String description,
        String source,
        UUID resourceId,
        String ruleId,
        String ruleName,
        UUID assignedTo
    ) {}

    // ========== Stats ==========

    public static class AlertStats {
        public int totalAlerts;
        public long openCount;
        public long criticalCount;
        public long acknowledgedCount;
        public long resolvedCount;
    }
}
"""

# ============================================================
# 3. SecurityScanService
# ============================================================
files[f"{BASE}/service/SecurityScanService.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.products.yappc.domain.model.ScanJob;
import com.ghatana.products.yappc.domain.enums.ScanStatus;
import com.ghatana.products.yappc.domain.enums.ScanType;
import com.ghatana.yappc.api.repository.SecurityScanRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing security scans.
 *
 * @doc.type class
 * @doc.purpose Business logic for security scan operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class SecurityScanService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityScanService.class);

    private final SecurityScanRepository repository;

    @Inject
    public SecurityScanService(SecurityScanRepository repository) {
        this.repository = repository;
    }

    /**
     * Starts a new security scan.
     */
    public Promise<ScanJob> startScan(UUID workspaceId, StartScanInput input) {
        logger.info("Starting scan for project: {} in workspace: {}", input.projectId(), workspaceId);

        ScanJob scan = ScanJob.of(workspaceId, input.projectId(), input.scanType());
        if (input.scannerName() != null) {
            scan.setScannerName(input.scannerName());
        }
        if (input.scannerVersion() != null) {
            scan.setScannerVersion(input.scannerVersion());
        }
        if (input.target() != null) {
            scan.setTarget(input.target());
        }
        if (input.config() != null) {
            scan.setConfig(input.config());
        }
        scan.start();

        return repository.save(scan);
    }

    /**
     * Gets a scan by ID.
     */
    public Promise<Optional<ScanJob>> getScan(UUID workspaceId, UUID scanId) {
        return repository.findById(workspaceId, scanId)
            .map(Optional::ofNullable);
    }

    /**
     * Lists scans for a project.
     */
    public Promise<List<ScanJob>> listProjectScans(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId);
    }

    /**
     * Lists scans by type.
     */
    public Promise<List<ScanJob>> listScansByType(UUID workspaceId, ScanType type) {
        return repository.findByType(workspaceId, type);
    }

    /**
     * Lists running scans.
     */
    public Promise<List<ScanJob>> listRunningScans(UUID workspaceId) {
        return repository.findRunning(workspaceId);
    }

    /**
     * Gets the latest scan for a project and type.
     */
    public Promise<ScanJob> getLatestScan(UUID workspaceId, UUID projectId, ScanType type) {
        return repository.findLatestByProjectAndType(workspaceId, projectId, type);
    }

    /**
     * Completes a scan with results.
     */
    public Promise<ScanJob> completeScan(UUID workspaceId, UUID scanId, CompleteScanInput input) {
        logger.info("Completing scan: {}", scanId);

        return repository.findById(workspaceId, scanId)
            .then(scan -> {
                if (scan == null) {
                    return Promise.ofException(new IllegalArgumentException("Scan not found"));
                }
                scan.setFindingsCount(input.findingsCount());
                scan.setCriticalCount(input.criticalCount());
                scan.setHighCount(input.highCount());
                scan.setMediumCount(input.mediumCount());
                scan.setLowCount(input.lowCount());
                scan.setInfoCount(input.infoCount());
                scan.complete();
                return repository.save(scan);
            });
    }

    /**
     * Fails a scan.
     */
    public Promise<ScanJob> failScan(UUID workspaceId, UUID scanId, String reason) {
        logger.info("Failing scan: {} - {}", scanId, reason);

        return repository.findById(workspaceId, scanId)
            .then(scan -> {
                if (scan == null) {
                    return Promise.ofException(new IllegalArgumentException("Scan not found"));
                }
                scan.fail(reason);
                return repository.save(scan);
            });
    }

    /**
     * Gets scan statistics for a project.
     */
    public Promise<ScanStats> getScanStatistics(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId)
            .map(scans -> {
                ScanStats stats = new ScanStats();
                stats.totalScans = scans.size();
                stats.completedCount = scans.stream()
                    .filter(s -> s.getStatus() == ScanStatus.COMPLETED).count();
                stats.runningCount = scans.stream()
                    .filter(s -> s.getStatus() == ScanStatus.RUNNING).count();
                stats.failedCount = scans.stream()
                    .filter(s -> s.getStatus() == ScanStatus.FAILED).count();
                stats.totalFindings = scans.stream()
                    .mapToInt(ScanJob::getFindingsCount).sum();
                stats.totalCritical = scans.stream()
                    .mapToInt(ScanJob::getCriticalCount).sum();
                return stats;
            });
    }

    /**
     * Deletes a scan.
     */
    public Promise<Void> deleteScan(UUID workspaceId, UUID scanId) {
        logger.info("Deleting scan: {} for workspace: {}", scanId, workspaceId);
        return repository.delete(workspaceId, scanId);
    }

    // ========== Input Records ==========

    public record StartScanInput(
        UUID projectId,
        ScanType scanType,
        String scannerName,
        String scannerVersion,
        String target,
        String config
    ) {}

    public record CompleteScanInput(
        int findingsCount,
        int criticalCount,
        int highCount,
        int mediumCount,
        int lowCount,
        int infoCount
    ) {}

    // ========== Stats ==========

    public static class ScanStats {
        public int totalScans;
        public long completedCount;
        public long runningCount;
        public long failedCount;
        public int totalFindings;
        public int totalCritical;
    }
}
"""

# ============================================================
# 4. ComplianceService
# ============================================================
files[f"{BASE}/service/ComplianceService.java"] = r"""/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.products.yappc.domain.model.ComplianceAssessment;
import com.ghatana.yappc.api.repository.ComplianceRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing compliance assessments.
 *
 * @doc.type class
 * @doc.purpose Business logic for compliance assessment operations
 * @doc.layer service
 * @doc.pattern Service
 */
public class ComplianceService {

    private static final Logger logger = LoggerFactory.getLogger(ComplianceService.class);

    private final ComplianceRepository repository;

    @Inject
    public ComplianceService(ComplianceRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new compliance assessment.
     */
    public Promise<ComplianceAssessment> createAssessment(UUID workspaceId, CreateAssessmentInput input) {
        logger.info("Creating assessment for framework: {} in workspace: {}", input.frameworkId(), workspaceId);

        ComplianceAssessment assessment = ComplianceAssessment.of(workspaceId, input.frameworkId());
        if (input.projectId() != null) {
            assessment.setProjectId(input.projectId());
        }
        if (input.assessorName() != null) {
            assessment.setAssessorName(input.assessorName());
        }
        if (input.assessmentType() != null) {
            assessment.setAssessmentType(input.assessmentType());
        }
        if (input.dueDate() != null) {
            assessment.setDueDate(input.dueDate());
        }
        if (input.notes() != null) {
            assessment.setNotes(input.notes());
        }

        return repository.save(assessment);
    }

    /**
     * Gets a compliance assessment by ID.
     */
    public Promise<Optional<ComplianceAssessment>> getAssessment(UUID workspaceId, UUID assessmentId) {
        return repository.findById(workspaceId, assessmentId)
            .map(Optional::ofNullable);
    }

    /**
     * Lists assessments for a project.
     */
    public Promise<List<ComplianceAssessment>> listProjectAssessments(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId);
    }

    /**
     * Lists assessments by framework.
     */
    public Promise<List<ComplianceAssessment>> listByFramework(UUID workspaceId, UUID frameworkId) {
        return repository.findByFramework(workspaceId, frameworkId);
    }

    /**
     * Lists assessments for a project and framework.
     */
    public Promise<List<ComplianceAssessment>> listProjectFrameworkAssessments(
            UUID workspaceId, UUID projectId, UUID frameworkId) {
        return repository.findByProjectAndFramework(workspaceId, projectId, frameworkId);
    }

    /**
     * Starts an assessment.
     */
    public Promise<ComplianceAssessment> startAssessment(UUID workspaceId, UUID assessmentId, String assessorName) {
        logger.info("Starting assessment: {} by {}", assessmentId, assessorName);

        return repository.findById(workspaceId, assessmentId)
            .then(assessment -> {
                if (assessment == null) {
                    return Promise.ofException(new IllegalArgumentException("Assessment not found"));
                }
                assessment.startAssessment();
                assessment.setAssessorName(assessorName);
                assessment.setAssessmentDate(LocalDate.now());
                return repository.save(assessment);
            });
    }

    /**
     * Completes an assessment with results.
     */
    public Promise<ComplianceAssessment> completeAssessment(UUID workspaceId, UUID assessmentId,
                                                             CompleteAssessmentInput input) {
        logger.info("Completing assessment: {}", assessmentId);

        return repository.findById(workspaceId, assessmentId)
            .then(assessment -> {
                if (assessment == null) {
                    return Promise.ofException(new IllegalArgumentException("Assessment not found"));
                }
                assessment.setPassedControls(input.passedControls());
                assessment.setFailedControls(input.failedControls());
                assessment.setNaControls(input.naControls());
                assessment.completeAssessment(input.score());
                if (input.notes() != null) {
                    assessment.setNotes(input.notes());
                }
                return repository.save(assessment);
            });
    }

    /**
     * Gets compliance statistics for a project.
     */
    public Promise<ComplianceStats> getComplianceStatistics(UUID workspaceId, UUID projectId) {
        return repository.findByProject(workspaceId, projectId)
            .map(assessments -> {
                ComplianceStats stats = new ComplianceStats();
                stats.totalAssessments = assessments.size();
                stats.completedCount = assessments.stream()
                    .filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus())).count();
                stats.inProgressCount = assessments.stream()
                    .filter(a -> "IN_PROGRESS".equalsIgnoreCase(a.getStatus())).count();
                stats.averageScore = assessments.stream()
                    .filter(a -> "COMPLETED".equalsIgnoreCase(a.getStatus()))
                    .mapToInt(ComplianceAssessment::getScore)
                    .average()
                    .orElse(0.0);
                stats.totalPassed = assessments.stream()
                    .mapToInt(ComplianceAssessment::getPassedControls).sum();
                stats.totalFailed = assessments.stream()
                    .mapToInt(ComplianceAssessment::getFailedControls).sum();
                return stats;
            });
    }

    /**
     * Deletes an assessment.
     */
    public Promise<Void> deleteAssessment(UUID workspaceId, UUID assessmentId) {
        logger.info("Deleting assessment: {} for workspace: {}", assessmentId, workspaceId);
        return repository.delete(workspaceId, assessmentId);
    }

    // ========== Input Records ==========

    public record CreateAssessmentInput(
        UUID frameworkId,
        UUID projectId,
        String assessorName,
        String assessmentType,
        LocalDate dueDate,
        String notes
    ) {}

    public record CompleteAssessmentInput(
        int score,
        int passedControls,
        int failedControls,
        int naControls,
        String notes
    ) {}

    // ========== Stats ==========

    public static class ComplianceStats {
        public int totalAssessments;
        public long completedCount;
        public long inProgressCount;
        public double averageScore;
        public int totalPassed;
        public int totalFailed;
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
