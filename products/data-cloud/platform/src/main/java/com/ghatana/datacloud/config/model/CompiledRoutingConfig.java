/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Compiled routing configuration with validated and optimized routing rules.
 *
 * <p>
 * Contains both write and read routing rules, partition configuration, and
 * fan-out settings for data replication.
 *
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * CompiledRoutingConfig config = compiler.compile(rawConfig);
 *
 * // Find write target
 * RoutingDecision decision = config.routeWrite(record, context);
 * String targetStorage = decision.target();
 *
 * // Find read target
 * RoutingDecision readDecision = config.routeRead(query, context);
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Compiled routing configuration for storage routing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CompiledRoutingConfig(
        String name,
        String namespace,
        Map<String, String> labels,
        CompiledWriteRouting writeRouting,
        CompiledReadRouting readRouting,
        CompiledFanOutConfig fanOut,
        CompiledPartitionConfig partitioning,
        long configVersion,
        Instant loadedAt) {

    public CompiledRoutingConfig         {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(writeRouting, "writeRouting");
        Objects.requireNonNull(readRouting, "readRouting");
        Objects.requireNonNull(loadedAt, "loadedAt");
        labels = labels != null ? Map.copyOf(labels) : Map.of();
    }

    // =====================================================================
    // Routing Strategy Enums
    // =====================================================================
    /**
     * Partition strategy types.
     */
    public enum PartitionStrategy {
        /**
         * Hash-based partitioning
         */
        HASH,
        /**
         * Range-based partitioning (e.g., time ranges)
         */
        RANGE,
        /**
         * List-based partitioning (explicit value mapping)
         */
        LIST,
        /**
         * Composite of multiple strategies
         */
        COMPOSITE
    }

    /**
     * Hash algorithm for partition key hashing.
     */
    public enum HashAlgorithm {
        MURMUR3,
        MD5,
        SHA256,
        XXHASH
    }

    /**
     * Read preference mode.
     */
    public enum ReadPreferenceMode {
        /**
         * Always read from primary
         */
        PRIMARY,
        /**
         * Prefer secondary replicas
         */
        SECONDARY,
        /**
         * Read from nearest replica
         */
        NEAREST,
        /**
         * Round-robin across replicas
         */
        ROUND_ROBIN
    }

    /**
     * Consistency level for distributed writes.
     */
    public enum ConsistencyLevel {
        /**
         * Write acknowledged by one node
         */
        ONE,
        /**
         * Write acknowledged by quorum
         */
        QUORUM,
        /**
         * Write acknowledged by all nodes
         */
        ALL,
        /**
         * Local quorum only
         */
        LOCAL_QUORUM,
        /**
         * Each quorum in each datacenter
         */
        EACH_QUORUM
    }

    /**
     * Placement strategy for partition affinity.
     */
    public enum PlacementStrategy {
        /**
         * Co-locate related partitions
         */
        COLOCATE,
        /**
         * Spread partitions across nodes
         */
        SPREAD,
        /**
         * Separate partitions to different nodes
         */
        SEPARATE,
        /**
         * Spread across availability zones
         */
        ZONE_SPREAD
    }

    // =====================================================================
    // Write Routing
    // =====================================================================
    /**
     * Compiled write routing configuration.
     */
    public record CompiledWriteRouting(
            List<CompiledRoutingRule> rules,
            String defaultTarget) {

        

    public CompiledWriteRouting         {
        Objects.requireNonNull(rules, "rules");
        rules = List.copyOf(rules);
    }

    /**
     * Find the target for a write operation.
     *
     * @param context the routing context
     * @return the routing decision
     */
    public RoutingDecision route(RoutingContext context) {
        for (CompiledRoutingRule rule : rules) {
            if (rule.enabled() && rule.matches(context)) {
                return new RoutingDecision(
                        rule.target(),
                        Optional.empty(),
                        rule.name(),
                        rule.metadata());
            }
        }
        return new RoutingDecision(
                defaultTarget,
                Optional.empty(),
                "default",
                Map.of());
    }
}

// =====================================================================
// Read Routing
// =====================================================================
/**
 * Compiled read routing configuration.
 */
public record CompiledReadRouting(
        List<CompiledRoutingRule> rules,
        String defaultTarget,
        CompiledReadPreference preference) {

    public CompiledReadRouting   {
        Objects.requireNonNull(rules, "rules");
        rules = List.copyOf(rules);
    }

    /**
     * Find the target for a read operation.
     *
     * @param context the routing context
     * @return the routing decision
     */
    public RoutingDecision route(RoutingContext context) {
        for (CompiledRoutingRule rule : rules) {
            if (rule.enabled() && rule.matches(context)) {
                return new RoutingDecision(
                        rule.target(),
                        rule.fallback(),
                        rule.name(),
                        rule.metadata());
            }
        }
        return new RoutingDecision(
                defaultTarget,
                Optional.empty(),
                "default",
                Map.of());
    }
}

/**
 * Compiled read preference.
 */
public record CompiledReadPreference(
        ReadPreferenceMode mode,
        Duration maxStaleness,
        Set<String> tagSets) {

    public CompiledReadPreference   {
        Objects.requireNonNull(mode, "mode");
        tagSets = tagSets != null ? Set.copyOf(tagSets) : Set.of();
    }

    /**
     * Create default read preference.
     */
    public static CompiledReadPreference defaultPreference() {
        return new CompiledReadPreference(
                ReadPreferenceMode.PRIMARY,
                Duration.ZERO,
                Set.of());
    }
}

// =====================================================================
// Routing Rules
// =====================================================================
/**
 * Compiled routing rule.
 */
public record CompiledRoutingRule(
        String name,
        CompiledCondition condition,
        String target,
        Optional<String> fallback,
        int priority,
        boolean enabled,
        Map<String, Object> metadata) {

    public CompiledRoutingRule       {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(target, "target");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Check if this rule matches the context.
     */
    public boolean matches(RoutingContext context) {
        return condition.evaluate(context);
    }
}

/**
 * Compiled condition for rule matching.
 */
public record CompiledCondition(
        ConditionType type,
        String field,
        ConditionOperator operator,
        Object value,
        List<CompiledCondition> children) {

    public CompiledCondition     {
        Objects.requireNonNull(type, "type");
        children = children != null ? List.copyOf(children) : List.of();
    }

    /**
     * Evaluate this condition against the context.
     */
    public boolean evaluate(RoutingContext context) {
        return switch (type) {
            case ALWAYS_TRUE ->
                true;
            case ALWAYS_FALSE ->
                false;
            case FIELD_MATCH ->
                evaluateFieldMatch(context);
            case AND ->
                children.stream().allMatch(c -> c.evaluate(context));
            case OR ->
                children.stream().anyMatch(c -> c.evaluate(context));
            case NOT ->
                !children.isEmpty() && !children.get(0).evaluate(context);
        };
    }

    private boolean evaluateFieldMatch(RoutingContext context) {
        if (field == null || operator == null) {
            return false;
        }
        Object fieldValue = context.getField(field);
        return switch (operator) {
            case EQUALS ->
                Objects.equals(fieldValue, value);
            case NOT_EQUALS ->
                !Objects.equals(fieldValue, value);
            case GREATER_THAN ->
                compareValues(fieldValue, value) > 0;
            case LESS_THAN ->
                compareValues(fieldValue, value) < 0;
            case GREATER_THAN_OR_EQUALS ->
                compareValues(fieldValue, value) >= 0;
            case LESS_THAN_OR_EQUALS ->
                compareValues(fieldValue, value) <= 0;
            case IN ->
                value instanceof List<?> list && list.contains(fieldValue);
            case NOT_IN ->
                !(value instanceof List<?> list && list.contains(fieldValue));
            case CONTAINS ->
                fieldValue != null && fieldValue.toString().contains(String.valueOf(value));
            case STARTS_WITH ->
                fieldValue != null && fieldValue.toString().startsWith(String.valueOf(value));
            case ENDS_WITH ->
                fieldValue != null && fieldValue.toString().endsWith(String.valueOf(value));
            case MATCHES ->
                fieldValue != null && fieldValue.toString().matches(String.valueOf(value));
        };
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : -1) : 1;
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return 0;
    }

    /**
     * Create an always-true condition.
     */
    public static CompiledCondition alwaysTrue() {
        return new CompiledCondition(
                ConditionType.ALWAYS_TRUE,
                null, null, null, List.of());
    }

    /**
     * Create a field match condition.
     */
    public static CompiledCondition fieldEquals(String field, Object value) {
        return new CompiledCondition(
                ConditionType.FIELD_MATCH,
                field,
                ConditionOperator.EQUALS,
                value,
                List.of());
    }
}

/**
 * Condition type.
 */
public enum ConditionType {
    ALWAYS_TRUE,
    ALWAYS_FALSE,
    FIELD_MATCH,
    AND,
    OR,
    NOT
}

/**
 * Condition operator.
 */
public enum ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    LESS_THAN,
    GREATER_THAN_OR_EQUALS,
    LESS_THAN_OR_EQUALS,
    IN,
    NOT_IN,
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    MATCHES
}

