package com.ghatana.appplatform.secrets.rotation;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;
import com.ghatana.platform.audit.AuditBusPort;
import com.ghatana.platform.audit.AuditEvent;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Scheduled service that rotates secrets nearing their expiry.
 *
 * <p>Runs at a configurable interval, scans all secrets under a configured prefix
 * using the provider's {@code listSecrets} capability, and rotates any that are
 * expired or within the rotation threshold.
 *
 * <p>The scheduler uses a single-thread daemon executor to avoid blocking the eventloop.
 *
 * @doc.type class
 * @doc.purpose Automatic secret rotation scheduler (STORY-K14)
 * @doc.layer product
 * @doc.pattern Service
 */
public class SecretRotationScheduler {

    private static final Logger LOG = Logger.getLogger(SecretRotationScheduler.class.getName());

    private final SecretProvider secretProvider;
    private final String monitoredPrefix;     // e.g. "/data-cloud/prod/"
    private final long rotationThresholdDays; // rotate if < N days to expiry
    private final Eventloop eventloop;
    private final Executor blockingExecutor;
    private final AuditBusPort audit;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private long intervalMs;

    public SecretRotationScheduler(SecretProvider secretProvider,
                                   String monitoredPrefix,
                                   long rotationThresholdDays,
                                   AuditBusPort audit,
                                   Eventloop eventloop,
                                   Executor blockingExecutor) {
        this.secretProvider = secretProvider;
        this.monitoredPrefix = monitoredPrefix;
        this.rotationThresholdDays = rotationThresholdDays;
        this.audit = audit;
        this.eventloop = eventloop;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * Start the scheduler, checking for rotation every {@code intervalHours} hours.
     */
    public void start(long intervalHours) {
        this.intervalMs = intervalHours * 3_600_000L;
        running.set(true);
        eventloop.delay(0, this::scheduleNextCheck);
        LOG.info("[SecretRotationScheduler] Started — checking every " + intervalHours + "h prefix=" + monitoredPrefix);
    }

    /** Stop the scheduler gracefully. */
    public void stop() {
        running.set(false);
        LOG.info("[SecretRotationScheduler] Stopped");
    }

    private void scheduleNextCheck() {
        if (!running.get()) return;
        Promise.ofBlocking(blockingExecutor, () -> {
            runRotationCheck();
            return null;
        }).whenComplete(() -> {
            if (running.get()) {
                eventloop.delay(intervalMs, this::scheduleNextCheck);
            }
        });
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void runRotationCheck() {
        try {
            List<String> paths = secretProvider.listSecrets(monitoredPrefix).getResult();
            if (paths == null) return;
            for (String path : paths) {
                try {
                    checkAndRotate(path);
                } catch (Exception e) {
                    LOG.warning("[SecretRotationScheduler] Failed to check/rotate path=" + path
                        + " error=" + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.warning("[SecretRotationScheduler] Rotation check failed: " + e.getMessage());
        }
    }

    private void checkAndRotate(String path) {
        SecretValue secret = secretProvider.getSecret(path).getResult();
        if (secret == null) return;

        Instant expiresAt = secret.expiresAt();
        if (expiresAt == null) return; // non-expiring secrets are not auto-rotated

        long daysToExpiry = java.time.Duration.between(Instant.now(), expiresAt).toDays();
        if (daysToExpiry <= rotationThresholdDays) {
            LOG.info("[SecretRotationScheduler] Rotating path=" + path + " daysToExpiry=" + daysToExpiry);
            secretProvider.rotateSecret(path).getResult();
            LOG.info("[SecretRotationScheduler] Rotated path=" + path);
            audit.emit(AuditEvent.builder()
                    .tenantId("SYSTEM")
                    .eventType("SECRET_ROTATED")
                    .principal("SecretRotationScheduler")
                    .resourceType("secret")
                    .resourceId(path)
                    .success(true)
                    .details(Map.of("daysToExpiry", String.valueOf(daysToExpiry)))
                    .build());
        }
    }
}
