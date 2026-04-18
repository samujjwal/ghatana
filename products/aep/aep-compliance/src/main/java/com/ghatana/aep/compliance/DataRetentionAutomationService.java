/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.compliance;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Automated data retention service that periodically scans for expired data
 * and schedules deletion with audit logging.
 *
 * <p>Runs on a configurable interval (default: daily) and:
 * <ul>
 *   <li>Finds all retention policies where expires_at < NOW()</li>
 *   <li>Schedules deletion for expired data</li>
 *   <li>Logs audit trail for each automated action</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Automated data retention enforcement with audit logging
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DataRetentionAutomationService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionAutomationService.class);

    private final DataSource dataSource;
    private final RetentionPolicyEnforcer retentionEnforcer;
    private final EventProcessingAuditService auditService;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final Duration scanInterval;
    private volatile ScheduledFuture<?> scheduledFuture;
    private volatile boolean running = false;

    /**
     * Creates a data retention automation service.
     *
     * @param dataSource JDBC data source for scanning retention policies
     * @param retentionEnforcer retention policy enforcer for scheduling deletions
     * @param auditService audit service for logging retention actions
     * @param scanInterval interval between retention scans (default: daily)
     */
    public DataRetentionAutomationService(
            DataSource dataSource,
            RetentionPolicyEnforcer retentionEnforcer,
            EventProcessingAuditService auditService,
            Duration scanInterval) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.retentionEnforcer = Objects.requireNonNull(retentionEnforcer, "retentionEnforcer");
        this.auditService = Objects.requireNonNull(auditService, "auditService");
        this.scanInterval = scanInterval != null ? scanInterval : Duration.ofDays(1);
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "retention-automation");
            t.setDaemon(true);
            return t;
        });
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "retention-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a data retention automation service with default daily scan interval.
     */
    public DataRetentionAutomationService(
            DataSource dataSource,
            RetentionPolicyEnforcer retentionEnforcer,
            EventProcessingAuditService auditService) {
        this(dataSource, retentionEnforcer, auditService, Duration.ofDays(1));
    }

    /**
     * Starts the automated retention scanning service.
     */
    public void start() {
        if (running) {
            log.warn("[retention-automation] Already running");
            return;
        }
        running = true;
        log.info("[retention-automation] Starting automated retention scanning (interval={})", scanInterval);
        scheduledFuture = scheduler.scheduleAtFixedRate(
            this::scanAndExpire,
            0,
            scanInterval.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Stops the automated retention scanning service.
     */
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
        scheduler.shutdown();
        log.info("[retention-automation] Stopped automated retention scanning");
    }

    /**
     * Triggers an immediate retention scan (useful for testing or manual triggers).
     *
     * @return promise of scan result with count of expired items processed
     */
    public Promise<RetentionScanResult> scanNow() {
        return Promise.ofBlocking(executor, this::scanAndExpire);
    }

    /**
     * Scans for expired retention policies and schedules deletion.
     *
     * @return count of expired items processed
     */
    private RetentionScanResult scanAndExpire() {
        int scanned = 0;
        int expired = 0;
        int scheduled = 0;
        int failed = 0;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT tenant_id, data_id, expires_at
                 FROM retention_policies
                 WHERE expires_at < NOW()
                   AND scheduled_for_deletion = FALSE
                 ORDER BY expires_at ASC
                 LIMIT 1000
                 """)) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    scanned++;
                    String tenantId = rs.getString("tenant_id");
                    String dataId = rs.getString("data_id");
                    Timestamp expiresAtTs = rs.getTimestamp("expires_at");
                    Instant expiresAt = expiresAtTs.toInstant();

                    try {
                        // Schedule deletion via retention enforcer
                        retentionEnforcer.scheduleDeletion(tenantId, dataId).getResult();
                        
                        // Log audit trail
                        auditService.logDecision(
                            tenantId,
                            dataId,
                            "RETENTION_EXPIRY",
                            "SCHEDULED_FOR_DELETION",
                            "Data expired at " + expiresAt + " and scheduled for deletion",
                            java.util.Map.of(
                                "expiresAt", expiresAt.toString(),
                                "scanTimestamp", Instant.now().toString()
                            )
                        );

                        expired++;
                        scheduled++;
                        log.debug("[retention-automation] Scheduled deletion for expired data: tenant={}, dataId={}, expires={}",
                            tenantId, dataId, expiresAt);
                    } catch (Exception e) {
                        failed++;
                        log.error("[retention-automation] Failed to schedule deletion for tenant={} dataId={}: {}",
                            tenantId, dataId, e.getMessage(), e);
                        
                        // Log audit trail for failure
                        auditService.logDecision(
                            tenantId,
                            dataId,
                            "RETENTION_EXPIRY",
                            "FAILED",
                            "Failed to schedule deletion: " + e.getMessage(),
                            java.util.Map.of(
                                "expiresAt", expiresAt.toString(),
                                "error", e.getMessage()
                            )
                        );
                    }
                }
            }

            if (expired > 0) {
                log.info("[retention-automation] Retention scan complete: scanned={}, expired={}, scheduled={}, failed={}",
                    scanned, expired, scheduled, failed);
            }

        } catch (Exception e) {
            log.error("[retention-automation] Retention scan failed: {}", e.getMessage(), e);
        }

        return new RetentionScanResult(scanned, expired, scheduled, failed);
    }

    /**
     * Result of a retention scan operation.
     *
     * @param scanned total number of retention policies scanned
     * @param expired number of expired policies found
     * @param scheduled number of deletions successfully scheduled
     * @param failed number of scheduling failures
     */
    public record RetentionScanResult(
        int scanned,
        int expired,
        int scheduled,
        int failed
    ) {
        public boolean hasExpired() { return expired > 0; }
        public boolean hasFailures() { return failed > 0; }
    }
}
