/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.CompiledPolicyConfig;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig.*;
import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.datacloud.config.model.RawPolicyConfig;
import com.ghatana.datacloud.config.model.RawPolicyConfig.*;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Compiles raw policy YAML configuration into validated, immutable runtime
 * objects. Handles policy type parsing, selector compilation, and rule
 * configuration.
 *
 * @doc.type class
 * @doc.purpose Compile raw policy config to immutable compiled config
 * @doc.layer product
 * @doc.pattern Compiler
 */
public class PolicyConfigCompiler {

    private static final int DEFAULT_PRIORITY = 100;

    /**
     * Compile a raw policy configuration into a compiled configuration.
     *
     * @param raw the raw policy config
     * @return compiled policy config
     * @throws ConfigurationException if compilation fails
     */
    public CompiledPolicyConfig compile(RawPolicyConfig raw) {
        Objects.requireNonNull(raw, "Raw policy config cannot be null");
        Objects.requireNonNull(raw.metadata(), "Policy metadata cannot be null");
        Objects.requireNonNull(raw.metadata().name(), "Policy name cannot be null");

        RawPolicySpec spec = raw.spec();
        if (spec == null) {
            throw new ConfigurationException("Policy spec cannot be null");
        }

        return new CompiledPolicyConfig(
                raw.metadata().name(),
                spec.displayName() != null ? spec.displayName() : raw.metadata().name(),
                spec.description(),
                raw.metadata().namespace(),
                raw.metadata().labels() != null ? Map.copyOf(raw.metadata().labels()) : Map.of(),
                compileSelector(spec.selector()),
                compileRules(spec.rules()),
                spec.priority() != null ? spec.priority() : DEFAULT_PRIORITY,
                spec.enabled() == null || spec.enabled());
    }

    /**
     * Compile a policy selector.
     *
     * @param raw the raw selector
     * @return compiled selector
     */
    CompiledPolicySelector compileSelector(RawPolicySelector raw) {
        if (raw == null) {
            // Default to match all
            return CompiledPolicySelector.forAll();
        }

        boolean matchAll = raw.matchAll() != null && raw.matchAll();

        Map<String, String> matchLabels = raw.matchLabels() != null
                ? Map.copyOf(raw.matchLabels())
                : Map.of();

        CompiledFieldSelector fieldSelector = compileFieldSelector(raw.matchFields());

        Set<String> matchCollections = raw.matchCollections() != null
                ? Set.copyOf(raw.matchCollections())
                : Set.of();

        Optional<String> matchExpression = Optional.ofNullable(raw.matchExpression());

        return new CompiledPolicySelector(
                matchAll,
                matchLabels,
                fieldSelector,
                matchCollections,
                matchExpression);
    }

    /**
     * Compile a field selector.
     *
     * @param raw the raw field selector
     * @return compiled field selector
     */
    CompiledFieldSelector compileFieldSelector(RawFieldSelector raw) {
        if (raw == null) {
            return CompiledFieldSelector.empty();
        }

        Map<String, String> annotations = raw.annotations() != null
                ? Map.copyOf(raw.annotations())
                : Map.of();

        Set<String> fieldNames = raw.fieldNames() != null
                ? Set.copyOf(raw.fieldNames())
                : Set.of();

        Set<String> fieldTypes = raw.fieldTypes() != null
                ? Set.copyOf(raw.fieldTypes())
                : Set.of();

        return new CompiledFieldSelector(annotations, fieldNames, fieldTypes);
    }

    /**
     * Compile policy rules.
     *
     * @param rawRules the raw rules list
     * @return list of compiled rules
     */
    List<CompiledPolicyRule> compileRules(List<RawPolicyRule> rawRules) {
        if (rawRules == null || rawRules.isEmpty()) {
            return List.of();
        }

        return rawRules.stream()
                .map(this::compileRule)
                .toList();
    }

    /**
     * Compile a single policy rule.
     *
     * @param raw the raw rule
     * @return compiled rule
     */
    CompiledPolicyRule compileRule(RawPolicyRule raw) {
        Objects.requireNonNull(raw.name(), "Rule name cannot be null");
        Objects.requireNonNull(raw.type(), "Rule type cannot be null");

        PolicyType type = compilePolicyType(raw.type());
        Set<OperationTrigger> triggers = compileTriggers(raw.trigger());
        Optional<String> condition = Optional.ofNullable(raw.condition());
        CompiledRuleConfig config = compileRuleConfig(type, raw.config());
        boolean enabled = raw.enabled() == null || raw.enabled();

        return new CompiledPolicyRule(raw.name(), type, triggers, condition, config, enabled);
    }

