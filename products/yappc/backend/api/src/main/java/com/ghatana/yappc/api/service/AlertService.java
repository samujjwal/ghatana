/*
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
