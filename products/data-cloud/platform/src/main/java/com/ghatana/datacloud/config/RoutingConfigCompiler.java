/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.datacloud.config.model.CompiledRoutingConfig;
import com.ghatana.datacloud.config.model.CompiledRoutingConfig.*;
import com.ghatana.datacloud.config.model.RawRoutingConfig;
import com.ghatana.datacloud.config.model.RawRoutingConfig.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiler that transforms raw YAML routing configuration into compiled,
 * validated, and optimized routing rules.
 *
 * <p>
 * <b>Usage:</b>
 * <pre>{@code
 * RoutingConfigCompiler compiler = new RoutingConfigCompiler();
 * CompiledRoutingConfig config = compiler.compile(rawConfig);
 * }</pre>
 *
 * <p>
 * <b>Compilation Steps:</b>
 * <ol>
 * <li>Validate required fields</li>
 * <li>Parse routing conditions</li>
 * <li>Compile write and read routing rules</li>
 * <li>Compile partition configuration</li>
 * <li>Validate target references</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Compile raw routing YAML to validated runtime config
 * @doc.layer product
 * @doc.pattern Compiler
 */
public class RoutingConfigCompiler {

    // Pattern for simple condition parsing: field operator value
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "([a-zA-Z_.]+)\\s*(==|!=|>|<|>=|<=|in|contains|startsWith|endsWith|matches)\\s*(.+)");

    /**
     * Compiles a raw routing configuration to a compiled configuration.
     *
     * @param raw the raw configuration from YAML
     * @return the compiled configuration
     * @throws ConfigurationException if configuration is invalid
     */
    public CompiledRoutingConfig compile(RawRoutingConfig raw) {
        Objects.requireNonNull(raw, "raw config");
        validateRawConfig(raw);

        String name = raw.metadata().name();
        String namespace = raw.metadata().namespace();
        Map<String, String> labels = raw.metadata().labels();

        CompiledWriteRouting writeRouting = compileWriteRouting(raw.spec().writeRouting());
        CompiledReadRouting readRouting = compileReadRouting(raw.spec().readRouting());
        CompiledFanOutConfig fanOut = compileFanOut(raw.spec().fanOut());
        CompiledPartitionConfig partitioning = compilePartitioning(raw.spec().partitioning());

        return new CompiledRoutingConfig(
                name,
                namespace,
                labels,
                writeRouting,
                readRouting,
                fanOut,
                partitioning,
                1L,
                Instant.now());
    }

    private void validateRawConfig(RawRoutingConfig raw) {
        if (raw.metadata() == null) {
            throw new ConfigurationException("metadata is required");
        }
        if (raw.metadata().name() == null || raw.metadata().name().isBlank()) {
            throw new ConfigurationException("metadata.name is required");
        }
        if (raw.spec() == null) {
            throw new ConfigurationException("spec is required");
        }
    }

    // =====================================================================
    // Write Routing Compilation
    // =====================================================================
    private CompiledWriteRouting compileWriteRouting(RawWriteRouting raw) {
        if (raw == null) {
            return new CompiledWriteRouting(
                    List.of(createDefaultRule("default")),
                    "default");
        }

        List<CompiledRoutingRule> rules = raw.rules().stream()
                .map(this::compileRoutingRule)
                .sorted(Comparator.comparingInt(CompiledRoutingRule::priority))
                .toList();

        String defaultTarget = raw.defaultTarget() != null ? raw.defaultTarget() : "default";

        return new CompiledWriteRouting(rules, defaultTarget);
    }

    // =====================================================================
    // Read Routing Compilation
    // =====================================================================
    private CompiledReadRouting compileReadRouting(RawReadRouting raw) {
        if (raw == null) {
            return new CompiledReadRouting(
                    List.of(createDefaultRule("default")),
                    "default",
                    CompiledReadPreference.defaultPreference());
        }

        List<CompiledRoutingRule> rules = raw.rules().stream()
                .map(this::compileRoutingRule)
                .sorted(Comparator.comparingInt(CompiledRoutingRule::priority))
                .toList();

        String defaultTarget = raw.defaultTarget() != null ? raw.defaultTarget() : "default";
        CompiledReadPreference preference = compileReadPreference(raw.preference());

        return new CompiledReadRouting(rules, defaultTarget, preference);
    }

    private CompiledReadPreference compileReadPreference(RawReadPreference raw) {
        if (raw == null) {
            return CompiledReadPreference.defaultPreference();
        }

        ReadPreferenceMode mode = parseReadPreferenceMode(raw.mode());
        Duration maxStaleness = raw.maxStalenessMs() != null
                ? Duration.ofMillis(raw.maxStalenessMs())
                : Duration.ZERO;
        Set<String> tagSets = raw.tagSets() != null ? Set.copyOf(raw.tagSets()) : Set.of();

        return new CompiledReadPreference(mode, maxStaleness, tagSets);
    }

    private ReadPreferenceMode parseReadPreferenceMode(String mode) {
        if (mode == null) {
            return ReadPreferenceMode.PRIMARY;
        }
        return switch (mode.toUpperCase()) {
            case "PRIMARY" ->
                ReadPreferenceMode.PRIMARY;
            case "SECONDARY" ->
                ReadPreferenceMode.SECONDARY;
            case "NEAREST" ->
                ReadPreferenceMode.NEAREST;
            case "ROUND_ROBIN" ->
                ReadPreferenceMode.ROUND_ROBIN;
            default ->
                ReadPreferenceMode.PRIMARY;
        };
    }

    // =====================================================================
    // Routing Rule Compilation
    // =====================================================================
    private CompiledRoutingRule compileRoutingRule(RawRoutingRule raw) {
        String name = raw.name();
        if (name == null || name.isBlank()) {
            throw new ConfigurationException("Routing rule name is required");
        }

        String target = raw.target();
        if (target == null || target.isBlank()) {
            throw new ConfigurationException("Routing rule target is required for rule: " + name);
        }

        CompiledCondition condition = compileCondition(raw.condition());
        Optional<String> fallback = Optional.ofNullable(raw.fallback());
        int priority = raw.priority() != null ? raw.priority() : 100;
        boolean enabled = raw.enabled() == null || raw.enabled();
        Map<String, Object> metadata = raw.metadata();

        return new CompiledRoutingRule(
                name,
                condition,
                target,
                fallback,
                priority,
                enabled,
                metadata);
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

    // =====================================================================
    // Condition Compilation
    // =====================================================================
    private CompiledCondition compileCondition(String conditionExpr) {
        if (conditionExpr == null || conditionExpr.isBlank() || conditionExpr.equals("true")) {
            return CompiledCondition.alwaysTrue();
        }

        if (conditionExpr.equals("false")) {
            return new CompiledCondition(
                    ConditionType.ALWAYS_FALSE,
                    null, null, null, List.of());
        }

        // Handle AND/OR compound conditions
        if (conditionExpr.contains(" && ")) {
            String[] parts = conditionExpr.split(" && ");
            List<CompiledCondition> children = Arrays.stream(parts)
                    .map(this::compileCondition)
                    .toList();
            return new CompiledCondition(
                    ConditionType.AND,
                    null, null, null, children);
        }

        if (conditionExpr.contains(" || ")) {
            String[] parts = conditionExpr.split(" \\|\\| ");
            List<CompiledCondition> children = Arrays.stream(parts)
                    .map(this::compileCondition)
                    .toList();
            return new CompiledCondition(
                    ConditionType.OR,
                    null, null, null, children);
        }

        // Parse simple condition
        Matcher matcher = CONDITION_PATTERN.matcher(conditionExpr.trim());
        if (!matcher.matches()) {
            // If we can't parse, treat as always true
            return CompiledCondition.alwaysTrue();
        }

        String field = matcher.group(1);
        String operatorStr = matcher.group(2);
        String valueStr = matcher.group(3).trim();

        ConditionOperator operator = parseOperator(operatorStr);
        Object value = parseValue(valueStr);

        return new CompiledCondition(
                ConditionType.FIELD_MATCH,
                field,
                operator,
                value,
                List.of());
    }

    private ConditionOperator parseOperator(String op) {
        return switch (op) {
            case "==" ->
                ConditionOperator.EQUALS;
            case "!=" ->
                ConditionOperator.NOT_EQUALS;
            case ">" ->
                ConditionOperator.GREATER_THAN;
            case "<" ->
                ConditionOperator.LESS_THAN;
            case ">=" ->
                ConditionOperator.GREATER_THAN_OR_EQUALS;
            case "<=" ->
                ConditionOperator.LESS_THAN_OR_EQUALS;
            case "in" ->
                ConditionOperator.IN;
            case "contains" ->
                ConditionOperator.CONTAINS;
            case "startsWith" ->
                ConditionOperator.STARTS_WITH;
            case "endsWith" ->
                ConditionOperator.ENDS_WITH;
            case "matches" ->
                ConditionOperator.MATCHES;
            default ->
                ConditionOperator.EQUALS;
        };
    }

    private Object parseValue(String valueStr) {
        // Remove quotes if present
        if ((valueStr.startsWith("'") && valueStr.endsWith("'"))
                || (valueStr.startsWith("\"") && valueStr.endsWith("\""))) {
            return valueStr.substring(1, valueStr.length() - 1);
        }

        // Try parsing as number
        try {
            if (valueStr.contains(".")) {
                return Double.parseDouble(valueStr);
            }
            return Long.parseLong(valueStr);
        } catch (NumberFormatException e) {
            // Return as string
            return valueStr;
        }
    }

    // =====================================================================
    // Fan-Out Compilation
    // =====================================================================
    private CompiledFanOutConfig compileFanOut(RawFanOutConfig raw) {
        if (raw == null || raw.enabled() == null || !raw.enabled()) {
            return CompiledFanOutConfig.disabled();
        }

        boolean async = raw.async() != null && raw.async();

        List<CompiledFanOutTarget> targets = raw.targets().stream()
                .map(this::compileFanOutTarget)
                .toList();

        CompiledRetryConfig retry = compileRetryConfig(raw.retry());

        return new CompiledFanOutConfig(true, async, targets, retry);
    }

    private CompiledFanOutTarget compileFanOutTarget(RawFanOutTarget raw) {
        String primary = raw.primary();
        if (primary == null || primary.isBlank()) {
            throw new ConfigurationException("Fan-out target primary is required");
        }

        List<String> replicas = raw.replicas();
        ConsistencyLevel consistencyLevel = parseConsistencyLevel(raw.consistencyLevel());

        return new CompiledFanOutTarget(primary, replicas, consistencyLevel);
    }

    private ConsistencyLevel parseConsistencyLevel(String level) {
        if (level == null) {
            return ConsistencyLevel.ONE;
        }
        return switch (level.toUpperCase()) {
            case "ONE" ->
                ConsistencyLevel.ONE;
            case "QUORUM" ->
                ConsistencyLevel.QUORUM;
            case "ALL" ->
                ConsistencyLevel.ALL;
            case "LOCAL_QUORUM" ->
                ConsistencyLevel.LOCAL_QUORUM;
            case "EACH_QUORUM" ->
                ConsistencyLevel.EACH_QUORUM;
            default ->
                ConsistencyLevel.ONE;
        };
    }

    private CompiledRetryConfig compileRetryConfig(RawRetryConfig raw) {
        if (raw == null) {
            return CompiledRetryConfig.defaultConfig();
        }

        int maxAttempts = raw.maxAttempts() != null ? raw.maxAttempts() : 3;
        Duration initialDelay = raw.initialDelayMs() != null
                ? Duration.ofMillis(raw.initialDelayMs())
                : Duration.ofMillis(100);
        Duration maxDelay = raw.maxDelayMs() != null
                ? Duration.ofMillis(raw.maxDelayMs())
                : Duration.ofSeconds(5);
        double multiplier = raw.multiplier() != null ? raw.multiplier() : 2.0;

        return new CompiledRetryConfig(maxAttempts, initialDelay, maxDelay, multiplier);
    }

    // =====================================================================
    // Partition Configuration Compilation
    // =====================================================================
    private CompiledPartitionConfig compilePartitioning(RawPartitionConfig raw) {
        if (raw == null) {
            return CompiledPartitionConfig.singlePartition();
        }

        PartitionStrategy strategy = parsePartitionStrategy(raw.strategy());
        CompiledPartitionKey partitionKey = compilePartitionKey(raw.partitionKey());
        CompiledHashPartition hashPartition = compileHashPartition(raw.hash());
        CompiledRangePartition rangePartition = compileRangePartition(raw.range());
        CompiledPlacement placement = compilePlacement(raw.placement());

        return new CompiledPartitionConfig(
                strategy,
                partitionKey,
                hashPartition,
                rangePartition,
                placement);
    }

    private PartitionStrategy parsePartitionStrategy(String strategy) {
        if (strategy == null) {
            return PartitionStrategy.HASH;
        }
        return switch (strategy.toUpperCase()) {
            case "HASH" ->
                PartitionStrategy.HASH;
            case "RANGE" ->
                PartitionStrategy.RANGE;
            case "LIST" ->
                PartitionStrategy.LIST;
            case "COMPOSITE" ->
                PartitionStrategy.COMPOSITE;
            default ->
                PartitionStrategy.HASH;
        };
    }

    private CompiledPartitionKey compilePartitionKey(RawPartitionKey raw) {
        if (raw == null) {
            return CompiledPartitionKey.defaultKey();
        }

        PartitionKeyType type = parsePartitionKeyType(raw.type());
        return new CompiledPartitionKey(
                type,
                raw.field(),
                raw.expression(),
                raw.fields());
    }

    private PartitionKeyType parsePartitionKeyType(String type) {
        if (type == null) {
            return PartitionKeyType.FIELD;
        }
        return switch (type.toUpperCase()) {
            case "FIELD" ->
                PartitionKeyType.FIELD;
            case "EXPRESSION" ->
                PartitionKeyType.EXPRESSION;
            case "COMPOSITE" ->
                PartitionKeyType.COMPOSITE;
            default ->
                PartitionKeyType.FIELD;
        };
    }

    private CompiledHashPartition compileHashPartition(RawHashConfig raw) {
        if (raw == null) {
            return new CompiledHashPartition(HashAlgorithm.MURMUR3, 64);
        }

        HashAlgorithm algorithm = parseHashAlgorithm(raw.algorithm());
        int partitionCount = raw.partitionCount() != null ? raw.partitionCount() : 64;

        return new CompiledHashPartition(algorithm, partitionCount);
    }

    private HashAlgorithm parseHashAlgorithm(String algorithm) {
        if (algorithm == null) {
            return HashAlgorithm.MURMUR3;
        }
        return switch (algorithm.toUpperCase()) {
            case "MURMUR3" ->
                HashAlgorithm.MURMUR3;
            case "MD5" ->
                HashAlgorithm.MD5;
            case "SHA256" ->
                HashAlgorithm.SHA256;
            case "XXHASH" ->
                HashAlgorithm.XXHASH;
            default ->
                HashAlgorithm.MURMUR3;
        };
    }

    private CompiledRangePartition compileRangePartition(RawRangeConfig raw) {
        if (raw == null) {
            return null;
        }

        String field = raw.field();
        List<Object> boundaries = raw.boundaries() != null
                ? new ArrayList<>(raw.boundaries())
                : List.of();
        Duration interval = raw.interval() != null
                ? Duration.parse(raw.interval())
                : null;

        return new CompiledRangePartition(field, boundaries, interval);
    }

    private CompiledPlacement compilePlacement(RawPlacementConfig raw) {
        if (raw == null) {
            return new CompiledPlacement(List.of(), List.of());
        }

        List<CompiledAffinityRule> affinity = raw.affinity().stream()
                .map(r -> new CompiledAffinityRule(r.key(), parsePlacementStrategy(r.strategy())))
                .toList();

        List<CompiledAntiAffinityRule> antiAffinity = raw.antiAffinity().stream()
                .map(r -> new CompiledAntiAffinityRule(r.key(), parsePlacementStrategy(r.strategy())))
                .toList();

        return new CompiledPlacement(affinity, antiAffinity);
    }

    private PlacementStrategy parsePlacementStrategy(String strategy) {
        if (strategy == null) {
            return PlacementStrategy.SPREAD;
        }
        return switch (strategy.toUpperCase()) {
            case "COLOCATE" ->
                PlacementStrategy.COLOCATE;
            case "SPREAD" ->
                PlacementStrategy.SPREAD;
            case "SEPARATE" ->
                PlacementStrategy.SEPARATE;
            case "ZONE_SPREAD" ->
                PlacementStrategy.ZONE_SPREAD;
            default ->
                PlacementStrategy.SPREAD;
        };
    }
}