    /**
     * Compile policy type from string.
     *
     * @param typeStr the type string
     * @return policy type
     */
    PolicyType compilePolicyType(String typeStr) {
        try {
            return PolicyType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid policy type: " + typeStr
                    + ". Valid values: " + Arrays.toString(PolicyType.values()));
        }
    }

    /**
     * Compile operation triggers.
     *
     * @param triggers the raw triggers
     * @return set of compiled triggers
     */
    Set<OperationTrigger> compileTriggers(List<String> triggers) {
        if (triggers == null || triggers.isEmpty()) {
            return Set.of(OperationTrigger.ALL);
        }

        return triggers.stream()
                .map(t -> {
                    try {
                        return OperationTrigger.valueOf(t.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new ConfigurationException("Invalid trigger: " + t
                                + ". Valid values: " + Arrays.toString(OperationTrigger.values()));
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Compile rule-specific configuration.
     *
     * @param type the policy type
     * @param rawConfig the raw config map
     * @return compiled rule config
     */
    CompiledRuleConfig compileRuleConfig(PolicyType type, Map<String, Object> rawConfig) {
        if (rawConfig == null) {
            rawConfig = Map.of();
        }

        return switch (type) {
            case ENCRYPTION ->
                new CompiledRuleConfig(
                Optional.of(compileEncryptionConfig(rawConfig)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                rawConfig);

            case MASKING ->
                new CompiledRuleConfig(
                Optional.empty(),
                Optional.of(compileMaskingConfig(rawConfig)),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                rawConfig);

            case AUDIT ->
                new CompiledRuleConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.of(compileAuditConfig(rawConfig)),
                Optional.empty(),
                Optional.empty(),
                rawConfig);

            case RATE_LIMIT ->
                new CompiledRuleConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(compileRateLimitConfig(rawConfig)),
                Optional.empty(),
                rawConfig);

            case VALIDATION ->
                new CompiledRuleConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(compileValidationConfig(rawConfig)),
                rawConfig);

            default ->
                new CompiledRuleConfig(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.copyOf(rawConfig));
        };
    }

    /**
     * Compile encryption configuration.
     */
    EncryptionConfig compileEncryptionConfig(Map<String, Object> config) {
        String algorithm = getStringOrDefault(config, "algorithm", "AES-256-GCM");
        String keyProvider = getStringOrDefault(config, "keyProvider", "vault");
        String keyPath = getStringOrDefault(config, "keyPath", "secrets/encryption/default-key");
        boolean rotateKeys = getBooleanOrDefault(config, "rotateKeys", false);

        return new EncryptionConfig(algorithm, keyProvider, keyPath, rotateKeys);
    }

    /**
     * Compile masking configuration.
     */
    MaskingConfig compileMaskingConfig(Map<String, Object> config) {
        String strategyStr = getStringOrDefault(config, "strategy", "PARTIAL");
        MaskingConfig.MaskingStrategy strategy;
        try {
            strategy = MaskingConfig.MaskingStrategy.valueOf(strategyStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            strategy = MaskingConfig.MaskingStrategy.PARTIAL;
        }

        int visibleChars = getIntOrDefault(config, "visibleChars", 4);
        String maskCharStr = getStringOrDefault(config, "maskChar", "*");
        char maskChar = maskCharStr.isEmpty() ? '*' : maskCharStr.charAt(0);
        String condition = (String) config.get("condition");

        return new MaskingConfig(strategy, visibleChars, maskChar, Optional.ofNullable(condition));
    }

    /**
     * Compile audit configuration.
     */
    AuditConfig compileAuditConfig(Map<String, Object> config) {
        String destination = getStringOrDefault(config, "destination", "audit-events");

        @SuppressWarnings("unchecked")
        List<String> includeList = (List<String>) config.get("include");
        Set<String> includeFields = includeList != null ? Set.copyOf(includeList) : Set.of();

        @SuppressWarnings("unchecked")
        List<String> excludeList = (List<String>) config.get("exclude");
        Set<String> excludeFields = excludeList != null ? Set.copyOf(excludeList) : Set.of();

        boolean includeChanges = getBooleanOrDefault(config, "includeChanges", true);
        String condition = (String) config.get("condition");

        return new AuditConfig(destination, includeFields, excludeFields, includeChanges,
                Optional.ofNullable(condition));
    }

    /**
     * Compile rate limit configuration.
     */
    RateLimitConfig compileRateLimitConfig(Map<String, Object> config) {
        String keyExpression = getStringOrDefault(config, "key", "tenantId");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> limitsConfig = (List<Map<String, Object>>) config.get("limits");
        List<RateLimitConfig.RateLimitWindow> limits = new ArrayList<>();

        if (limitsConfig != null) {
            for (Map<String, Object> limitConfig : limitsConfig) {
                String windowStr = (String) limitConfig.get("window");
                Duration window = parseDuration(windowStr);
                int maxRequests = getIntOrDefault(limitConfig, "maxRequests", 1000);
                limits.add(new RateLimitConfig.RateLimitWindow(window, maxRequests));
            }
        }

        if (limits.isEmpty()) {
            // Default limit
            limits.add(new RateLimitConfig.RateLimitWindow(Duration.ofMinutes(1), 1000));
        }

        String actionStr = getStringOrDefault(config, "action", "REJECT");
        RateLimitConfig.RateLimitAction action;
        try {
            action = RateLimitConfig.RateLimitAction.valueOf(actionStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            action = RateLimitConfig.RateLimitAction.REJECT;
        }

        return new RateLimitConfig(keyExpression, List.copyOf(limits), action);
    }

    /**
     * Compile validation configuration.
     */
    ValidationConfig compileValidationConfig(Map<String, Object> config) {
        boolean strictMode = getBooleanOrDefault(config, "strictMode", true);
        boolean coerceTypes = getBooleanOrDefault(config, "coerceTypes", false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rulesConfig = (List<Map<String, Object>>) config.get("rules");
        List<ValidationConfig.FieldValidationRule> rules = new ArrayList<>();

        if (rulesConfig != null) {
            for (Map<String, Object> ruleConfig : rulesConfig) {
                String field = (String) ruleConfig.get("field");
                String rule = (String) ruleConfig.get("rule");
                String message = (String) ruleConfig.get("message");
                if (field != null && rule != null) {
                    rules.add(new ValidationConfig.FieldValidationRule(field, rule, message));
                }
            }
        }

        return new ValidationConfig(strictMode, coerceTypes, List.copyOf(rules));
    }

    // Helper methods
    private String getStringOrDefault(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getIntOrDefault(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanOrDefault(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) {
            return Duration.ofMinutes(1);
        }
        try {
            return Duration.parse(durationStr);
        } catch (Exception e) {
            throw new ConfigurationException("Invalid duration format: " + durationStr);
        }
    }
}
