package com.ghatana.datacloud;

/**
 * Storage tier classification for data lifecycle management.
 *
 * <p>
 * Data flows through tiers based on access patterns and retention policies:
 * <ul>
 * <li>{@link #HOT}: In-memory cache for real-time access (Redis/Dragonfly)</li>
 * <li>{@link #WARM}: Primary database for recent data (PostgreSQL)</li>
 * <li>{@link #COOL}: Data lake for analytics (Apache Iceberg)</li>
 * <li>{@link #COLD}: Archive storage for compliance (S3/Parquet)</li>
 * </ul>
 *
 * <p>
 * <b>Tier Characteristics</b>:
 * <table border="1">
 * <tr><th>Tier</th><th>Latency</th><th>Cost</th><th>Retention</th><th>Access
 * Pattern</th></tr>
 * <tr><td>HOT</td><td>&lt;1ms</td><td>$$$</td><td>Minutes</td><td>Real-time
 * streaming</td></tr>
 * <tr><td>WARM</td><td>&lt;10ms</td><td>$$</td><td>Days/Weeks</td><td>Interactive
 * queries</td></tr>
 * <tr><td>COOL</td><td>&lt;100ms</td><td>$</td><td>Months</td><td>Batch
 * analytics</td></tr>
 * <tr><td>COLD</td><td>Seconds</td><td>¢</td><td>Years</td><td>Compliance/Archive</td></tr>
 * </table>
 *
 * <p>
 * <b>Lifecycle Flow</b>:
 * <pre>
 * HOT → WARM → COOL → COLD
 *  │      │      │      │
 *  │      │      │      └── Archive (7+ years)
 *  │      │      └── Analytics (30+ days)
 *  │      └── Primary (7+ days)
 *  └── Real-time (5 minutes)
 * </pre>
 *
 * <p>
 * <b>Applicability</b>: This tier model applies to ALL data types in
 * Data-Cloud:
 * <ul>
 * <li><b>Events</b>: Stream events flow HOT→WARM→COOL→COLD</li>
 * <li><b>Entities</b>: Master data typically stays WARM, archived versions go
 * COLD</li>
 * <li><b>TimeSeries</b>: Recent metrics HOT/WARM, historical COOL/COLD</li>
 * <li><b>Documents</b>: Active docs WARM, archived COLD</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b>:
 * <pre>{@code
 * // For Events
 * Event event = Event.builder()
 *     .currentTier(StorageTier.WARM)
 *     .build();
 *
 * // For Entities
 * Entity entity = Entity.builder()
 *     .storageTier(StorageTier.WARM)
 *     .build();
 *
 * // Tier comparison
 * if (record.getTier().isLowerThan(StorageTier.WARM)) {
 *     // Data is in analytics/archive tier
 * }
 * }</pre>
 *
 * @see DataRecord
 * @see EventRecord
 * @see EntityRecord
 * @see RetentionPolicy
 * @doc.type enum
 * @doc.purpose Storage tier classification for data lifecycle management
 * @doc.layer core
 * @doc.pattern Tiered Storage, Data Lifecycle Management
 */
public enum StorageTier {

    /**
     * Hot tier: In-memory storage for real-time access.
     *
     * <p>
     * Characteristics:
     * <ul>
     * <li>Storage: Redis, Dragonfly, In-memory cache</li>
     * <li>Latency: Sub-millisecond</li>
     * <li>Retention: Minutes (configurable)</li>
     * <li>Use case: Real-time streaming, tailing, notifications</li>
     * </ul>
     */
    HOT,
    /**
     * Warm tier: Primary database storage for recent data.
     *
     * <p>
     * Characteristics:
     * <ul>
     * <li>Storage: PostgreSQL, TimescaleDB, Cassandra</li>
     * <li>Latency: &lt;10ms</li>
     * <li>Retention: Days to weeks</li>
     * <li>Use case: Interactive queries, dashboards, alerts</li>
     * </ul>
     */
    WARM,
    /**
     * Cool tier: Data lake storage for analytics.
     *
     * <p>
     * Characteristics:
     * <ul>
     * <li>Storage: Apache Iceberg, Delta Lake</li>
     * <li>Latency: ~100ms</li>
     * <li>Retention: Months</li>
     * <li>Use case: Batch analytics, ML training, reporting</li>
     * </ul>
     */
    COOL,
    /**
     * Cold tier: Archive storage for compliance and long-term retention.
     *
     * <p>
     * Characteristics:
     * <ul>
     * <li>Storage: S3, GCS (Parquet/ORC format)</li>
     * <li>Latency: Seconds to minutes</li>
     * <li>Retention: Years</li>
     * <li>Use case: Compliance, audit, disaster recovery</li>
     * </ul>
     */
    COLD;

    /**
     * Checks if this tier is higher (faster) than another tier.
     *
     * @param other the tier to compare
     * @return true if this tier has lower latency
     */
    public boolean isHigherThan(StorageTier other) {
        return this.ordinal() < other.ordinal();
    }

    /**
     * Checks if this tier is lower (slower/cheaper) than another tier.
     *
     * @param other the tier to compare
     * @return true if this tier has higher latency
     */
    public boolean isLowerThan(StorageTier other) {
        return this.ordinal() > other.ordinal();
    }

    /**
     * Gets the next lower (colder) tier for automatic tier-down.
     *
     * @return the next colder tier, or COLD if already at coldest
     */
    public StorageTier nextLowerTier() {
        int next = this.ordinal() + 1;
        StorageTier[] tiers = values();
        return next < tiers.length ? tiers[next] : COLD;
    }

    /**
     * Gets the next higher (hotter) tier for tier-up promotion.
     *
     * @return the next hotter tier, or HOT if already at hottest
     */
    public StorageTier nextHigherTier() {
        int prev = this.ordinal() - 1;
        return prev >= 0 ? values()[prev] : HOT;
    }

    /**
     * Gets the default storage tier for new records.
     *
     * @return WARM as the default tier
     */
    public static StorageTier defaultTier() {
        return WARM;
    }

    /**
     * Checks if this is the coldest (archive) tier.
     *
     * @return true if COLD
     */
    public boolean isColdest() {
        return this == COLD;
    }

    /**
     * Checks if this is the hottest (real-time) tier.
     *
     * @return true if HOT
     */
    public boolean isHottest() {
        return this == HOT;
    }
}
