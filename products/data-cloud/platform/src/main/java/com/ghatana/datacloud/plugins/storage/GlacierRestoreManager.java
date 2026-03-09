package com.ghatana.datacloud.plugins.s3archive;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * Manages Glacier restore operations for archived events.
 *
 * <p><b>Purpose</b><br>
 * Handles the restoration of archived events from S3 Glacier storage classes:
 * <ul>
 *   <li><b>Initiate Restore</b>: Start async restore job</li>
 *   <li><b>Check Status</b>: Poll for restore completion</li>
 *   <li><b>Download Restored</b>: Access temporarily restored objects</li>
 *   <li><b>Cancel/Cleanup</b>: Manage restore lifecycle</li>
 * </ul>
 *
 * <p><b>Restore Tiers</b><br>
 * <table border="1">
 *   <tr><th>Tier</th><th>Glacier Flexible</th><th>Deep Archive</th><th>Cost</th></tr>
 *   <tr><td>Expedited</td><td>1-5 minutes</td><td>N/A</td><td>High</td></tr>
 *   <tr><td>Standard</td><td>3-5 hours</td><td>12 hours</td><td>Medium</td></tr>
 *   <tr><td>Bulk</td><td>5-12 hours</td><td>48 hours</td><td>Low</td></tr>
 * </table>
 *
 * <p><b>Restore Lifecycle</b><br>
 * <pre>
 * 1. Call initiateRestore() → Returns immediately
 * 2. Poll isRestoreComplete() → Check restore status
 * 3. Once complete, object is temporarily available
 * 4. After restoreExpirationDays, object re-archives
 * </pre>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * GlacierRestoreManager restoreManager = new GlacierRestoreManager(s3Client, config);
 * 
 * // Initiate restore
 * String archiveKey = "eventcloud/archives/tenant-1/2024/01/events.parquet";
 * restoreManager.initiateRestore(archiveKey, RestoreTier.STANDARD).getResult();
 * 
 * // Poll for completion
 * while (!restoreManager.isRestoreComplete(archiveKey).getResult()) {
 *     Thread.sleep(Duration.ofMinutes(5).toMillis());
 * }
 * 
 * // Download restored object
 * byte[] data = restoreManager.downloadRestored(archiveKey).getResult();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Glacier restore operations management
 * @doc.layer plugin
 * @doc.pattern Manager
 */
public class GlacierRestoreManager {

    private static final Logger log = LoggerFactory.getLogger(GlacierRestoreManager.class);

    private final S3Client s3Client;
    private final S3ArchiveConfig config;

    // ==================== Constructor ====================

