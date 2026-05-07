/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.dr;

import com.ghatana.aep.server.backup.AepBackupRecoveryService;
import com.ghatana.aep.server.backup.AepBackupRecoveryService.BackupMetadata;
import com.ghatana.aep.server.backup.AepBackupRecoveryService.BackupResult;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated Disaster Recovery (DR) service for AEP tenants.
 *
 * <p>Wraps {@link AepBackupRecoveryService} and adds:
 * <ul>
 *   <li><b>Automated backup scheduling</b> — periodic full or incremental backups
 *       driven by a per-tenant {@link DRPolicy}.</li>
 *   <li><b>RPO compliance tracking</b> — verifies that the last successful backup
 *       falls within the tenant's Recovery Point Objective.</li>
 *   <li><b>Retention enforcement</b> — keeps at most {@link DRPolicy#retentionCount()}
 *       backups per tenant, deleting the oldest ones automatically.</li>
 *   <li><b>DR readiness tests</b> — delegates to {@link AepBackupRecoveryService#verifyBackup}
 *       to ensure the latest backup can be restored.</li>
 *   <li><b>DR status reporting</b> — returns a structured {@link DRStatus} for each
 *       tenant including last backup time, RPO breach indicator, and backup count.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * All internal state is thread-safe. {@link #scheduleAutomatedBackups} and
 * {@link #stopAutomatedBackups} may be called from any thread.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * AepDisasterRecoveryService dr = new AepDisasterRecoveryService(backupService, meterRegistry);
 *
 * // Enable automated hourly backups, keep 7, require recovery within 2 hours
 * dr.scheduleAutomatedBackups("tenant-acme",
 *     new DRPolicy(60, 7, 120, BackupMode.FULL));
 *
 * // Get DR status
 * DRStatus status = dr.getDRStatus("tenant-acme").getResult();
 *
 * // Test recoverability of last backup
 * DRTestResult test = dr.testRecoverability("tenant-acme").getResult();
 *
 * // Graceful shutdown
 * dr.shutdown();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Automated disaster recovery orchestration for AEP tenants
 * @doc.layer product
 * @doc.pattern Service, Scheduler
 */
public final class AepDisasterRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AepDisasterRecoveryService.class);

    private final AepBackupRecoveryService                          backupService;
    private final ScheduledExecutorService                          scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>>    scheduledTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DRPolicy>              activePolicies = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicReference<BackupResult>> lastBackup = new ConcurrentHashMap<>();
    private final Counter                                           backupCounter;
    private final Counter                                           backupErrorCounter;
    private final Counter                                           retentionDeleteCounter;

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a DR service with a single-threaded backup scheduler.
     *
     * @param backupService underlying backup / recovery service
     * @param meterRegistry Micrometer registry for operational metrics
     */
    public AepDisasterRecoveryService(AepBackupRecoveryService backupService,
                                       MeterRegistry meterRegistry) {
        this(backupService, meterRegistry,
                Executors.newScheduledThreadPool(1, r -> {
                    Thread t = new Thread(r, "aep-dr-scheduler");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /**
     * Creates a DR service with a caller-supplied scheduler (useful for testing).
     *
     * @param backupService underlying backup / recovery service
     * @param meterRegistry Micrometer registry for operational metrics
     * @param scheduler     scheduled executor for backup automation
     */
    public AepDisasterRecoveryService(AepBackupRecoveryService backupService,
                                       MeterRegistry meterRegistry,
                                       ScheduledExecutorService scheduler) {
        this.backupService         = Objects.requireNonNull(backupService, "backupService must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.scheduler             = Objects.requireNonNull(scheduler, "scheduler must not be null");
        this.backupCounter         = meterRegistry.counter("aep.dr.backup.total");
        this.backupErrorCounter    = meterRegistry.counter("aep.dr.backup.errors");
        this.retentionDeleteCounter= meterRegistry.counter("aep.dr.retention.deleted");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Scheduling
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Enables automated periodic backups for the given tenant according to the policy.
     *
     * <p>If a schedule is already active for this tenant it is replaced atomically.
     *
     * @param tenantId tenant identifier
     * @param policy   DR policy governing backup frequency, retention, and RPO
     */
    public void scheduleAutomatedBackups(String tenantId, DRPolicy policy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        // Cancel any existing schedule for this tenant
        ScheduledFuture<?> existing = scheduledTasks.remove(tenantId);
        if (existing != null) {
            existing.cancel(false);
        }

        activePolicies.put(tenantId, policy);
        lastBackup.computeIfAbsent(tenantId, k -> new AtomicReference<>());

        long periodMinutes = Math.max(1, policy.backupIntervalMinutes());
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> runScheduledBackup(tenantId, policy),
                periodMinutes, periodMinutes, TimeUnit.MINUTES);

        scheduledTasks.put(tenantId, future);
        log.info("DR: automated backups scheduled for tenant={} intervalMinutes={} retention={}",
                tenantId, periodMinutes, policy.retentionCount());
    }

    /**
     * Stops automated backups for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return true if a schedule was stopped; false if none was active
     */
    public boolean stopAutomatedBackups(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        ScheduledFuture<?> task = scheduledTasks.remove(tenantId);
        activePolicies.remove(tenantId);
        if (task != null) {
            task.cancel(false);
            log.info("DR: automated backups stopped for tenant={}", tenantId);
            return true;
        }
        return false;
    }

    /**
     * Returns true if automated backups are currently active for the given tenant.
     *
     * @param tenantId tenant identifier
     * @return true if a schedule is active
     */
    public boolean isScheduled(String tenantId) {
        ScheduledFuture<?> task = scheduledTasks.get(tenantId);
        return task != null && !task.isCancelled() && !task.isDone();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  DR Status
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the current DR status for the given tenant.
     *
     * <p>Queries Data-Cloud for all backups, then determines:
     * <ul>
     *   <li>Last successful backup timestamp</li>
     *   <li>Total and active backup count</li>
     *   <li>Whether the tenant is within its RPO window</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @return promise of {@link DRStatus}
     */
    public Promise<DRStatus> getDRStatus(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        DRPolicy policy = activePolicies.get(tenantId);
        int targetRPOMinutes = policy != null ? policy.targetRPOMinutes() : Integer.MAX_VALUE;

        return backupService.listBackups(tenantId)
                .map(backups -> buildDRStatus(tenantId, backups, targetRPOMinutes, isScheduled(tenantId)));
    }

    /**
     * Tests DR readiness by verifying the most recent successful backup.
     *
     * <p>If no backup exists, returns a failed {@link DRTestResult}.
     *
     * @param tenantId tenant identifier
     * @return promise of {@link DRTestResult}
     */
    public Promise<DRTestResult> testRecoverability(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return backupService.listBackups(tenantId)
                .then(backups -> {
                    Optional<BackupMetadata> latest = backups.stream()
                            .filter(b -> "COMPLETE".equals(b.status()))
                            .max(Comparator.comparing(BackupMetadata::createdAt));

                    if (latest.isEmpty()) {
                        return Promise.of(new DRTestResult(
                                tenantId, null, false,
                                "No completed backup found", Instant.now()));
                    }

                    BackupMetadata meta = latest.get();
                    return backupService.verifyBackup(tenantId, meta.backupId())
                            .map(verification -> new DRTestResult(
                                    tenantId,
                                    meta.backupId(),
                                    verification.valid(),
                                    verification.valid()
                                            ? "Backup verified successfully"
                                            : verification.errorMessage(),
                                    Instant.now()
                            ));
                });
    }

    /**
     * Triggers an immediate full backup regardless of the automated schedule.
     *
     * @param tenantId tenant identifier
     * @return promise of the backup result
     */
    public Promise<BackupResult> triggerImmediateBackup(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        log.info("DR: immediate backup triggered for tenant={}", tenantId);
        return backupService.createFullBackup(tenantId)
                .then(result -> {
                    updateLastBackup(tenantId, result);
                    return Promise.of(result);
                });
    }

    /**
     * Shuts down the internal scheduler and cancels all active backup tasks.
     * Should be called when the service is no longer needed.
     */
    public void shutdown() {
        scheduledTasks.values().forEach(f -> f.cancel(false));
        scheduledTasks.clear();
        scheduler.shutdown();
        log.info("DR: service shut down");
    }

    /**
     * Returns an unmodifiable view of all currently active DR policies.
     *
     * @return map of tenantId → active policy
     */
    public Map<String, DRPolicy> activePolicies() {
        return Collections.unmodifiableMap(activePolicies);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Internal — Scheduled Backup Execution
    // ─────────────────────────────────────────────────────────────────────────

    private void runScheduledBackup(String tenantId, DRPolicy policy) {
        try {
            Promise<BackupResult> backupPromise;
            if (policy.mode() == BackupMode.INCREMENTAL) {
                BackupResult last = getLastBackupResult(tenantId);
                Instant since = last != null ? last.startedAt()
                        : Instant.now().minus(Duration.ofMinutes(policy.backupIntervalMinutes()));
                backupPromise = backupService.createIncrementalBackup(tenantId, since);
            } else {
                backupPromise = backupService.createFullBackup(tenantId);
            }

            backupPromise.then(result -> {
                if (result.success()) {
                    backupCounter.increment();
                    updateLastBackup(tenantId, result);
                    log.info("DR: scheduled backup completed tenant={} backupId={} entities={}",
                            tenantId, result.backupId(), result.entityCount());
                    // Fire-and-forget retention cleanup
                    enforceRetention(tenantId, policy.retentionCount());
                } else {
                    backupErrorCounter.increment();
                    log.warn("DR: scheduled backup completed with errors tenant={} failedCollections={}",
                            tenantId, result.failedCollections());
                }
                return Promise.complete();
            }, err -> {
                backupErrorCounter.increment();
                log.error("DR: scheduled backup failed tenant={}: {}", tenantId, err.getMessage(), err);
                return Promise.complete();
            });
        } catch (Exception ex) {
            backupErrorCounter.increment();
            log.error("DR: unexpected error during scheduled backup for tenant={}", tenantId, ex);
        }
    }

    private void enforceRetention(String tenantId, int retentionCount) {
        backupService.listBackups(tenantId)
                .then(backups -> {
                    if (backups.size() <= retentionCount) return Promise.complete();

                    // Sort oldest first and delete the excess
                    List<BackupMetadata> sorted = new ArrayList<>(backups);
                    sorted.sort(Comparator.comparing(BackupMetadata::createdAt));
                    List<Promise<Void>> deletions = new ArrayList<>();
                    int toDelete = sorted.size() - retentionCount;
                    for (int i = 0; i < toDelete; i++) {
                        String backupId = sorted.get(i).backupId();
                        deletions.add(backupService.deleteBackup(tenantId, backupId)
                                .then(v -> {
                                    retentionDeleteCounter.increment();
                                    log.info("DR: deleted old backup tenant={} backupId={}", tenantId, backupId);
                                    return Promise.complete();
                                }, e -> {
                                    log.warn("DR: failed to delete backup tenant={} backupId={}: {}",
                                            tenantId, backupId, e.getMessage());
                                    return Promise.complete();
                                }));
                    }
                    return Promises.all(deletions).toVoid();
                }, err -> {
                    log.error("DR: retention cleanup failed for tenant={}: {}", tenantId, err.getMessage(), err);
                    return Promise.complete();
                });
    }

    private void updateLastBackup(String tenantId, BackupResult result) {
        lastBackup.computeIfAbsent(tenantId, k -> new AtomicReference<>())
                  .set(result);
    }

    private BackupResult getLastBackupResult(String tenantId) {
        AtomicReference<BackupResult> ref = lastBackup.get(tenantId);
        return ref != null ? ref.get() : null;
    }

    private static DRStatus buildDRStatus(String tenantId, List<BackupMetadata> backups,
                                           int targetRPOMinutes, boolean scheduled) {
        if (backups.isEmpty()) {
            return new DRStatus(tenantId, null, 0, false, targetRPOMinutes,
                    false, scheduled, Instant.now());
        }

        Optional<BackupMetadata> latestComplete = backups.stream()
                .filter(b -> "COMPLETE".equals(b.status()))
                .max(Comparator.comparing(BackupMetadata::createdAt));

        Instant lastBackupTime = latestComplete.map(BackupMetadata::createdAt).orElse(null);
        boolean withinRPO = false;
        if (lastBackupTime != null) {
            long minutesSinceBackup = Duration.between(lastBackupTime, Instant.now()).toMinutes();
            withinRPO = minutesSinceBackup <= targetRPOMinutes;
        }

        return new DRStatus(
                tenantId,
                lastBackupTime,
                backups.size(),
                latestComplete.isPresent(),
                targetRPOMinutes,
                withinRPO,
                scheduled,
                Instant.now()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Public API Types
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * DR backup mode.
     *
     * @doc.type enum
     * @doc.purpose Determines whether each automated backup is full or incremental
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public enum BackupMode { FULL, INCREMENTAL }

    /**
     * DR policy for a tenant.
     *
     * @param backupIntervalMinutes frequency of automated backups (minimum: 1)
     * @param retentionCount        maximum number of backups to retain (minimum: 1)
     * @param targetRPOMinutes      Recovery Point Objective in minutes; used to
     *                              determine RPO compliance in {@link DRStatus}
     * @param mode                  whether automated backups are full or incremental
     *
     * @doc.type record
     * @doc.purpose Immutable DR policy governing backup frequency, retention, and RPO
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record DRPolicy(
            int        backupIntervalMinutes,
            int        retentionCount,
            int        targetRPOMinutes,
            BackupMode mode) {

        public DRPolicy {
            if (backupIntervalMinutes < 1) throw new IllegalArgumentException("backupIntervalMinutes must be >= 1");
            if (retentionCount        < 1) throw new IllegalArgumentException("retentionCount must be >= 1");
            if (targetRPOMinutes      < 1) throw new IllegalArgumentException("targetRPOMinutes must be >= 1");
            Objects.requireNonNull(mode, "mode must not be null");
        }

        /** Convenient hourly full backup policy with 7-day retention and 2-hour RPO. */
        public static DRPolicy standard() {
            return new DRPolicy(60, 168, 120, BackupMode.FULL);
        }

        /** Conservative daily full backup policy with 30-day retention and 24-hour RPO. */
        public static DRPolicy conservative() {
            return new DRPolicy(1440, 30, 1440, BackupMode.FULL);
        }
    }

    /**
     * Current DR status snapshot for a tenant.
     *
     * @param tenantId          tenant identifier
     * @param lastBackupTime    timestamp of last completed backup (null if no backup)
     * @param backupCount       total backups retained
     * @param hasCompleteBackup true if at least one COMPLETE backup exists
     * @param targetRPOMinutes  configured Recovery Point Objective in minutes
     * @param withinRPO         true if the elapsed time since last backup ≤ targetRPO
     * @param automationActive  true if automated scheduling is running
     * @param reportedAt        when this status was computed
     *
     * @doc.type record
     * @doc.purpose Immutable DR status snapshot for operational dashboards
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record DRStatus(
            String  tenantId,
            Instant lastBackupTime,
            int     backupCount,
            boolean hasCompleteBackup,
            int     targetRPOMinutes,
            boolean withinRPO,
            boolean automationActive,
            Instant reportedAt) {}

    /**
     * Result of a DR recoverability test.
     *
     * @param tenantId    tenant that was tested
     * @param backupId    backup that was verified (null if no backup was found)
     * @param recoverable true if the backup can be restored
     * @param message     human-readable summary
     * @param testedAt    when the test was performed
     *
     * @doc.type record
     * @doc.purpose Immutable DR recoverability test result
     * @doc.layer product
     * @doc.pattern ValueObject
     */
    public record DRTestResult(
            String  tenantId,
            String  backupId,
            boolean recoverable,
            String  message,
            Instant testedAt) {}
}
