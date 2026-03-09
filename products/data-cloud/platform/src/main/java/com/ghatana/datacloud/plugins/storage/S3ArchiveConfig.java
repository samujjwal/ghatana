package com.ghatana.datacloud.plugins.s3archive;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.util.Objects;

/**
 * Configuration for the S3 L4 (COLD tier) Archive Plugin.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates all configuration settings for S3 archival storage including:
 * <ul>
 *   <li>Bucket and region settings</li>
 *   <li>Encryption configuration (SSE-S3, SSE-KMS, client-side)</li>
 *   <li>Lifecycle transition policies</li>
 *   <li>Glacier restore settings</li>
 *   <li>Compliance and retention settings</li>
 * </ul>
 *
 * <p><b>Storage Classes</b><br>
 * <ul>
 *   <li><b>STANDARD</b>: General-purpose, frequent access</li>
 *   <li><b>INTELLIGENT_TIERING</b>: Automatic cost optimization</li>
 *   <li><b>GLACIER_INSTANT</b>: Archive with millisecond access</li>
 *   <li><b>GLACIER_FLEXIBLE</b>: Archive with 3-5 hour access</li>
 *   <li><b>GLACIER_DEEP</b>: Lowest cost, 12-48 hour access</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Production configuration with KMS encryption
 * S3ArchiveConfig prodConfig = S3ArchiveConfig.builder()
 *     .bucketName("eventcloud-archive-prod")
 *     .region("us-east-1")
 *     .encryptionType(EncryptionType.SSE_KMS)
 *     .kmsKeyId("arn:aws:kms:us-east-1:123456789012:key/...")
 *     .initialStorageClass(StorageClass.INTELLIGENT_TIERING)
 *     .glacierTransitionDays(90)
 *     .deepArchiveTransitionDays(365)
 *     .retentionYears(7)
 *     .objectLockEnabled(true)
 *     .build();
 * 
 * // Development configuration with LocalStack
 * S3ArchiveConfig devConfig = S3ArchiveConfig.forLocalStack(
 *     "eventcloud-archive-dev", "http://localhost:4566");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Configuration for S3 L4 archive plugin
 * @doc.layer plugin
 * @doc.pattern ValueObject, Builder
 */
@Getter
@Builder(toBuilder = true)
@ToString
public class S3ArchiveConfig {

    // ==================== S3 Bucket Configuration ====================

    /**
     * S3 bucket name for archive storage.
     * Must be globally unique.
     */
    private final String bucketName;

    /**
     * AWS region for the S3 bucket.
     */
    @Builder.Default
    private final String region = "us-east-1";

    /**
     * S3 endpoint override (for LocalStack, MinIO).
     */
    private final String endpointOverride;

    /**
     * Enable path-style access (required for some S3-compatible stores).
     */
    @Builder.Default
    private final boolean pathStyleAccess = false;

    /**
     * Prefix for all archive objects (e.g., "eventcloud/archives/").
     */
    @Builder.Default
    private final String keyPrefix = "eventcloud/archives/";

    // ==================== Encryption Configuration ====================

    /**
     * Server-side encryption type.
     */
    @Builder.Default
    private final EncryptionType encryptionType = EncryptionType.SSE_S3;

    /**
     * KMS key ID for SSE-KMS encryption.
     * Required when encryptionType is SSE_KMS.
     */
    private final String kmsKeyId;

    /**
     * Enable bucket key for KMS (reduces KMS costs).
     */
    @Builder.Default
    private final boolean bucketKeyEnabled = true;

    // ==================== Storage Class Configuration ====================

    /**
     * Initial storage class for uploaded objects.
     */
    @Builder.Default
    private final StorageClass initialStorageClass = StorageClass.INTELLIGENT_TIERING;

    /**
     * Days after upload to transition to Glacier Instant Retrieval.
     * Set to 0 to skip this tier.
     */
    @Builder.Default
    private final int glacierInstantTransitionDays = 30;

    /**
     * Days after upload to transition to Glacier Flexible Retrieval.
     * Set to 0 to skip this tier.
     */
    @Builder.Default
    private final int glacierTransitionDays = 90;

    /**
     * Days after upload to transition to Glacier Deep Archive.
     * Set to 0 to skip this tier.
     */
    @Builder.Default
    private final int deepArchiveTransitionDays = 365;

    // ==================== Retention Configuration ====================

    /**
     * Retention period in years (for compliance).
     * Objects cannot be deleted before this period.
     */
    @Builder.Default
    private final int retentionYears = 7;

    /**
     * Enable S3 Object Lock (WORM compliance).
     * Once enabled, cannot be disabled.
     */
    @Builder.Default
    private final boolean objectLockEnabled = false;

