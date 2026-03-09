package com.ghatana.datacloud;

import lombok.*;

import java.io.Serializable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Retention policy configuration for collections.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines how long records should be retained and when/how they should be
 * archived or deleted. Supports:
 * <ul>
 * <li>Time-based retention (keep for X days/months/years)</li>
 * <li>Count-based retention (keep last N records)</li>
 * <li>Size-based retention (keep up to X GB)</li>
 * <li>Tiered storage (hot/warm/cold/archive)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Simple time-based retention
 * RetentionPolicy policy = RetentionPolicy.keepFor(Duration.ofDays(90));
 *
 * // Multi-tier retention
 * RetentionPolicy tiered = RetentionPolicy.builder()
 *     .hotDuration(Duration.ofDays(7))
 *     .warmDuration(Duration.ofDays(30))
 *     .coldDuration(Duration.ofDays(365))
 *     .archiveAfter(Duration.ofDays(365))
 *     .deleteAfter(Duration.ofDays(2 * 365))
 *     .build();
 *
 * // Count-based for event streams
 * RetentionPolicy countBased = RetentionPolicy.keepLastN(1_000_000);
 * }</pre>
 *
 * @see Collection
 * @doc.type class
 * @doc.purpose Retention configuration
 * @doc.layer core
 * @doc.pattern Value Object
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetentionPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Retention strategy.
     */
    @Builder.Default
    private RetentionStrategy strategy = RetentionStrategy.TIME_BASED;

    /**
     * Duration to keep records (for TIME_BASED strategy).
     */
    private Duration retentionDuration;

    /**
     * Maximum number of records to keep (for COUNT_BASED strategy).
     */
    private Long maxRecordCount;

    /**
     * Maximum size in bytes (for SIZE_BASED strategy).
     */
    private Long maxSizeBytes;

    /**
     * Duration to keep records in HOT tier before moving to WARM.
     */
    private Duration hotDuration;

    /**
     * Duration to keep records in WARM tier before moving to COLD.
     */
    private Duration warmDuration;

    /**
     * Duration to keep records in COLD tier before archiving.
     */
    private Duration coldDuration;

    /**
     * Duration after which to archive records.
     */
    private Duration archiveAfter;

    /**
     * Duration after which to delete records.
     */
    private Duration deleteAfter;

    /**
     * Whether to compress data in cold/archive tiers.
     */
    @Builder.Default
    private Boolean compressArchived = true;

    /**
     * Target storage tier for archived data.
     */
    @Builder.Default
    private String archiveStorageTier = "S3_GLACIER";

    /**
     * Whether retention is enabled.
     */
    @Builder.Default
    private Boolean enabled = true;

    /**
     * Retention strategies.
     */
    public enum RetentionStrategy {
        /**
         * Retain based on age.
         */
        TIME_BASED,
        /**
         * Retain last N records.
         */
        COUNT_BASED,
        /**
         * Retain up to max size.
         */
        SIZE_BASED,
        /**
         * No automatic retention.
         */
        NONE
    }

    // ==================== Factory Methods ====================
    /**
     * Creates a time-based retention policy.
     *
     * @param duration How long to keep records
     * @return RetentionPolicy
     */
    public static RetentionPolicy keepFor(Duration duration) {
        return builder()
                .strategy(RetentionStrategy.TIME_BASED)
                .retentionDuration(duration)
                .deleteAfter(duration)
                .build();
    }

    /**
     * Creates a time-based retention policy with archive.
     *
     * @param activeDuration How long in active storage
     * @param archiveDuration How long in archive before delete
     * @return RetentionPolicy
     */
    public static RetentionPolicy keepThenArchive(Duration activeDuration, Duration archiveDuration) {
        return builder()
                .strategy(RetentionStrategy.TIME_BASED)
                .retentionDuration(activeDuration.plus(archiveDuration))
                .archiveAfter(activeDuration)
                .deleteAfter(activeDuration.plus(archiveDuration))
                .build();
    }

    /**
     * Creates a count-based retention policy.
     *
     * @param maxCount Maximum records to keep
     * @return RetentionPolicy
     */
    public static RetentionPolicy keepLastN(long maxCount) {
        return builder()
                .strategy(RetentionStrategy.COUNT_BASED)
                .maxRecordCount(maxCount)
                .build();
    }

    /**
     * Creates a size-based retention policy.
     *
     * @param maxSizeBytes Maximum size in bytes
     * @return RetentionPolicy
     */
    public static RetentionPolicy keepUpToSize(long maxSizeBytes) {
        return builder()
                .strategy(RetentionStrategy.SIZE_BASED)
                .maxSizeBytes(maxSizeBytes)
                .build();
    }

    /**
     * Creates a retention policy with no automatic deletion.
     *
     * @return RetentionPolicy
     */
    public static RetentionPolicy keepForever() {
        return builder()
                .strategy(RetentionStrategy.NONE)
                .enabled(false)
                .build();
    }

    /**
     * Creates a tiered retention policy.
     *
     * @param hot Days in hot tier
     * @param warm Days in warm tier
     * @param cold Days in cold tier
     * @param archiveDays Days in archive before delete
     * @return RetentionPolicy
     */
    public static RetentionPolicy tiered(int hot, int warm, int cold, int archiveDays) {
        Duration hotDur = Duration.ofDays(hot);
        Duration warmDur = Duration.ofDays(warm);
        Duration coldDur = Duration.ofDays(cold);
        Duration archiveDur = Duration.ofDays(archiveDays);

        return builder()
                .strategy(RetentionStrategy.TIME_BASED)
                .hotDuration(hotDur)
                .warmDuration(warmDur)
                .coldDuration(coldDur)
                .archiveAfter(hotDur.plus(warmDur).plus(coldDur))
                .deleteAfter(hotDur.plus(warmDur).plus(coldDur).plus(archiveDur))
                .retentionDuration(hotDur.plus(warmDur).plus(coldDur).plus(archiveDur))
                .build();
    }

    // ==================== Helper Methods ====================
    /**
     * Checks if this policy uses tiered storage.
     */
    public boolean isTiered() {
        return hotDuration != null || warmDuration != null || coldDuration != null;
    }

    /**
     * Gets the total retention duration.
     */
    public Duration getTotalDuration() {
        if (deleteAfter != null) {
            return deleteAfter;
        }
        if (retentionDuration != null) {
            return retentionDuration;
        }
        return Duration.ZERO;
    }
}
