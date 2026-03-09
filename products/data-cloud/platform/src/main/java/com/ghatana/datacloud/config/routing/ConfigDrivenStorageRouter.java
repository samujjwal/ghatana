/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.routing;

import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.model.CompiledRoutingConfig;
import com.ghatana.datacloud.config.model.CompiledRoutingConfig.*;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration-driven storage router that uses YAML-defined routing rules.
 *
 * <p>
 * <b>Purpose:</b>
 * Routes read and write operations to appropriate storage tiers based on
 * configurable rules defined in YAML. Supports:
 * <ul>
 * <li>Write routing with condition-based targeting</li>
 * <li>Read routing with fallback support</li>
 * <li>Fan-out for write replication</li>
 * <li>Partition-based routing for data locality</li>
 * </ul>
 *
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * ConfigDrivenStorageRouter router = new ConfigDrivenStorageRouter(registry, metrics);
 *
 * // Route a write operation
 * WriteRouteResult result = runPromise(() ->
 *     router.routeWrite(tenantId, collectionName, recordType, recordData));
 *
 * // Route a read operation
 * ReadRouteResult readResult = runPromise(() ->
 *     router.routeRead(tenantId, collectionName, queryParams));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Config-driven storage routing service
 * @doc.layer product
 * @doc.pattern Service
 */
public class ConfigDrivenStorageRouter {

    private static final Logger logger = LoggerFactory.getLogger(ConfigDrivenStorageRouter.class);

    private final ConfigRegistry configRegistry;
    private final MetricsCollector metrics;
    private final ConcurrentHashMap<String, CompiledRoutingConfig> routingCache;

    /**
     * Creates a new ConfigDrivenStorageRouter.
     *
     * @param configRegistry the configuration registry
     * @param metrics the metrics collector
     */
    public ConfigDrivenStorageRouter(ConfigRegistry configRegistry, MetricsCollector metrics) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.routingCache = new ConcurrentHashMap<>();
    }

    /**
     * Route a write operation to the appropriate storage target.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param recordType the record type (ENTITY, EVENT, etc.)
     * @param recordData the record data
     * @return Promise of write route result
     */
    public Promise<WriteRouteResult> routeWrite(
            String tenantId,
            String collectionName,
            String recordType,
            Map<String, Object> recordData) {

        long startTime = System.nanoTime();

        return getRoutingConfig(tenantId, collectionName)
                .map(config -> {
                    RoutingContext context = RoutingContext.forWrite(
                            tenantId, collectionName, recordType, recordData);

                    RoutingDecision decision = config.routeWrite(context);

                    // Compute partition if configured
                    int partition = config.partitioning() != null
                            ? config.partitioning().computePartition(recordData)
                            : 0;

                    // Get fan-out targets
                    List<String> fanOutTargets = config.fanOut().enabled()
                            ? config.getFanOutTargets()
                            : List.of();

                    recordMetrics("write", tenantId, collectionName, decision, startTime);

                    return new WriteRouteResult(
                            decision.target(),
                            partition,
                            decision.matchedRule(),
                            fanOutTargets,
                            config.fanOut().async());
                });
    }

    /**
     * Route a read operation to the appropriate storage target.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param queryParams the query parameters
     * @return Promise of read route result
     */
    public Promise<ReadRouteResult> routeRead(
            String tenantId,
            String collectionName,
            Map<String, Object> queryParams) {

        long startTime = System.nanoTime();

        return getRoutingConfig(tenantId, collectionName)
                .map(config -> {
                    RoutingContext context = RoutingContext.forRead(
                            tenantId, collectionName, queryParams);

                    RoutingDecision decision = config.routeRead(context);

                    recordMetrics("read", tenantId, collectionName, decision, startTime);

                    return new ReadRouteResult(
                            decision.target(),
                            decision.fallback().orElse(null),
                            decision.matchedRule(),
                            config.readRouting().preference());
                });
    }

    /**
     * Route a batch write operation.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param records list of records with their types
     * @return Promise of batch write route result
     */
    public Promise<BatchWriteRouteResult> routeBatchWrite(
            String tenantId,
            String collectionName,
            List<BatchRecord> records) {

        return getRoutingConfig(tenantId, collectionName)
                .map(config -> {
                    Map<String, List<BatchRecord>> routedRecords = new HashMap<>();

                    for (BatchRecord record : records) {
                        RoutingContext context = RoutingContext.forWrite(
                                tenantId, collectionName, record.recordType(), record.data());

                        RoutingDecision decision = config.routeWrite(context);
                        String target = decision.target();

                        routedRecords.computeIfAbsent(target, k -> new ArrayList<>())
                                .add(record);
                    }

                    return new BatchWriteRouteResult(routedRecords);
                });
    }

    /**
     * Get partition information for a record.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @param recordData the record data
     * @return Promise of partition info
     */
    public Promise<PartitionInfo> getPartition(
            String tenantId,
            String collectionName,
            Map<String, Object> recordData) {

        return getRoutingConfig(tenantId, collectionName)
                .map(config -> {
                    if (config.partitioning() == null) {
                        return new PartitionInfo(0, 1, "default");
                    }

                    int partition = config.partitioning().computePartition(recordData);
                    int totalPartitions = config.partitioning().hashPartition() != null
                            ? config.partitioning().hashPartition().partitionCount()
                            : 1;

                    Object key = config.partitioning().partitionKey().extractKey(recordData);

                    return new PartitionInfo(partition, totalPartitions, String.valueOf(key));
                });
    }

    /**
     * Reload routing configuration for a tenant/collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadConfig(String tenantId, String collectionName) {
        String cacheKey = buildCacheKey(tenantId, collectionName);
        routingCache.remove(cacheKey);
        logger.info("Routing config cache cleared for {}/{}", tenantId, collectionName);
        return Promise.complete();
    }

    // =====================================================================
    // Internal Methods
    // =====================================================================
    private Promise<CompiledRoutingConfig> getRoutingConfig(String tenantId, String collectionName) {
        String cacheKey = buildCacheKey(tenantId, collectionName);

        CompiledRoutingConfig cached = routingCache.get(cacheKey);
        if (cached != null) {
            metrics.incrementCounter("routing.cache.hit", "tenant", tenantId);
            return Promise.of(cached);
        }

        metrics.incrementCounter("routing.cache.miss", "tenant", tenantId);

        return configRegistry.getRoutingConfig(tenantId, collectionName)
                .map(config -> {
                    routingCache.put(cacheKey, config);
                    return config;
                })
                .then(
                        config -> Promise.of(config),
                        ex -> {
                            logger.warn("Failed to load routing config for {}/{}, using defaults",
                                    tenantId, collectionName, ex);
                            return Promise.of(createDefaultConfig(tenantId, collectionName));
                        }
                );
    }

    private CompiledRoutingConfig createDefaultConfig(String tenantId, String collectionName) {
        return new CompiledRoutingConfig(
                "default",
                tenantId,
                Map.of(),
                new CompiledWriteRouting(
                        List.of(createDefaultRule("default-storage")),
                        "default-storage"),
                new CompiledReadRouting(
                        List.of(createDefaultRule("default-storage")),
                        "default-storage",
                        CompiledReadPreference.defaultPreference()),
                CompiledFanOutConfig.disabled(),
                CompiledPartitionConfig.singlePartition(),
                1L,
                Instant.now());
    }

    private CompiledRoutingRule createDefaultRule(String target) {
        return new CompiledRoutingRule(
                "default",
                CompiledCondition.alwaysTrue(),
                target,
                Optional.empty(),
                Integer.MAX_VALUE,
                true,
                Map.of());
    }

    private String buildCacheKey(String tenantId, String collectionName) {
        return tenantId + ":" + collectionName;
    }

    private void recordMetrics(
            String operation,
            String tenantId,
            String collectionName,
            RoutingDecision decision,
            long startTime) {

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        metrics.recordTimer("routing.decision.duration", durationMs,
                "operation", operation,
                "tenant", tenantId,
                "collection", collectionName);

        metrics.incrementCounter("routing.decision.count",
                "operation", operation,
                "tenant", tenantId,
                "target", decision.target(),
                "rule", decision.matchedRule());
    }

    // =====================================================================
    // Result Types
    // =====================================================================
    /**
     * Result of a write routing decision.
     *
     * @param target the primary write target
     * @param partition the partition number
     * @param matchedRule the name of the matched routing rule
     * @param fanOutTargets additional targets for replication
     * @param asyncFanOut whether fan-out should be async
     */
    public record WriteRouteResult(
            String target,
            int partition,
            String matchedRule,
            List<String> fanOutTargets,
            boolean asyncFanOut) {

    public WriteRouteResult

    {
        Objects.requireNonNull(target, "target");
        fanOutTargets = fanOutTargets != null ? List.copyOf(fanOutTargets) : List.of();
    }

    /**
     * Check if fan-out is enabled.
     */
    public boolean hasFanOut() {
        return !fanOutTargets.isEmpty();
    }
}