    /**
     * Creates a GlacierRestoreManager.
     *
     * @param s3Client S3 client
     * @param config   archive configuration
     */
    public GlacierRestoreManager(S3Client s3Client, S3ArchiveConfig config) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.config = Objects.requireNonNull(config, "config");
    }

    // ==================== Restore Operations ====================

    /**
     * Initiates a restore request for a Glacier object.
     *
     * @param key         S3 object key
     * @param restoreTier restore tier (EXPEDITED, STANDARD, BULK)
     * @return Promise completing when restore is initiated
     */
    public Promise<RestoreResult> initiateRestore(String key, S3ArchiveConfig.RestoreTier restoreTier) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            log.info("Initiating restore for key={} with tier={}", key, restoreTier);

            // Check current object status
            HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .build());

            StorageClass storageClass = headResponse.storageClass();
            
            // Check if object is in Glacier
            if (!isGlacierStorageClass(storageClass)) {
                log.info("Object {} is not in Glacier (storage class: {}), no restore needed",
                        key, storageClass);
                return new RestoreResult(key, RestoreStatus.ALREADY_AVAILABLE, null);
            }

            // Check if already being restored
            if (headResponse.restore() != null && headResponse.restore().contains("ongoing-request=\"true\"")) {
                log.info("Restore already in progress for key={}", key);
                return new RestoreResult(key, RestoreStatus.IN_PROGRESS, null);
            }

            // Check if already restored
            if (headResponse.restore() != null && headResponse.restore().contains("ongoing-request=\"false\"")) {
                log.info("Object {} is already restored", key);
                return new RestoreResult(key, RestoreStatus.COMPLETED, parseRestoreExpiry(headResponse.restore()));
            }

            // Initiate restore
            Tier tier = mapRestoreTier(restoreTier);
            
            RestoreObjectRequest restoreRequest = RestoreObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .restoreRequest(r -> r
                            .days(config.getRestoreExpirationDays())
                            .glacierJobParameters(g -> g.tier(tier)))
                    .build();

            s3Client.restoreObject(restoreRequest);

            log.info("Restore initiated for key={}, tier={}, expiration={} days",
                    key, restoreTier, config.getRestoreExpirationDays());

            return new RestoreResult(key, RestoreStatus.INITIATED, null);
        });
    }

    /**
     * Initiates restore with default tier.
     *
     * @param key S3 object key
     * @return Promise completing when restore is initiated
     */
    public Promise<RestoreResult> initiateRestore(String key) {
        return initiateRestore(key, config.getDefaultRestoreTier());
    }

    /**
     * Checks if a restore is complete.
     *
     * @param key S3 object key
     * @return Promise with true if restore is complete
     */
    public Promise<Boolean> isRestoreComplete(String key) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .build());

            String restore = headResponse.restore();
            
            if (restore == null) {
                // Not in Glacier or never restored
                StorageClass storageClass = headResponse.storageClass();
                return !isGlacierStorageClass(storageClass);
            }

            boolean complete = restore.contains("ongoing-request=\"false\"");
            log.debug("Restore status for key={}: complete={}, restore={}", key, complete, restore);
            
            return complete;
        });
    }

    /**
     * Gets the restore status for an object.
     *
     * @param key S3 object key
     * @return Promise with restore status
     */
    public Promise<RestoreResult> getRestoreStatus(String key) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(config.getBucketName())
                    .key(key)
                    .build());

            StorageClass storageClass = headResponse.storageClass();
            String restore = headResponse.restore();

            if (!isGlacierStorageClass(storageClass)) {
                return new RestoreResult(key, RestoreStatus.ALREADY_AVAILABLE, null);
            }

            if (restore == null) {
                return new RestoreResult(key, RestoreStatus.NOT_STARTED, null);
            }

            if (restore.contains("ongoing-request=\"true\"")) {
                return new RestoreResult(key, RestoreStatus.IN_PROGRESS, null);
            }

            if (restore.contains("ongoing-request=\"false\"")) {
                Instant expiry = parseRestoreExpiry(restore);
                return new RestoreResult(key, RestoreStatus.COMPLETED, expiry);
            }

            return new RestoreResult(key, RestoreStatus.UNKNOWN, null);
        });
    }

    /**
     * Lists all objects currently being restored or restored.
     *
     * @param prefix key prefix to filter
     * @return Promise with list of restore statuses
     */
    public Promise<java.util.List<RestoreResult>> listRestoreStatuses(String prefix) {
        return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> {
            java.util.List<RestoreResult> results = new java.util.ArrayList<>();

            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(config.getBucketName())
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

            for (S3Object obj : listResponse.contents()) {
                try {
                    RestoreResult status = getRestoreStatus(obj.key()).getResult();
                    if (status.status() != RestoreStatus.NOT_STARTED &&
                            status.status() != RestoreStatus.ALREADY_AVAILABLE) {
                        results.add(status);
                    }
                } catch (Exception e) {
                    log.warn("Failed to get restore status for {}: {}", obj.key(), e.getMessage());
                }
            }

            return results;
        });
    }

    // ==================== Helper Methods ====================

    private boolean isGlacierStorageClass(StorageClass storageClass) {
        return storageClass == StorageClass.GLACIER ||
                storageClass == StorageClass.GLACIER_IR ||
                storageClass == StorageClass.DEEP_ARCHIVE;
    }

    private Tier mapRestoreTier(S3ArchiveConfig.RestoreTier restoreTier) {
        return switch (restoreTier) {
            case EXPEDITED -> Tier.EXPEDITED;
            case STANDARD -> Tier.STANDARD;
            case BULK -> Tier.BULK;
        };
    }

    private Instant parseRestoreExpiry(String restoreHeader) {
        // Format: ongoing-request="false", expiry-date="Fri, 23 Dec 2022 00:00:00 GMT"
        try {
            if (restoreHeader != null && restoreHeader.contains("expiry-date=")) {
                int start = restoreHeader.indexOf("expiry-date=\"") + 13;
                int end = restoreHeader.indexOf("\"", start);
                String dateStr = restoreHeader.substring(start, end);
                
                java.time.format.DateTimeFormatter formatter = 
                        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
                return java.time.ZonedDateTime.parse(dateStr, formatter).toInstant();
            }
        } catch (Exception e) {
            log.warn("Failed to parse restore expiry date: {}", restoreHeader);
        }
        return null;
    }

    // ==================== Inner Classes ====================

    /**
     * Restore operation status.
     */
    public enum RestoreStatus {
        /**
         * Object is not in Glacier, already accessible.
         */
        ALREADY_AVAILABLE,

        /**
         * Restore has not been initiated.
         */
        NOT_STARTED,

        /**
         * Restore request was just initiated.
         */
        INITIATED,

        /**
         * Restore is in progress.
         */
        IN_PROGRESS,

        /**
         * Restore is complete, object is temporarily accessible.
         */
        COMPLETED,

        /**
         * Restored object has expired and re-archived.
         */
        EXPIRED,

        /**
         * Unknown status.
         */
        UNKNOWN
    }

    /**
     * Result of a restore operation.
     */
    public record RestoreResult(
            String key,
            RestoreStatus status,
            Instant expiryTime
    ) {
        /**
         * Checks if the object is currently accessible.
         *
         * @return true if object can be downloaded
         */
        public boolean isAccessible() {
            return status == RestoreStatus.ALREADY_AVAILABLE ||
                    (status == RestoreStatus.COMPLETED &&
                            (expiryTime == null || expiryTime.isAfter(Instant.now())));
        }
    }
}