// =====================================================================
// Routing Context and Decision
// =====================================================================
/**
 * Context for routing decisions.
 */
public record RoutingContext(
        String tenantId,
        String collectionName,
        String recordType,
        Map<String, Object> fields,
        Map<String, Object> queryParams,
        Instant timestamp) {

    public RoutingContext      {
        Objects.requireNonNull(tenantId, "tenantId");
        fields = fields != null ? Map.copyOf(fields) : Map.of();
        queryParams = queryParams != null ? Map.copyOf(queryParams) : Map.of();
    }

    /**
     * Get a field value from the context.
     */
    public Object getField(String fieldName) {
        if (fieldName == null) {
            return null;
        }
        return switch (fieldName) {
            case "tenantId" ->
                tenantId;
            case "collectionName" ->
                collectionName;
            case "recordType" ->
                recordType;
            case "timestamp" ->
                timestamp;
            default -> {
                Object value = fields.get(fieldName);
                yield value != null ? value : queryParams.get(fieldName);
            }
        };
    }

    /**
     * Create a write context.
     */
    public static RoutingContext forWrite(
            String tenantId,
            String collectionName,
            String recordType,
            Map<String, Object> fields) {
        return new RoutingContext(
                tenantId, collectionName, recordType,
                fields, Map.of(), Instant.now());
    }

    /**
     * Create a read context.
     */
    public static RoutingContext forRead(
            String tenantId,
            String collectionName,
            Map<String, Object> queryParams) {
        return new RoutingContext(
                tenantId, collectionName, null,
                Map.of(), queryParams, Instant.now());
    }
}

