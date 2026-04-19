package com.ghatana.phr.service;

import com.ghatana.kernel.service.KernelLifecycleAware;
import com.ghatana.phr.kernel.retention.DeletionOutcome;
import com.ghatana.phr.kernel.retention.LegalHoldService;
import com.ghatana.phr.kernel.retention.PatientDeletionWorkflow;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduled retention housekeeping for the PHR product.
 *
 * <p>Runs three categories of periodic jobs following Nepal Health Records
 * Act 2081 §22 and Privacy Act 2075 Art. 14:
 *
 * <ol>
 *   <li><b>Retention-eligible deletion</b> — identifies patients whose 25-year
 *       retention period has elapsed, verifies no active treatment episodes or
 *       legal holds are present, then delegates to {@link PatientDeletionWorkflow}
 *       to execute the authorised deletion with full audit evidence.</li>
 *   <li><b>Expired consent purge</b> — finds consent grants that have passed
 *       their expiry date and marks them as {@code EXPIRED} so downstream services
 *       stop honouring them.  Hard-deletion happens only after an additional
 *       grace window defined by {@code gracePeriodDays}.</li>
 *   <li><b>Audit chain sync</b> — replays the outstanding audit-event backlog
 *       from the durable store into the in-memory hash chain to keep the
 *       verification window up to date after restarts.</li>
 * </ol>
 *
 * <p>Lifecycle: call {@link #start()} once during application startup and
 * {@link #stop()} during graceful shutdown.  The scheduler owns a single daemon
 * thread and therefore does not prevent JVM exit.
 *
 * @doc.type class
 * @doc.purpose PHR data retention scheduler — erasure, consent expiry, audit sync
 * @doc.layer product
 * @doc.pattern ScheduledService
 * @since 1.0.0
 */
public final class PhrRetentionScheduler implements KernelLifecycleAware {

    private static final Logger LOG = LoggerFactory.getLogger(PhrRetentionScheduler.class);

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /** Default interval between retention scans. */
    public static final Duration DEFAULT_RETENTION_INTERVAL = Duration.ofHours(6);

    /** Default interval between consent-expiry scans. */
    public static final Duration DEFAULT_CONSENT_EXPIRY_INTERVAL = Duration.ofHours(1);

    /** Default interval between audit-chain sync runs. */
    public static final Duration DEFAULT_AUDIT_SYNC_INTERVAL = Duration.ofMinutes(30);

    // -------------------------------------------------------------------------
    // Ports — callers supply concrete implementations
    // -------------------------------------------------------------------------

    /**
     * Source of patient IDs whose retention period has elapsed and whose
     * record may now be eligible for deletion.
     */
    public interface RetentionEligibilityPort {
        /**
         * Lists patient IDs registered before {@code cutoff} that are not on
         * a legal hold and have no active treatment episodes.
         */
        List<String> listRetentionEligiblePatients(String tenantId, Instant cutoff);
    }

    /**
     * Port for marking expired consents and hard-deleting those past the
     * grace window.
     */
    public interface ConsentExpiryPort {
        /**
         * Marks consent records as {@code EXPIRED} where {@code expires_at < now}.
         *
         * @return number of records marked expired
         */
        int markExpiredConsents();

        /**
         * Hard-deletes consent records that have been in the {@code EXPIRED}
         * state for longer than {@code gracePeriodDays}.
         *
         * @param gracePeriodDays the number of days after expiry before deletion
         * @return number of records deleted
         */
        int purgeExpiredConsents(int gracePeriodDays);
    }

    /**
     * Port for triggering an audit-chain replay from the durable store.
     */
    public interface AuditSyncPort {
        /**
         * Replays outstanding audit-event entries from the durable store into
         * the in-memory hash chain verifier.
         *
         * @return number of events synced
         */
        int syncAuditChain();
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private final PatientDeletionWorkflow deletionWorkflow;
    private final LegalHoldService legalHoldService;
    private final RetentionEligibilityPort eligibilityPort;
    private final ConsentExpiryPort consentExpiryPort;
    private final AuditSyncPort auditSyncPort;

    private final Duration retentionInterval;
    private final Duration consentExpiryInterval;
    private final Duration auditSyncInterval;
    private final int consentGracePeriodDays;

    private ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Full constructor.
     *
     * @param deletionWorkflow      the patient deletion workflow; must not be null
     * @param legalHoldService      the legal-hold service; must not be null
     * @param eligibilityPort       port for listing retention-eligible patients; must not be null
     * @param consentExpiryPort     port for managing expired consent records; must not be null
     * @param auditSyncPort         port for audit-chain sync; must not be null
     * @param retentionInterval     how often to run the deletion scan
     * @param consentExpiryInterval how often to run the consent-expiry scan
     * @param auditSyncInterval     how often to sync the audit chain
     * @param consentGracePeriodDays number of days to keep EXPIRED consent records before hard-delete
     */
    public PhrRetentionScheduler(PatientDeletionWorkflow deletionWorkflow,
                                  LegalHoldService legalHoldService,
                                  RetentionEligibilityPort eligibilityPort,
                                  ConsentExpiryPort consentExpiryPort,
                                  AuditSyncPort auditSyncPort,
                                  Duration retentionInterval,
                                  Duration consentExpiryInterval,
                                  Duration auditSyncInterval,
                                  int consentGracePeriodDays) {
        this.deletionWorkflow = Objects.requireNonNull(deletionWorkflow, "deletionWorkflow cannot be null");
        this.legalHoldService = Objects.requireNonNull(legalHoldService, "legalHoldService cannot be null");
        this.eligibilityPort = Objects.requireNonNull(eligibilityPort, "eligibilityPort cannot be null");
        this.consentExpiryPort = Objects.requireNonNull(consentExpiryPort, "consentExpiryPort cannot be null");
        this.auditSyncPort = Objects.requireNonNull(auditSyncPort, "auditSyncPort cannot be null");
        this.retentionInterval = Objects.requireNonNull(retentionInterval, "retentionInterval cannot be null");
        this.consentExpiryInterval = Objects.requireNonNull(consentExpiryInterval, "consentExpiryInterval cannot be null");
        this.auditSyncInterval = Objects.requireNonNull(auditSyncInterval, "auditSyncInterval cannot be null");
        this.consentGracePeriodDays = consentGracePeriodDays;
    }

    /**
     * Convenience constructor using default intervals and a 30-day consent grace period.
     */
    public PhrRetentionScheduler(PatientDeletionWorkflow deletionWorkflow,
                                  LegalHoldService legalHoldService,
                                  RetentionEligibilityPort eligibilityPort,
                                  ConsentExpiryPort consentExpiryPort,
                                  AuditSyncPort auditSyncPort) {
        this(deletionWorkflow, legalHoldService, eligibilityPort, consentExpiryPort, auditSyncPort,
                DEFAULT_RETENTION_INTERVAL, DEFAULT_CONSENT_EXPIRY_INTERVAL, DEFAULT_AUDIT_SYNC_INTERVAL, 30);
    }

    // -------------------------------------------------------------------------
    // KernelLifecycleAware
    // -------------------------------------------------------------------------

    @Override
    public Promise<Void> start() {
        if (!running.compareAndSet(false, true)) {
            return Promise.complete();
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "phr-retention-scheduler");
            thread.setDaemon(true);
            return thread;
        });

        long retentionSeconds = retentionInterval.toSeconds();
        long consentSeconds = consentExpiryInterval.toSeconds();
        long auditSeconds = auditSyncInterval.toSeconds();

        // Stagger initial delays to avoid thundering-herd at startup
        scheduler.scheduleAtFixedRate(this::runRetentionScan,
                retentionSeconds, retentionSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::runConsentExpiryScan,
                60L, consentSeconds, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::runAuditSync,
                30L, auditSeconds, TimeUnit.SECONDS);

        LOG.info("PhrRetentionScheduler started [retention={}h, consentExpiry={}m, auditSync={}m]",
                retentionInterval.toHours(), consentExpiryInterval.toMinutes(), auditSyncInterval.toMinutes());
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        if (!running.compareAndSet(true, false)) {
            return Promise.complete();
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOG.info("PhrRetentionScheduler stopped");
        return Promise.complete();
    }

    @Override
    public boolean isHealthy() {
        return running.get() && scheduler != null && !scheduler.isShutdown();
    }

    @Override
    public String getName() {
        return "phr-retention-scheduler";
    }

    // -------------------------------------------------------------------------
    // Job implementations
    // -------------------------------------------------------------------------

    /**
     * Scans for patients whose 25-year retention period has elapsed and
     * initiates the deletion workflow for each eligible patient.
     */
    void runRetentionScan() {
        try {
            // Nepal Health Records Act 2081 §22: 25-year minimum retention
            Instant cutoff = Instant.now().minusSeconds(25L * 365 * 24 * 3600);
            LOG.debug("PhrRetentionScheduler: running retention scan [cutoff={}]", cutoff);

            // In production the tenantId would come from a tenant iterator; use sentinel here
            List<String> eligible = eligibilityPort.listRetentionEligiblePatients("*", cutoff);

            if (eligible.isEmpty()) {
                LOG.debug("PhrRetentionScheduler: retention scan complete — no eligible patients");
                return;
            }

            LOG.info("PhrRetentionScheduler: found {} retention-eligible patient(s)", eligible.size());

            for (String patientId : eligible) {
                PatientDeletionWorkflow.DeletionRequest request = new PatientDeletionWorkflow.DeletionRequest(
                        UUID.randomUUID(),
                        "*",
                        patientId,
                        "phr-retention-scheduler",
                        Instant.now(),
                        "Nepal Health Records Act 2081 §22");

                deletionWorkflow.execute(request, List.of())
                        .whenResult(report -> LOG.info(
                                "PhrRetentionScheduler: deletion workflow complete [patient={} decisions={}]",
                                patientId, report.decisions().size()))
                        .whenException(ex -> LOG.error(
                                "PhrRetentionScheduler: deletion workflow failed [patient={}]", patientId, ex));
            }
        } catch (Exception e) {
            LOG.error("PhrRetentionScheduler: retention scan error", e);
        }
    }

    /**
     * Marks consent records as {@code EXPIRED} and hard-deletes those past
     * the configured grace window.
     */
    void runConsentExpiryScan() {
        try {
            int marked = consentExpiryPort.markExpiredConsents();
            int purged = consentExpiryPort.purgeExpiredConsents(consentGracePeriodDays);
            if (marked > 0 || purged > 0) {
                LOG.info("PhrRetentionScheduler: consent expiry scan [marked={} purged={}]", marked, purged);
            } else {
                LOG.debug("PhrRetentionScheduler: consent expiry scan — nothing to do");
            }
        } catch (Exception e) {
            LOG.error("PhrRetentionScheduler: consent expiry scan error", e);
        }
    }

    /**
     * Replays the outstanding audit-event backlog from the durable store.
     */
    void runAuditSync() {
        try {
            int synced = auditSyncPort.syncAuditChain();
            if (synced > 0) {
                LOG.info("PhrRetentionScheduler: audit chain sync — synced {} event(s)", synced);
            } else {
                LOG.debug("PhrRetentionScheduler: audit chain sync — nothing to sync");
            }
        } catch (Exception e) {
            LOG.error("PhrRetentionScheduler: audit chain sync error", e);
        }
    }
}