/**
 * Result of a read routing decision.
 *
 * @param target the primary read target
 * @param fallback the fallback target if primary fails
 * @param matchedRule the name of the matched routing rule
 * @param preference read preference settings
 */
public record ReadRouteResult(
        String target,
        String fallback,
        String matchedRule,
        CompiledReadPreference preference) {

    public ReadRouteResult    {
        Objects.requireNonNull(target, "target");
    }

    /**
     * Check if fallback is available.
     */
    public boolean hasFallback() {
        return fallback != null;
    }
}

/**
 * Result of a batch write routing decision.
 *
 * @param routedRecords records grouped by target storage
 */
public record BatchWriteRouteResult(
        Map<String, List<BatchRecord>> routedRecords) {

    public BatchWriteRouteResult {
        routedRecords = routedRecords != null ? Map.copyOf(routedRecords) : Map.of();
    }

    /**
     * Get all targets in this batch.
     */
    public Set<String> getTargets() {
        return routedRecords.keySet();
    }

    /**
     * Get records for a specific target.
     */
    public List<BatchRecord> getRecordsForTarget(String target) {
        return routedRecords.getOrDefault(target, List.of());
    }
}

/**
 * A record in a batch operation.
 *
 * @param recordType the record type
 * @param data the record data
 */
public record BatchRecord(
        String recordType,
        Map<String, Object> data) {

    public BatchRecord  {
        data = data != null ? Map.copyOf(data) : Map.of();
    }
}

/**
 * Partition information for a record.
 *
 * @param partition the partition number
 * @param totalPartitions total number of partitions
 * @param partitionKey the computed partition key
 */
public record PartitionInfo(
        int partition,
        int totalPartitions,
        String partitionKey) {

}
}
