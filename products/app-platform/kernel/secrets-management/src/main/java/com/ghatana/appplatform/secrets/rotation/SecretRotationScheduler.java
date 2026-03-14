package com.ghatana.appplatform.secrets.rotation;

import com.ghatana.appplatform.secrets.domain.SecretMetadata;
import com.ghatana.appplatform.secrets.domain.SecretValue;
import com.ghatana.appplatform.secrets.port.SecretProvider;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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
    private final ScheduledExecutorService scheduler;

    public SecretRotationScheduler(SecretProvider secretProvider,
                                   String monitoredPrefix,
                                   long rotationThresholdDays) {
        this.secretProvider = secretProvider;
        this.monitoredPrefix = monitoredPrefix;
        this.rotationThresholdDays = rotationThresholdDays;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "secret-rotation-scheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Start the scheduler, checking for rotation every {@code intervalHours} hours.
     */
    public void start(long intervalHours) {
        scheduler.scheduleAtFixedRate(this::runRotationCheck, 0, intervalHours, TimeUnit.HOURS);
        LOG.info("[SecretRotationScheduler] Started — checking every " + intervalHours + "h prefix=" + monitoredPrefix);
    }

    /** Stop the scheduler gracefully. */
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("[SecretRotationScheduler] Stopped");
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
        }
    }
}
