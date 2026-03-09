package com.ghatana.datacloud.entity.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Policy for managing span retention across storage tiers.
 *
 * <p><b>Purpose</b><br>
 * Defines how traces are moved between hot/warm/cold storage based on age,
 * with configurable TTL and archival rules per tier.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RetentionPolicy policy = RetentionPolicy.builder()
 *     .hotStorage(Duration.ofDays(7))
 *     .warmStorage(Duration.ofDays(30))
 *     .coldStorage(Duration.ofDays(365))
 *     .archiveToS3(true)
 *     .build();
 *
 * if (policy.shouldArchiveSpan(span)) {
 *     archiveToS3(span);
 * }
 * }</pre>
 *
 * <p><b>Storage Tiers</b><br>
 * - <b>Hot (0-7d):</b> In-memory, highly indexed, query cache enabled
 * - <b>Warm (7-30d):</b> Local SSD, basic indexing, query cache disabled
 * - <b>Cold (30-365d):</b> Cloud storage (S3/GCS), no indexing, full scans
 * - <b>Archive (>365d):</b> Deep archive (S3 Glacier), retrieval time hours
 *
 * <p><b>Archival Strategies</b><br>
 * - <b>Time-based:</b> Move to next tier after TTL expires
 * - <b>Size-based:</b> Move when storage tier exceeds max size
 * - <b>Hybrid:</b> Combine time and size triggers
 *
 * @doc.type class
 * @doc.purpose Trace retention and archival policy configuration
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class RetentionPolicy {

    private final Duration hotStorageTtl;           // 0-7 days: in-memory
    private final Duration warmStorageTtl;          // 7-30 days: SSD
    private final Duration coldStorageTtl;          // 30-365 days: S3
    private final Duration archiveStorageTtl;       // 365+ days: Glacier
    private final boolean archiveToS3;              // Enable S3 archival
    private final String s3BucketName;              // S3 bucket for archival
    private final String s3ArchivePrefix;           // S3 path prefix for archived traces
    private final boolean enableCompression;        // Compress before archival
    private final int maxHotSpans;                  // Max spans in hot tier
    private final int maxWarmSpans;                 // Max spans in warm tier
    private final ArchiveStrategy archiveStrategy;  // Time vs size trigger
    private final int maxConcurrentArchives;        // Parallel archive operations

    private RetentionPolicy(Builder builder) {
        this.hotStorageTtl = Objects.requireNonNull(builder.hotStorageTtl, "hotStorageTtl required");
        this.warmStorageTtl = Objects.requireNonNull(builder.warmStorageTtl, "warmStorageTtl required");
        this.coldStorageTtl = Objects.requireNonNull(builder.coldStorageTtl, "coldStorageTtl required");
        this.archiveStorageTtl = Objects.requireNonNull(builder.archiveStorageTtl, "archiveStorageTtl required");
        this.archiveToS3 = builder.archiveToS3;
        this.s3BucketName = builder.s3BucketName;
        this.s3ArchivePrefix = builder.s3ArchivePrefix;
        this.enableCompression = builder.enableCompression;
        this.maxHotSpans = builder.maxHotSpans;
        this.maxWarmSpans = builder.maxWarmSpans;
        this.archiveStrategy = builder.archiveStrategy;
        this.maxConcurrentArchives = builder.maxConcurrentArchives;
        validate();
    }

    private void validate() {
        if (hotStorageTtl.isNegative() || hotStorageTtl.isZero()) {
            throw new IllegalArgumentException("hotStorageTtl must be > 0");
        }
        if (warmStorageTtl.compareTo(hotStorageTtl) <= 0) {
            throw new IllegalArgumentException("warmStorageTtl must be > hotStorageTtl");
        }
        if (coldStorageTtl.compareTo(warmStorageTtl) <= 0) {
            throw new IllegalArgumentException("coldStorageTtl must be > warmStorageTtl");
        }
        if (archiveStorageTtl.compareTo(coldStorageTtl) <= 0) {
            throw new IllegalArgumentException("archiveStorageTtl must be > coldStorageTtl");
        }
        if (archiveToS3 && (s3BucketName == null || s3BucketName.isBlank())) {
            throw new IllegalArgumentException("s3BucketName required when archiveToS3=true");
        }
        if (maxHotSpans <= 0) {
            throw new IllegalArgumentException("maxHotSpans must be > 0");
        }
        if (maxWarmSpans <= 0) {
            throw new IllegalArgumentException("maxWarmSpans must be > 0");
        }
        if (maxConcurrentArchives <= 0) {
            throw new IllegalArgumentException("maxConcurrentArchives must be > 0");
        }
    }

    /**
     * Determine storage tier for span based on age.
     *
     * @param span Span to check (uses startTime for age calculation)
     * @return StorageTier: HOT, WARM, COLD, or ARCHIVE
     */
    public StorageTier getStorageTier(SpanData span) {
        Objects.requireNonNull(span, "span required");
        Duration age = Duration.between(span.getStartTime(), Instant.now());

        if (age.compareTo(archiveStorageTtl) >= 0) return StorageTier.ARCHIVE;
        if (age.compareTo(coldStorageTtl) >= 0) return StorageTier.COLD;
        if (age.compareTo(warmStorageTtl) >= 0) return StorageTier.WARM;
        return StorageTier.HOT;
    }

    /**
     * Check if span should be moved to next tier.
     *
     * @param span Span to check
     * @param currentTier Current storage tier
     * @return true if span should be moved
     */
    public boolean shouldMoveTier(SpanData span, StorageTier currentTier) {
        StorageTier targetTier = getStorageTier(span);
        return targetTier.ordinal() > currentTier.ordinal();
    }

    /**
     * Check if span should be archived to S3.
     *
     * @param span Span to check
     * @return true if archiving enabled and span age > archiveStorageTtl
     */
    public boolean shouldArchiveSpan(SpanData span) {
        if (!archiveToS3) return false;
        Duration age = Duration.between(span.getStartTime(), Instant.now());
        return age.compareTo(archiveStorageTtl) >= 0;
    }

    /**
     * Check if span should be deleted (TTL expired).
     *
     * @param span Span to check
     * @param deleteAfterArchiveAge Age after archival to delete
     * @return true if span should be deleted
     */
    public boolean shouldDelete(SpanData span, Duration deleteAfterArchiveAge) {
        if (!archiveToS3 && span.getStartTime().plus(coldStorageTtl).isBefore(Instant.now())) {
            return true;
        }
        return span.getStartTime().plus(archiveStorageTtl).plus(deleteAfterArchiveAge).isBefore(Instant.now());
    }

    /**
     * Get S3 archive path for span.
     *
     * @param span Span to archive
     * @return S3 path (e.g., "s3://bucket/traces/2025/01/23/trace-123/")
     */
    public String getArchivePath(SpanData span) {
        Objects.requireNonNull(span, "span required");
        Instant timestamp = span.getStartTime();
        String date = String.format("%04d/%02d/%02d",
                timestamp.get(java.time.temporal.ChronoField.YEAR),
                timestamp.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                timestamp.get(java.time.temporal.ChronoField.DAY_OF_MONTH));
        return String.format("s3://%s/%s/%s/", s3BucketName, s3ArchivePrefix, date);
    }

    /**
     * Get default retention policy (7d hot, 30d warm, 365d cold, 7y archive).
     *
     * @return Default policy
     */
    public static RetentionPolicy defaultPolicy() {
        return builder().build();
    }

    /**
     * Create policy builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public Duration getHotStorageTtl() { return hotStorageTtl; }
    public Duration getWarmStorageTtl() { return warmStorageTtl; }
    public Duration getColdStorageTtl() { return coldStorageTtl; }
    public Duration getArchiveStorageTtl() { return archiveStorageTtl; }
    public boolean isArchiveToS3() { return archiveToS3; }
    public String getS3BucketName() { return s3BucketName; }
    public String getS3ArchivePrefix() { return s3ArchivePrefix; }
    public boolean isCompressionEnabled() { return enableCompression; }
    public int getMaxHotSpans() { return maxHotSpans; }
    public int getMaxWarmSpans() { return maxWarmSpans; }
    public ArchiveStrategy getArchiveStrategy() { return archiveStrategy; }
    public int getMaxConcurrentArchives() { return maxConcurrentArchives; }

    /**
     * Storage tier enumeration.
     */
    public enum StorageTier {
        HOT("In-memory, fully indexed, cache enabled"),
        WARM("SSD, basic indexing, cache disabled"),
        COLD("S3, no indexing, full scans"),
        ARCHIVE("Glacier, retrieval hours");

        private final String description;

        StorageTier(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * Archive strategy enumeration.
     */
    public enum ArchiveStrategy {
        TIME_BASED("Move to next tier after TTL expires"),
        SIZE_BASED("Move when storage tier exceeds max size"),
        HYBRID("Combine time and size triggers");

        private final String description;

        ArchiveStrategy(String description) {
            this.description = description;
        }

        public String getDescription() { return description; }
    }

    /**
     * Builder for RetentionPolicy.
     */
    public static class Builder {
        private Duration hotStorageTtl = Duration.ofDays(7);
        private Duration warmStorageTtl = Duration.ofDays(30);
        private Duration coldStorageTtl = Duration.ofDays(365);
        private Duration archiveStorageTtl = Duration.ofDays(365 * 7);  // 7 years
        private boolean archiveToS3 = false;
        private String s3BucketName;
        private String s3ArchivePrefix = "traces";
        private boolean enableCompression = true;
        private int maxHotSpans = 1_000_000;       // 1M spans
        private int maxWarmSpans = 10_000_000;     // 10M spans
        private ArchiveStrategy archiveStrategy = ArchiveStrategy.TIME_BASED;
        private int maxConcurrentArchives = 5;

        /**
         * Set hot storage TTL (0-7 days by default).
         *
         * @param hotStorageTtl Duration in hot tier
         * @return Builder for chaining
         */
        public Builder hotStorage(Duration hotStorageTtl) {
            this.hotStorageTtl = Objects.requireNonNull(hotStorageTtl);
            return this;
        }

        /**
         * Set warm storage TTL (7-30 days by default).
         *
         * @param warmStorageTtl Duration in warm tier
         * @return Builder for chaining
         */
        public Builder warmStorage(Duration warmStorageTtl) {
            this.warmStorageTtl = Objects.requireNonNull(warmStorageTtl);
            return this;
        }

        /**
         * Set cold storage TTL (30-365 days by default).
         *
         * @param coldStorageTtl Duration in cold tier
         * @return Builder for chaining
         */
        public Builder coldStorage(Duration coldStorageTtl) {
            this.coldStorageTtl = Objects.requireNonNull(coldStorageTtl);
            return this;
        }

        /**
         * Set archive storage TTL (365+ days by default).
         *
         * @param archiveStorageTtl Duration in archive tier
         * @return Builder for chaining
         */
        public Builder archiveStorage(Duration archiveStorageTtl) {
            this.archiveStorageTtl = Objects.requireNonNull(archiveStorageTtl);
            return this;
        }

        /**
         * Enable S3 archival with bucket name.
         *
         * @param bucketName S3 bucket name (e.g., "my-traces-archive")
         * @return Builder for chaining
         */
        public Builder archiveToS3(String bucketName) {
            this.archiveToS3 = true;
            this.s3BucketName = Objects.requireNonNull(bucketName);
            return this;
        }

        /**
         * Set S3 archive path prefix.
         *
         * @param prefix S3 prefix (e.g., "traces" or "archive/traces")
         * @return Builder for chaining
         */
        public Builder s3ArchivePrefix(String prefix) {
            this.s3ArchivePrefix = Objects.requireNonNull(prefix);
            return this;
        }

        /**
         * Enable or disable compression for archived spans.
         *
         * @param enabled true to enable compression
         * @return Builder for chaining
         */
        public Builder enableCompression(boolean enabled) {
            this.enableCompression = enabled;
            return this;
        }

        /**
         * Set max spans in hot tier before moving to warm.
         *
         * @param maxSpans Maximum spans in hot tier
         * @return Builder for chaining
         */
        public Builder maxHotSpans(int maxSpans) {
            this.maxHotSpans = maxSpans;
            return this;
        }

        /**
         * Set max spans in warm tier before moving to cold.
         *
         * @param maxSpans Maximum spans in warm tier
         * @return Builder for chaining
         */
        public Builder maxWarmSpans(int maxSpans) {
            this.maxWarmSpans = maxSpans;
            return this;
        }

        /**
         * Set archive strategy (time-based, size-based, or hybrid).
         *
         * @param strategy Archive strategy
         * @return Builder for chaining
         */
        public Builder archiveStrategy(ArchiveStrategy strategy) {
            this.archiveStrategy = Objects.requireNonNull(strategy);
            return this;
        }

        /**
         * Set max concurrent archive operations.
         *
         * @param maxConcurrent Maximum concurrent operations
         * @return Builder for chaining
         */
        public Builder maxConcurrentArchives(int maxConcurrent) {
            this.maxConcurrentArchives = maxConcurrent;
            return this;
        }

        /**
         * Build retention policy.
         *
         * @return RetentionPolicy instance
         */
        public RetentionPolicy build() {
            return new RetentionPolicy(this);
        }
    }
}