/**
 * Routing decision result.
 */
public record RoutingDecision(
        String target,
        Optional<String> fallback,
        String matchedRule,
        Map<String, Object> metadata) {

    public RoutingDecision    {
        Objects.requireNonNull(target, "target");
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}

// =====================================================================
// Fan-Out Configuration
// =====================================================================
/**
 * Compiled fan-out configuration.
 */
public record CompiledFanOutConfig(
        boolean enabled,
        boolean async,
        List<CompiledFanOutTarget> targets,
        CompiledRetryConfig retry) {

    public CompiledFanOutConfig    {
        targets = targets != null ? List.copyOf(targets) : List.of();
    }

    /**
     * Create disabled fan-out config.
     */
    public static CompiledFanOutConfig disabled() {
        return new CompiledFanOutConfig(
                false, false, List.of(), CompiledRetryConfig.defaultConfig());
    }
}

/**
 * Compiled fan-out target.
 */
public record CompiledFanOutTarget(
        String primary,
        List<String> replicas,
        ConsistencyLevel consistencyLevel) {

    public CompiledFanOutTarget   {
        Objects.requireNonNull(primary, "primary");
        replicas = replicas != null ? List.copyOf(replicas) : List.of();
    }
}

/**
 * Compiled retry configuration.
 */
public record CompiledRetryConfig(
        int maxAttempts,
        Duration initialDelay,
        Duration maxDelay,
        double multiplier) {

    public CompiledRetryConfig    {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("maxAttempts must be >= 0");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
    }

    /**
     * Create default retry config.
     */
    public static CompiledRetryConfig defaultConfig() {
        return new CompiledRetryConfig(
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(5),
                2.0);
    }
}

// =====================================================================
// Partition Configuration
// =====================================================================
/**
 * Compiled partition configuration.
 */
public record CompiledPartitionConfig(
        PartitionStrategy strategy,
        CompiledPartitionKey partitionKey,
        CompiledHashPartition hashPartition,
        CompiledRangePartition rangePartition,
        CompiledPlacement placement) {

    /**
     * Compute the partition for a record.
     */
    public int computePartition(Map<String, Object> record) {
        Object keyValue = partitionKey.extractKey(record);
        return switch (strategy) {
            case HASH ->
                hashPartition != null ? hashPartition.computePartition(keyValue) : 0;
            case RANGE ->
                rangePartition != null ? rangePartition.computePartition(keyValue) : 0;
            case LIST, COMPOSITE ->
                0; // Not yet implemented
            };
    }

    /**
     * Create default partition config (single partition).
     */
    public static CompiledPartitionConfig singlePartition() {
        return new CompiledPartitionConfig(
                PartitionStrategy.HASH,
                CompiledPartitionKey.defaultKey(),
                new CompiledHashPartition(HashAlgorithm.MURMUR3, 1),
                null,
                null);
    }
}

/**
 * Compiled partition key configuration.
 */
public record CompiledPartitionKey(
        PartitionKeyType type,
        String field,
        String expression,
        List<String> fields) {

    public CompiledPartitionKey    {
        fields = fields != null ? List.copyOf(fields) : List.of();
    }

    /**
     * Extract partition key from record.
     */
    public Object extractKey(Map<String, Object> record) {
        return switch (type) {
            case FIELD ->
                record.get(field);
            case EXPRESSION ->
                record.get(field); // Simplified; expression eval not implemented
            case COMPOSITE -> {
                StringBuilder sb = new StringBuilder();
                for (String f : fields) {
                    if (!sb.isEmpty()) {
                        sb.append(":");
                    }
                    Object val = record.get(f);
                    sb.append(val != null ? val.toString() : "");
                }
                yield sb.toString();
            }
        };
    }

    /**
     * Create default partition key (by id).
     */
    public static CompiledPartitionKey defaultKey() {
        return new CompiledPartitionKey(
                PartitionKeyType.FIELD, "id", null, List.of());
    }
}

/**
 * Partition key type.
 */
public enum PartitionKeyType {
    FIELD,
    EXPRESSION,
    COMPOSITE
}

/**
 * Compiled hash partition config.
 */
public record CompiledHashPartition(
        HashAlgorithm algorithm,
        int partitionCount) {

    public CompiledHashPartition  {
        if (partitionCount < 1) {
            throw new IllegalArgumentException("partitionCount must be >= 1");
        }
    }

    /**
     * Compute partition number for a key.
     */
    public int computePartition(Object key) {
        if (key == null) {
            return 0;
        }
        int hash = switch (algorithm) {
            case MURMUR3 ->
                murmur3Hash(key.toString());
            case MD5, SHA256, XXHASH ->
                key.hashCode(); // Simplified
            };
        return Math.abs(hash % partitionCount);
    }

    private int murmur3Hash(String key) {
        // Simplified murmur3 - use proper implementation in production
        int h = 0;
        for (char c : key.toCharArray()) {
            h = 31 * h + c;
        }
        return h;
    }
}

/**
 * Compiled range partition config.
 */
public record CompiledRangePartition(
        String field,
        List<Object> boundaries,
        Duration interval) {

    public CompiledRangePartition   {
        boundaries = boundaries != null ? List.copyOf(boundaries) : List.of();
    }

    /**
     * Compute partition number for a value.
     */
    @SuppressWarnings("unchecked")
    public int computePartition(Object value) {
        if (boundaries.isEmpty() || value == null) {
            return 0;
        }
        for (int i = 0; i < boundaries.size(); i++) {
            Object boundary = boundaries.get(i);
            if (value instanceof Comparable && boundary instanceof Comparable) {
                if (((Comparable<Object>) value).compareTo(boundary) < 0) {
                    return i;
                }
            }
        }
        return boundaries.size();
    }
}

/**
 * Compiled placement configuration.
 */
public record CompiledPlacement(
        List<CompiledAffinityRule> affinity,
        List<CompiledAntiAffinityRule> antiAffinity) {

    public CompiledPlacement  {
        affinity = affinity != null ? List.copyOf(affinity) : List.of();
        antiAffinity = antiAffinity != null ? List.copyOf(antiAffinity) : List.of();
    }
}

/**
 * Compiled affinity rule.
 */
public record CompiledAffinityRule(
        String key,
        PlacementStrategy strategy) {

}

/**
 * Compiled anti-affinity rule.
 */
public record CompiledAntiAffinityRule(
        String key,
        PlacementStrategy strategy) {

}

// =====================================================================
// Convenience Methods
// =====================================================================
/**
 * Route a write operation.
 */
public RoutingDecision routeWrite(RoutingContext context) {
        return writeRouting.route(context);
    }

    /**
     * Route a read operation.
     */
    public RoutingDecision routeRead(RoutingContext context) {
        return readRouting.route(context);
    }

    /**
     * Get all fan-out targets for a write.
     */
    public List<String> getFanOutTargets() {
        if (!fanOut.enabled()) {
            return List.of();
        }
        return fanOut.targets().stream()
                .flatMap(t -> {
                    List<String> all = new java.util.ArrayList<>();
                    all.add(t.primary());
                    all.addAll(t.replicas());
                    return all.stream();
                })
                .toList();
    }
}
