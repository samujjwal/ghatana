package com.ghatana.datacloud.entity.storage;

import java.time.Instant;
import java.util.*;

/**
 * Immutable value object representing a storage configuration profile.
 *
 * <p><b>Purpose</b><br>
 * Defines a named storage profile that specifies:
 * - Supported backend types and their configurations
 * - Latency class (immediate, fast, standard, bulk)
 * - Cost tier (high, medium, low)
 * - Consistency model hints
 * - Multi-tier strategy with TTL transitions
 *
 * Storage profiles allow collections to declare their storage needs declaratively,
 * with routing services determining which actual backends to use.
 *
 * <p><b>Predefined Profiles</b><br>
 * - HOT: In-memory + Redis for immediate access (milliseconds)
 * - WARM: PostgreSQL for standard access (seconds)
 * - COLD: Object storage for bulk/archive access (minutes)
 * - ANALYTICS: Data warehouse for analysis (optimized for scans)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StorageProfile profile = StorageProfile.builder()
 *     .name("hot-data")
 *     .label("Hot Data Profile")
 *     .supportedBackends(StorageBackendType.KEY_VALUE, StorageBackendType.RELATIONAL)
 *     .latencyClass(LatencyClass.IMMEDIATE)
 *     .costTier(CostTier.HIGH)
 *     .consistencyHint(ConsistencyHint.STRONG)
 *     .ttlDays(90)
 *     .build();
 *
 * boolean acceptable = profile.supports(StorageBackendType.RELATIONAL);
 * }</pre>
 *
 * <p><b>Immutability</b><br>
 * All fields are final and defensive copies are made for collections.
 * Safe to share between threads and store in caches.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. No synchronization needed.
 *
 * @see StorageBackendType
 * @see MetaCollection
 * @doc.type class
 * @doc.purpose Storage configuration profile definition
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class StorageProfile {
    private final String name;
    private final String label;
    private final String description;
    private final Set<StorageBackendType> supportedBackends;
    private final LatencyClass latencyClass;
    private final CostTier costTier;
    private final ConsistencyHint consistencyHint;
    private final Map<String, Object> backendConfig;
    private final Integer ttlDays;
    private final Instant createdAt;

    /**
     * Latency class for storage profile.
     * Used to guide storage selection and SLO planning.
     */
    public enum LatencyClass {
        /** Ultra-low latency (<1ms), typically in-memory */
        IMMEDIATE("immediate", 1),
        /** Fast latency (1-10ms), typically cached or in-region */
        FAST("fast", 10),
        /** Standard latency (10-100ms), typical for databases */
        STANDARD("standard", 100),
        /** High latency (100ms-10s), bulk operations, archives */
        BULK("bulk", 10_000),
        /** Extreme latency (>10s), glacier/deep archives */
        ARCHIVE("archive", 100_000);

        private final String identifier;
        private final long maxLatencyMs;

        LatencyClass(String identifier, long maxLatencyMs) {
            this.identifier = identifier;
            this.maxLatencyMs = maxLatencyMs;
        }

        public String getIdentifier() { return identifier; }
        public long getMaxLatencyMs() { return maxLatencyMs; }
    }

    /**
     * Cost tier for storage backend.
     * Used for cost optimization decisions.
     */
    public enum CostTier {
        /** Premium tier: optimized for performance, highest cost */
        HIGH("high"),
        /** Balanced tier: moderate cost and performance */
        MEDIUM("medium"),
        /** Budget tier: optimized for cost, lower performance */
        LOW("low");

        private final String identifier;

        CostTier(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() { return identifier; }
    }

    /**
     * Consistency model hint.
     * Advisory for backend selection; actual consistency depends on backend.
     */
    public enum ConsistencyHint {
        /** Strong consistency: all writes visible immediately */
        STRONG("strong"),
        /** Eventual consistency: writes propagate over time */
        EVENTUAL("eventual"),
        /** Causal consistency: causally related operations ordered */
        CAUSAL("causal"),
        /** Session consistency: guarantees within session only */
        SESSION("session");

        private final String identifier;

        ConsistencyHint(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() { return identifier; }
    }

    /**
     * Create storage profile (private, use builder).
     */
    private StorageProfile(
            String name,
            String label,
            String description,
            Set<StorageBackendType> supportedBackends,
            LatencyClass latencyClass,
            CostTier costTier,
            ConsistencyHint consistencyHint,
            Map<String, Object> backendConfig,
            Integer ttlDays,
            Instant createdAt) {
        this.name = Objects.requireNonNull(name, "name required");
        this.label = Objects.requireNonNull(label, "label required");
        this.description = description;
        this.supportedBackends = Collections.unmodifiableSet(
                new HashSet<>(Objects.requireNonNull(supportedBackends, "supportedBackends required")));
        if (supportedBackends.isEmpty()) {
            throw new IllegalArgumentException("supportedBackends must not be empty");
        }
        this.latencyClass = Objects.requireNonNull(latencyClass, "latencyClass required");
        this.costTier = Objects.requireNonNull(costTier, "costTier required");
        this.consistencyHint = Objects.requireNonNull(consistencyHint, "consistencyHint required");
        this.backendConfig = Collections.unmodifiableMap(
                new HashMap<>(Objects.requireNonNull(backendConfig, "backendConfig required")));
        this.ttlDays = ttlDays;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    /**
     * Create new builder.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Get profile name.
     *
     * @return Name
     */
    public String getName() {
        return name;
    }

    /**
     * Get profile label.
     *
     * @return Label
     */
    public String getLabel() {
        return label;
    }

    /**
     * Get profile description.
     *
     * @return Description (optional)
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get supported backend types.
     *
     * @return Immutable set of backends
     */
    public Set<StorageBackendType> getSupportedBackends() {
        return supportedBackends;
    }

    /**
     * Check if backend is supported.
     *
     * @param backend Backend type to check
     * @return true if supported
     */
    public boolean supports(StorageBackendType backend) {
        return supportedBackends.contains(Objects.requireNonNull(backend));
    }

    /**
     * Get latency class.
     *
     * @return Latency class
     */
    public LatencyClass getLatencyClass() {
        return latencyClass;
    }

    /**
     * Get cost tier.
     *
     * @return Cost tier
     */
    public CostTier getCostTier() {
        return costTier;
    }

    /**
     * Get consistency hint.
     *
     * @return Consistency hint
     */
    public ConsistencyHint getConsistencyHint() {
        return consistencyHint;
    }

    /**
     * Get backend-specific configuration.
     *
     * @return Immutable config map
     */
    public Map<String, Object> getBackendConfig() {
        return backendConfig;
    }

    /**
     * Get retention TTL in days.
     *
     * @return TTL days (null for unlimited)
     */
    public Integer getTtlDays() {
        return ttlDays;
    }

    /**
     * Get creation time.
     *
     * @return Created at instant
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StorageProfile that = (StorageProfile) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "StorageProfile{" +
                "name='" + name + '\'' +
                ", latencyClass=" + latencyClass +
                ", costTier=" + costTier +
                ", supportedBackends=" + supportedBackends +
                ", ttlDays=" + ttlDays +
                '}';
    }

    /**
     * Predefined HOT profile: in-memory + Redis for immediate access.
     *
     * @return HOT profile
     */
    public static StorageProfile hotProfile() {
        return builder()
                .name("hot")
                .label("Hot Data")
                .description("In-memory and cached storage for immediate access")
                .supportedBackends(StorageBackendType.IN_MEMORY, StorageBackendType.KEY_VALUE)
                .latencyClass(LatencyClass.IMMEDIATE)
                .costTier(CostTier.HIGH)
                .consistencyHint(ConsistencyHint.STRONG)
                .ttlDays(7)
                .build();
    }

    /**
     * Predefined WARM profile: PostgreSQL for standard access.
     *
     * @return WARM profile
     */
    public static StorageProfile warmProfile() {
        return builder()
                .name("warm")
                .label("Warm Data")
                .description("PostgreSQL JSONB for standard transactional access")
                .supportedBackends(StorageBackendType.RELATIONAL)
                .latencyClass(LatencyClass.STANDARD)
                .costTier(CostTier.MEDIUM)
                .consistencyHint(ConsistencyHint.STRONG)
                .ttlDays(90)
                .build();
    }

    /**
     * Predefined COLD profile: object storage for bulk/archive.
     *
     * @return COLD profile
     */
    public static StorageProfile coldProfile() {
        return builder()
                .name("cold")
                .label("Cold Data")
                .description("Object storage (S3/GCS) for archive and bulk access")
                .supportedBackends(StorageBackendType.BLOB)
                .latencyClass(LatencyClass.BULK)
                .costTier(CostTier.LOW)
                .consistencyHint(ConsistencyHint.EVENTUAL)
                .ttlDays(365)
                .build();
    }

    /**
     * Predefined ANALYTICS profile: data warehouse optimized.
     *
     * @return ANALYTICS profile
     */
    public static StorageProfile analyticsProfile() {
        return builder()
                .name("analytics")
                .label("Analytics")
                .description("Data warehouse (Snowflake/BigQuery) for analytical queries")
                .supportedBackends(StorageBackendType.LAKEHOUSE, StorageBackendType.TIMESERIES)
                .latencyClass(LatencyClass.STANDARD)
                .costTier(CostTier.MEDIUM)
                .consistencyHint(ConsistencyHint.EVENTUAL)
                .ttlDays(null) // Indefinite for analytics
                .build();
    }

    /**
     * Builder for StorageProfile.
     */
    public static class Builder {
        private String name;
        private String label;
        private String description;
        private Set<StorageBackendType> supportedBackends;
        private LatencyClass latencyClass;
        private CostTier costTier;
        private ConsistencyHint consistencyHint;
        private Map<String, Object> backendConfig = new HashMap<>();
        private Integer ttlDays;
        private Instant createdAt;

        /**
         * Set profile name.
         *
         * @param name Name
         * @return Builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set profile label.
         *
         * @param label Label
         * @return Builder
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Set profile description.
         *
         * @param description Description
         * @return Builder
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Set supported backend types.
         *
         * @param backends Backend types
         * @return Builder
         */
        public Builder supportedBackends(StorageBackendType... backends) {
            this.supportedBackends = new HashSet<>(Arrays.asList(
                    Objects.requireNonNull(backends, "backends required")));
            return this;
        }

        /**
         * Set latency class.
         *
         * @param latencyClass Latency class
         * @return Builder
         */
        public Builder latencyClass(LatencyClass latencyClass) {
            this.latencyClass = latencyClass;
            return this;
        }

        /**
         * Set cost tier.
         *
         * @param costTier Cost tier
         * @return Builder
         */
        public Builder costTier(CostTier costTier) {
            this.costTier = costTier;
            return this;
        }

        /**
         * Set consistency hint.
         *
         * @param consistencyHint Consistency hint
         * @return Builder
         */
        public Builder consistencyHint(ConsistencyHint consistencyHint) {
            this.consistencyHint = consistencyHint;
            return this;
        }

        /**
         * Set backend configuration.
         *
         * @param key Config key
         * @param value Config value
         * @return Builder
         */
        public Builder withBackendConfig(String key, Object value) {
            this.backendConfig.put(
                    Objects.requireNonNull(key),
                    Objects.requireNonNull(value));
            return this;
        }

        /**
         * Set all backend configuration.
         *
         * @param config Config map
         * @return Builder
         */
        public Builder backendConfig(Map<String, Object> config) {
            this.backendConfig = new HashMap<>(
                    Objects.requireNonNull(config, "config required"));
            return this;
        }

        /**
         * Set TTL in days.
         *
         * @param ttlDays TTL in days (null for unlimited)
         * @return Builder
         */
        public Builder ttlDays(Integer ttlDays) {
            if (ttlDays != null && ttlDays <= 0) {
                throw new IllegalArgumentException("ttlDays must be > 0");
            }
            this.ttlDays = ttlDays;
            return this;
        }

        /**
         * Set creation time.
         *
         * @param createdAt Creation time
         * @return Builder
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Build StorageProfile.
         *
         * @return Immutable profile
         */
        public StorageProfile build() {
            return new StorageProfile(
                    name,
                    label,
                    description,
                    supportedBackends != null ? supportedBackends : new HashSet<>(),
                    latencyClass,
                    costTier,
                    consistencyHint,
                    backendConfig,
                    ttlDays,
                    createdAt
            );
        }
    }
}