    /**
     * Object Lock retention mode.
     */
    @Builder.Default
    private final ObjectLockMode objectLockMode = ObjectLockMode.GOVERNANCE;

    // ==================== Restore Configuration ====================

    /**
     * Default restore tier for Glacier objects.
     */
    @Builder.Default
    private final RestoreTier defaultRestoreTier = RestoreTier.STANDARD;

    /**
     * Days to keep restored objects accessible before re-archiving.
     */
    @Builder.Default
    private final int restoreExpirationDays = 7;

    /**
     * Timeout for waiting on restore completion.
     */
    @Builder.Default
    private final Duration restoreTimeout = Duration.ofHours(12);

    // ==================== Upload Configuration ====================

    /**
     * Multipart upload threshold in bytes (default 100MB).
     */
    @Builder.Default
    private final long multipartThresholdBytes = 100 * 1024 * 1024L;

    /**
     * Part size for multipart uploads in bytes (default 10MB).
     */
    @Builder.Default
    private final long multipartPartSizeBytes = 10 * 1024 * 1024L;

    /**
     * Maximum concurrent upload parts.
     */
    @Builder.Default
    private final int maxConcurrentUploads = 4;

    /**
     * Maximum events per archive file.
     */
    @Builder.Default
    private final int maxEventsPerArchive = 100_000;

    /**
     * Target archive file size in bytes (default 256MB).
     */
    @Builder.Default
    private final long targetArchiveSizeBytes = 256 * 1024 * 1024L;

    // ==================== Metrics Configuration ====================

    /**
     * Enable metrics collection.
     */
    @Builder.Default
    private final boolean metricsEnabled = true;

    /**
     * Metrics prefix for all S3 archive metrics.
     */
    @Builder.Default
    private final String metricsPrefix = "eventcloud.s3archive";

    // ==================== Enums ====================

    /**
     * Server-side encryption types.
     */
    public enum EncryptionType {
        /**
         * No encryption.
         */
        NONE,

        /**
         * S3-managed keys (AES-256).
         */
        SSE_S3,

        /**
         * AWS KMS-managed keys.
         */
        SSE_KMS,

        /**
         * Customer-provided keys (not recommended).
         */
        SSE_C
    }

    /**
     * S3 storage classes.
     */
    public enum StorageClass {
        /**
         * General-purpose storage for frequently accessed data.
         */
        STANDARD,

        /**
         * Reduced redundancy (not recommended).
         */
        REDUCED_REDUNDANCY,

        /**
         * Automatic cost optimization based on access patterns.
         */
        INTELLIGENT_TIERING,

        /**
         * Infrequent access, lower cost, retrieval fee.
         */
        STANDARD_IA,

        /**
         * Single-AZ infrequent access.
         */
        ONEZONE_IA,

        /**
         * Archive with millisecond retrieval.
         */
        GLACIER_INSTANT_RETRIEVAL,

        /**
         * Archive with minutes to hours retrieval.
         */
        GLACIER_FLEXIBLE_RETRIEVAL,

        /**
         * Lowest cost archive, hours retrieval.
         */
        GLACIER_DEEP_ARCHIVE
    }

    /**
     * Glacier restore tiers.
     */
    public enum RestoreTier {
        /**
         * Expedited: 1-5 minutes (Glacier Flexible only).
         */
        EXPEDITED,

        /**
         * Standard: 3-5 hours (Glacier Flexible), 12 hours (Deep Archive).
         */
        STANDARD,

        /**
         * Bulk: 5-12 hours (Glacier Flexible), 48 hours (Deep Archive).
         */
        BULK
    }

    /**
     * S3 Object Lock retention modes.
     */
    public enum ObjectLockMode {
        /**
         * Governance mode: Users with special permissions can delete.
         */
        GOVERNANCE,

        /**
         * Compliance mode: Nobody can delete, including root user.
         */
        COMPLIANCE
    }

    // ==================== Factory Methods ====================

    /**
     * Creates a default configuration.
     * Requires bucketName to be set.
     *
     * @param bucketName S3 bucket name
     * @return default configuration
     */
    public static S3ArchiveConfig defaults(String bucketName) {
        return S3ArchiveConfig.builder()
                .bucketName(Objects.requireNonNull(bucketName))
                .build();
    }

    /**
     * Creates a configuration for LocalStack testing.
     *
     * @param bucketName       S3 bucket name
     * @param localstackEndpoint LocalStack endpoint (e.g., http://localhost:4566)
     * @return LocalStack configuration
     */
    public static S3ArchiveConfig forLocalStack(String bucketName, String localstackEndpoint) {
        return S3ArchiveConfig.builder()
                .bucketName(Objects.requireNonNull(bucketName))
                .endpointOverride(Objects.requireNonNull(localstackEndpoint))
                .pathStyleAccess(true)
                .encryptionType(EncryptionType.NONE)
                .initialStorageClass(StorageClass.STANDARD)
                .glacierInstantTransitionDays(0)
                .glacierTransitionDays(0)
                .deepArchiveTransitionDays(0)
                .objectLockEnabled(false)
                .build();
    }

