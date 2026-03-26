/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Map;

/**
 * Raw routing configuration as loaded from YAML.
 *
 * <p>
 * Represents the deserialized YAML structure for storage and partition routing
 * rules. This model is transformed into {@link CompiledRoutingConfig} after
 * validation.
 *
 * <p>
 * <b>Example YAML:</b>
 * <pre>{@code
 * apiVersion: datacloud.ghatana.com/v1
 * kind: RoutingConfig
 * metadata:
 *   name: default-routing
 *   namespace: production
 * spec:
 *   writeRouting:
 *     rules:
 *       - name: events-to-hot
 *         condition: "record.recordType == 'EVENT'"
 *         target: hot-redis
 *   readRouting:
 *     rules:
 *       - name: recent-from-hot
 *         condition: "query.timeRange.end > now() - PT1H"
 *         target: hot-redis
 *         fallback: warm-postgres
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Raw YAML configuration model for routing
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record RawRoutingConfig(
        String apiVersion,
        String kind,
        RawRoutingMetadata metadata,
        RawRoutingSpec spec) {

    /**
     * Metadata section of the routing config.
     */
    public record RawRoutingMetadata(
            String name,
            String namespace,
            Map<String, String> labels,
            Map<String, String> annotations) {

        

    public RawRoutingMetadata    {
        labels = labels != null ? Map.copyOf(labels) : Map.of();
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }
}

/**
 * Spec section containing routing rules.
 */
public record RawRoutingSpec(
        RawWriteRouting writeRouting,
        RawReadRouting readRouting,
        RawFanOutConfig fanOut,
        RawPartitionConfig partitioning) {

}

/**
 * Write routing configuration.
 */
public record RawWriteRouting(
        List<RawRoutingRule> rules,
        String defaultTarget) {

    public RawWriteRouting  {
        rules = rules != null ? List.copyOf(rules) : List.of();
    }
}

/**
 * Read routing configuration.
 */
public record RawReadRouting(
        List<RawRoutingRule> rules,
        String defaultTarget,
        RawReadPreference preference) {

    public RawReadRouting   {
        rules = rules != null ? List.copyOf(rules) : List.of();
    }
}

/**
 * Individual routing rule.
 */
public record RawRoutingRule(
        String name,
        String condition,
        String target,
        String fallback,
        Integer priority,
        Boolean enabled,
        Map<String, Object> metadata) {

    public RawRoutingRule       {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }
}

/**
 * Read preference configuration.
 */
public record RawReadPreference(
        String mode, // PRIMARY | SECONDARY | NEAREST
        Integer maxStalenessMs,
        List<String> tagSets) {

    public RawReadPreference   {
        tagSets = tagSets != null ? List.copyOf(tagSets) : List.of();
    }
}

/**
 * Fan-out configuration for write replication.
 */
public record RawFanOutConfig(
        Boolean enabled,
        Boolean async,
        List<RawFanOutTarget> targets,
        RawRetryConfig retry) {

    public RawFanOutConfig    {
        targets = targets != null ? List.copyOf(targets) : List.of();
    }
}

/**
 * Fan-out target with primary and replicas.
 */
public record RawFanOutTarget(
        String primary,
        List<String> replicas,
        String consistencyLevel) {

    public RawFanOutTarget   {
        replicas = replicas != null ? List.copyOf(replicas) : List.of();
    }
}

/**
 * Retry configuration for failed operations.
 */
public record RawRetryConfig(
        Integer maxAttempts,
        Integer initialDelayMs,
        Integer maxDelayMs,
        Double multiplier) {

}

/**
 * Partition configuration.
 */
public record RawPartitionConfig(
        String strategy, // HASH | RANGE | LIST | COMPOSITE
        RawPartitionKey partitionKey,
        RawHashConfig hash,
        RawRangeConfig range,
        RawPlacementConfig placement) {

}

/**
 * Partition key configuration.
 */
public record RawPartitionKey(
        String type, // FIELD | EXPRESSION | COMPOSITE
        String field,
        String expression,
        List<String> fields) {

    public RawPartitionKey    {
        fields = fields != null ? List.copyOf(fields) : List.of();
    }
}

/**
 * Hash partitioning configuration.
 */
public record RawHashConfig(
        String algorithm, // MURMUR3 | MD5 | SHA256
        Integer partitionCount) {

}

/**
 * Range partitioning configuration.
 */
public record RawRangeConfig(
        String field,
        List<String> boundaries,
        String interval) {        // ISO 8601 duration for time-based

    public RawRangeConfig   {
        boundaries = boundaries != null ? List.copyOf(boundaries) : List.of();
    }
}

/**
 * Placement configuration for partition affinity.
 */
public record RawPlacementConfig(
        List<RawAffinityRule> affinity,
        List<RawAntiAffinityRule> antiAffinity) {

    public RawPlacementConfig  {
        affinity = affinity != null ? List.copyOf(affinity) : List.of();
        antiAffinity = antiAffinity != null ? List.copyOf(antiAffinity) : List.of();
    }
}

/**
 * Affinity rule for partition placement.
 */
public record RawAffinityRule(
        String key,
        String strategy) {        // COLOCATE | SPREAD

}

/**
 * Anti-affinity rule for partition placement.
 */
public record RawAntiAffinityRule(
        String key,
        String strategy) {        // SEPARATE | ZONE_SPREAD

}
}
