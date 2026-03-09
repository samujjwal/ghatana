/*
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

        ScanJob scan = ScanJob.pending(workspaceId, input.projectId(), input.scanType());
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
