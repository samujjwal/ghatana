package com.ghatana.datacloud.entity.storage;

import java.util.*;

/**
 * Immutable routing plan for directing storage operations to appropriate
 * connectors.
 *
 * <p>
 * <b>Purpose</b><br>
 * Encapsulates the decision of which StorageConnector to use for a given
 * operation,
 * including failover strategies, weights, and metadata about the routing
 * decision.
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * RoutingPlan plan = RoutingPlan.builder()
 *         .tenantId("tenant-123")
 *         .collectionId("products-v1")
 *         .primaryConnector("postgres-jsonb-1")
 *         .secondaryConnectors(Arrays.asList("postgres-jsonb-2", "analytics-pg"))
 *         .mode(RoutingMode.READ_WRITE)
 *         .addWeight("postgres-jsonb-1", 0.7)
 *         .addWeight("postgres-jsonb-2", 0.3)
 *         .failoverStrategy(FailoverStrategy.ROUND_ROBIN)
 *         .build();
 *
 * String selected = plan.selectConnector(); // "postgres-jsonb-1" (primary)
 * List<String> fallbacks = plan.getFailoverConnectors(); // [secondary connectors]
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Part of the Domain Layer storage subsystem. Represents the routing decision
 * output
 * from StorageRoutingService. Used by QueryExecutionService to determine which
 * connectors to invoke and in what order.
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * Each plan is scoped to a specific tenant and collection. Tenant isolation is
 * enforced through tenantId field (immutable).
 *
 * @doc.type class
 * @doc.purpose Immutable routing plan for storage operations
 * @doc.layer domain
 * @doc.pattern Value Object
 * @see StorageConnector
 * @see StorageRoutingService
 * @see StorageProfile
 */
public final class RoutingPlan {

    /**
     * Routing mode - directs how connectors are selected and weighted.
     * <ul>
     * <li>READ: Optimized for read operations (favor fast connectors)
     * <li>WRITE: Optimized for write operations (favor durable connectors)
     * <li>READ_WRITE: Balanced for both operations
     * <li>ANALYTICS: Optimized for analytical queries (favor Lakehouse/TimeSeries)
     * </ul>
     */
    public enum RoutingMode {
        READ,
        WRITE,
        READ_WRITE,
        ANALYTICS
    }

    /**
     * Failover strategy - determines how to select alternative connectors.
     * <ul>
     * <li>ROUND_ROBIN: Cycle through alternates in order
     * <li>WEIGHTED: Use weights to select alternates
     * <li>RANDOM: Randomly select alternate connector
     * <li>LATENCY_BASED: Select alternate with lowest latency
     * </ul>
     */
    public enum FailoverStrategy {
        ROUND_ROBIN,
        WEIGHTED,
        RANDOM,
        LATENCY_BASED
    }

    private final String tenantId;
    private final String collectionId;
    private final String primaryConnector;
    private final List<String> secondaryConnectors;
    private final RoutingMode mode;
    private final Map<String, Double> weights;
    private final FailoverStrategy failoverStrategy;
    private final Map<String, Object> metadata;
    private final long createdAtMillis;