    /**
     * Creates a production configuration with KMS encryption.
     *
     * @param bucketName S3 bucket name
     * @param region     AWS region
     * @param kmsKeyId   KMS key ARN
     * @return production configuration
     */
    public static S3ArchiveConfig forProduction(String bucketName, String region, String kmsKeyId) {
        return S3ArchiveConfig.builder()
                .bucketName(Objects.requireNonNull(bucketName))
                .region(Objects.requireNonNull(region))
                .encryptionType(EncryptionType.SSE_KMS)
                .kmsKeyId(Objects.requireNonNull(kmsKeyId))
                .bucketKeyEnabled(true)
                .initialStorageClass(StorageClass.INTELLIGENT_TIERING)
                .glacierInstantTransitionDays(30)
                .glacierTransitionDays(90)
                .deepArchiveTransitionDays(365)
                .retentionYears(7)
                .objectLockEnabled(true)
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .build();
    }

    // ==================== Validation ====================

    /**
     * Validates the configuration.
     *
     * @throws IllegalStateException if configuration is invalid
     */
    public void validate() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException("bucketName is required");
        }

        if (region == null || region.isBlank()) {
            throw new IllegalStateException("region is required");
        }

        if (encryptionType == EncryptionType.SSE_KMS && (kmsKeyId == null || kmsKeyId.isBlank())) {
            throw new IllegalStateException("kmsKeyId is required for SSE_KMS encryption");
        }

        if (retentionYears < 0) {
            throw new IllegalStateException("retentionYears cannot be negative");
        }

        if (glacierTransitionDays > 0 && glacierInstantTransitionDays > 0 &&
                glacierTransitionDays <= glacierInstantTransitionDays) {
            throw new IllegalStateException(
                    "glacierTransitionDays must be greater than glacierInstantTransitionDays");
        }

        if (deepArchiveTransitionDays > 0 && glacierTransitionDays > 0 &&
                deepArchiveTransitionDays <= glacierTransitionDays) {
            throw new IllegalStateException(
                    "deepArchiveTransitionDays must be greater than glacierTransitionDays");
        }

        if (multipartThresholdBytes <= 0) {
            throw new IllegalStateException("multipartThresholdBytes must be positive");
        }

        if (multipartPartSizeBytes < 5 * 1024 * 1024L) {
            throw new IllegalStateException("multipartPartSizeBytes must be at least 5MB");
        }
    }

    /**
     * Gets the S3 storage class enum value.
     *
     * @return AWS SDK StorageClass value
     */
    public software.amazon.awssdk.services.s3.model.StorageClass getS3StorageClass() {
        return switch (initialStorageClass) {
            case STANDARD -> software.amazon.awssdk.services.s3.model.StorageClass.STANDARD;
            case REDUCED_REDUNDANCY -> software.amazon.awssdk.services.s3.model.StorageClass.REDUCED_REDUNDANCY;
            case INTELLIGENT_TIERING -> software.amazon.awssdk.services.s3.model.StorageClass.INTELLIGENT_TIERING;
            case STANDARD_IA -> software.amazon.awssdk.services.s3.model.StorageClass.STANDARD_IA;
            case ONEZONE_IA -> software.amazon.awssdk.services.s3.model.StorageClass.ONEZONE_IA;
            case GLACIER_INSTANT_RETRIEVAL -> software.amazon.awssdk.services.s3.model.StorageClass.GLACIER_IR;
            case GLACIER_FLEXIBLE_RETRIEVAL -> software.amazon.awssdk.services.s3.model.StorageClass.GLACIER;
            case GLACIER_DEEP_ARCHIVE -> software.amazon.awssdk.services.s3.model.StorageClass.DEEP_ARCHIVE;
        };
    }

    /**
     * Gets the Glacier restore tier enum value.
     *
     * @return AWS SDK GlacierJobTier value
     */
    public software.amazon.awssdk.services.s3.model.Tier getGlacierRestoreTier() {
        return switch (defaultRestoreTier) {
            case EXPEDITED -> software.amazon.awssdk.services.s3.model.Tier.EXPEDITED;
            case STANDARD -> software.amazon.awssdk.services.s3.model.Tier.STANDARD;
            case BULK -> software.amazon.awssdk.services.s3.model.Tier.BULK;
        };
    }
}