    /**
     * Private constructor - use builder.
     */
    private RoutingPlan(
            String tenantId,
            String collectionId,
            String primaryConnector,
            List<String> secondaryConnectors,
            RoutingMode mode,
            Map<String, Double> weights,
            FailoverStrategy failoverStrategy,
            Map<String, Object> metadata) {

        this.tenantId = tenantId;
        this.collectionId = collectionId;
        this.primaryConnector = primaryConnector;
        this.secondaryConnectors = Collections.unmodifiableList(new ArrayList<>(secondaryConnectors));
        this.mode = mode;
        this.weights = Collections.unmodifiableMap(new HashMap<>(weights));
        this.failoverStrategy = failoverStrategy;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(metadata));
        this.createdAtMillis = System.currentTimeMillis();
    }

    // --- Accessors (Getters) ---

    public String getTenantId() {
        return tenantId;
    }

    public String getCollectionId() {
        return collectionId;
    }

    public String getPrimaryConnector() {
        return primaryConnector;
    }

    public List<String> getSecondaryConnectors() {
        return secondaryConnectors;
    }

    public RoutingMode getMode() {
        return mode;
    }

    public Map<String, Double> getWeights() {
        return weights;
    }

    public FailoverStrategy getFailoverStrategy() {
        return failoverStrategy;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    // --- Query Methods ---

    /**
     * Returns the primary connector (always selected first).
     *
     * @return primary connector identifier
     */
    public String selectConnector() {
        return primaryConnector;
    }

    /**
     * Returns all connectors in this plan (primary + secondary).
     *
     * @return list of all connector IDs
     */
    public List<String> getAllConnectors() {
        List<String> all = new ArrayList<>();
        all.add(primaryConnector);
        all.addAll(secondaryConnectors);
        return Collections.unmodifiableList(all);
    }

    /**
     * Returns list of failover connectors (all secondaries in order).
     *
     * @return list of secondary connector IDs
     */
    public List<String> getFailoverConnectors() {
        return secondaryConnectors;
    }

    /**
     * Gets weight for a connector (0.0-1.0 scale).
     *
     * @param connectorId connector identifier
     * @return weight for connector (or 0.0 if not set)
     */
    public double getWeight(String connectorId) {
        return weights.getOrDefault(connectorId, 0.0);
    }

    /**
     * Checks if plan supports windowed queries (time-series operations).
     *
     * @return true if any connector in plan supports windowed queries
     */
    public boolean supportsWindowedQueries() {
        Object flag = metadata.get("supportsWindowed");
        return flag instanceof Boolean && (Boolean) flag;
    }

    /**
     * Gets estimated latency for this routing plan (milliseconds).
     *
     * @return estimated latency based on primary connector's profile
     */
    public long getEstimatedLatencyMillis() {
        Object latency = metadata.get("estimatedLatencyMillis");
        return latency instanceof Number ? ((Number) latency).longValue() : 0L;
    }

    /**
     * Checks if plan requires multi-connector aggregation.
     *
     * @return true if secondary connectors should be queried in parallel
     */
    public boolean isMultiConnectorAggregation() {
        Object flag = metadata.get("multiConnectorAggregation");
        return flag instanceof Boolean && (Boolean) flag;
    }

    // --- Immutability Enforcement ---

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RoutingPlan that = (RoutingPlan) o;
        return tenantId.equals(that.tenantId) &&
                collectionId.equals(that.collectionId) &&
                primaryConnector.equals(that.primaryConnector) &&
                secondaryConnectors.equals(that.secondaryConnectors) &&
                mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, collectionId, primaryConnector, mode);
    }

    @Override
    public String toString() {
        return "RoutingPlan{" +
                "tenantId='" + tenantId + '\'' +
                ", collectionId='" + collectionId + '\'' +
                ", primaryConnector='" + primaryConnector + '\'' +
                ", mode=" + mode +
                ", failoverConnectors=" + secondaryConnectors.size() +
                ", createdAtMillis=" + createdAtMillis +
                '}';
    }

    // --- Builder Pattern ---

    /**
     * Creates a new RoutingPlan builder.
     *
     * @return builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for RoutingPlan.
     *
     * <p>
     * Usage:<br>
     * 
     * <pre>{@code
     * RoutingPlan plan = RoutingPlan.builder()
     *         .tenantId("tenant-1")
     *         .collectionId("products")
     *         .primaryConnector("pg-1")
     *         .mode(RoutingMode.READ_WRITE)
     *         .addSecondaryConnector("pg-2")
     *         .addWeight("pg-1", 0.7)
     *         .addWeight("pg-2", 0.3)
     *         .failoverStrategy(FailoverStrategy.ROUND_ROBIN)
     *         .build();
     * }</pre>
     */
    public static class Builder {
        private String tenantId;
        private String collectionId;
        private String primaryConnector;
        private final List<String> secondaryConnectors = new ArrayList<>();
        private RoutingMode mode = RoutingMode.READ_WRITE;
        private final Map<String, Double> weights = new HashMap<>();
        private FailoverStrategy failoverStrategy = FailoverStrategy.ROUND_ROBIN;
        private final Map<String, Object> metadata = new HashMap<>();

        private Builder() {
        }

        public Builder tenantId(String tenantId) {
            if (tenantId == null || tenantId.trim().isEmpty()) {
                throw new IllegalArgumentException("tenantId cannot be null or empty");
            }
            this.tenantId = tenantId;
            return this;
        }

        public Builder collectionId(String collectionId) {
            if (collectionId == null || collectionId.trim().isEmpty()) {
                throw new IllegalArgumentException("collectionId cannot be null or empty");
            }
            this.collectionId = collectionId;
            return this;
        }

        public Builder primaryConnector(String primaryConnector) {
            if (primaryConnector == null || primaryConnector.trim().isEmpty()) {
                throw new IllegalArgumentException("primaryConnector cannot be null or empty");
            }
            this.primaryConnector = primaryConnector;
            return this;
        }

        public Builder secondaryConnectors(List<String> connectors) {
            if (connectors != null) {
                this.secondaryConnectors.clear();
                this.secondaryConnectors.addAll(connectors);
            }
            return this;
        }

        public Builder addSecondaryConnector(String connectorId) {
            if (connectorId != null && !connectorId.trim().isEmpty()) {
                secondaryConnectors.add(connectorId);
            }
            return this;
        }

        public Builder mode(RoutingMode mode) {
            this.mode = mode != null ? mode : RoutingMode.READ_WRITE;
            return this;
        }

        public Builder addWeight(String connectorId, double weight) {
            if (connectorId != null && !connectorId.trim().isEmpty()) {
                if (weight < 0.0 || weight > 1.0) {
                    throw new IllegalArgumentException("weight must be between 0.0 and 1.0");
                }
                weights.put(connectorId, weight);
            }
            return this;
        }

        public Builder failoverStrategy(FailoverStrategy strategy) {
            this.failoverStrategy = strategy != null ? strategy : FailoverStrategy.ROUND_ROBIN;
            return this;
        }

        public Builder putMetadata(String key, Object value) {
            if (key != null && !key.trim().isEmpty()) {
                metadata.put(key, value);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> meta) {
            if (meta != null) {
                metadata.clear();
                metadata.putAll(meta);
            }
            return this;
        }

        /**
         * Builds the RoutingPlan instance.
         *
         * @return new RoutingPlan with configured settings
         * @throws IllegalArgumentException if required fields are missing
         */
        public RoutingPlan build() {
            if (tenantId == null || tenantId.trim().isEmpty()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (collectionId == null || collectionId.trim().isEmpty()) {
                throw new IllegalArgumentException("collectionId is required");
            }
            if (primaryConnector == null || primaryConnector.trim().isEmpty()) {
                throw new IllegalArgumentException("primaryConnector is required");
            }

            return new RoutingPlan(
                    tenantId,
                    collectionId,
                    primaryConnector,
                    secondaryConnectors,
                    mode,
                    weights,
                    failoverStrategy,
                    metadata);
        }
    }
}
